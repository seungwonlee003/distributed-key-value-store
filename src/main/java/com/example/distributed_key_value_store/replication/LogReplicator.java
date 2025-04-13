package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.config.RaftConfig;
import com.example.distributed_key_value_store.dto.AppendEntryRequestDto;
import com.example.distributed_key_value_store.dto.AppendEntryResponseDto;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import com.example.distributed_key_value_store.node.Role;
import com.example.distributed_key_value_store.storage.StateMachine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@RequiredArgsConstructor
public class LogReplicator {
    private final RaftConfig config;
    private final RaftLog log;
    private final RaftNodeStateManager nodeStateManager;
    private final RaftNodeState nodeState;
    private final StateMachine stateMachine;

    private final Map<String, Integer> nextIndex = new ConcurrentHashMap<>();
    private final Map<String, Integer> matchIndex = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pendingReplication = new ConcurrentHashMap<>();
    private final ExecutorService applyExecutor = Executors.newSingleThreadExecutor();

    private RestTemplate restTemplate;
    private ScheduledExecutorService executor;
    @PostConstruct
    private void initExecutor() {
        this.executor = Executors.newScheduledThreadPool(config.getPeerUrls().size());
    }

    public void initializeIndices() {
        int lastIndex = log.getLastIndex();
        for (String peer : config.getPeerUrls().values()) {
            nextIndex.put(peer, lastIndex + 1);
            matchIndex.put(peer, 0);
        }
    }

    public void start() {
        if (nodeState.getCurrentRole() != Role.LEADER) return;
        for (String peer : config.getPeerUrls().values()) {
            if (!pendingReplication.getOrDefault(peer, false)) {
                pendingReplication.put(peer, true);
                executor.submit(() -> replicateLoop(peer));
            }
        }
    }

    private void replicateLoop(String peer) {
        int backoff = config.getHeartbeatIntervalMillis();
        while (nodeState.getCurrentRole() == Role.LEADER) {
            boolean ok = replicate(peer);
            if (ok) updateCommitIndex();
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        pendingReplication.put(peer, false);
    }

    private boolean replicate(String peer) {
        int ni = nextIndex.get(peer);
        int prevIdx = ni - 1;
        int prevTerm = (prevIdx >= 0) ? log.getTermAt(prevIdx) : 0;
        List<LogEntry> entries = log.getEntriesFrom(ni);

        AppendEntryRequestDto dto = new AppendEntryRequestDto(
                nodeState.getCurrentTerm(), nodeState.getNodeId(),
                prevIdx, prevTerm, entries, log.getCommitIndex()
        );

        try {
            String url = peer + "/raft/appendEntries";
            ResponseEntity<AppendEntryResponseDto> res = restTemplate.postForEntity(url, dto, AppendEntryResponseDto.class);
            AppendEntryResponseDto body = res.getBody() != null ? res.getBody() : new AppendEntryResponseDto(-1, false);
            if (body.getTerm() > nodeState.getCurrentTerm()) {
                nodeStateManager.becomeFollower(body.getTerm());
                return false;
            }
            if (body.isSuccess()) {
                nextIndex.put(peer, ni + entries.size());
                matchIndex.put(peer, ni + entries.size() - 1);
                return true;
            } else {
                nextIndex.put(peer, Math.max(0, ni - 1));
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void updateCommitIndex() {
        int majority = (config.getPeerUrls().size() + 1) / 2 + 1;
        int term = nodeState.getCurrentTerm();
        for (int i = log.getLastIndex(); i > log.getCommitIndex(); i--) {
            int count = 1;
            for (int idx : matchIndex.values()) {
                if (idx >= i) count++;
            }
            if (count >= majority && log.getTermAt(i) == term) {
                log.setCommitIndex(i);
                applyEntries();
                break;
            }
        }
    }

    private void applyEntries() {
        int commit = log.getCommitIndex();
        int lastApplied = nodeState.getLastApplied();
        for (int i = lastApplied + 1; i <= commit; i++) {
            try {
                LogEntry entry = log.getEntryAt(i);
                stateMachine.apply(entry);
                nodeState.setLastApplied(i);
            } catch (Exception e) {
                System.out.println("Failed to apply entry " + i);
                System.exit(1);
            }
        }
    }
}
