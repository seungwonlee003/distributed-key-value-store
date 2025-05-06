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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@RequiredArgsConstructor
public class LogReplicator {
    private final RaftConfig config;
    private final RaftLog log;
    @Autowired
    @Lazy
    private RaftNodeStateManager nodeStateManager;
    private final RaftNodeState nodeState;
    private final StateMachine stateMachine;

    private final Map<String, Integer> nextIndex = new ConcurrentHashMap<>();
    private final Map<String, Integer> matchIndex = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pendingReplication = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;
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

    // Upon election: send initial empty AppendEntries RPCs (heartbeat) to each server; repeat during idle periods
    // to prevent election timeouts (§5.2)
    public void start() {
        if (nodeState.getCurrentRole() != Role.LEADER) return;
        for (String peer : config.getPeerUrls().values()) {
            if (!pendingReplication.getOrDefault(peer, false)) {
                pendingReplication.put(peer, true);
                executor.submit(() -> replicateLoop(peer));
            }
        }
    }

    // Sends heartbeats/log entries to peer, adjusting sleep time to match heartbeat interval
    private void replicateLoop(String peer) {
        int backoff = config.getHeartbeatIntervalMillis();
        while (nodeState.getCurrentRole() == Role.LEADER) {
            long startTime = System.currentTimeMillis();
            boolean ok = replicate(peer);
            if (ok) updateCommitIndex();
            long duration = System.currentTimeMillis() - startTime;
            long sleepTime = Math.max(0, backoff - duration);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        pendingReplication.put(peer, false);
    }

    // If last log index >= nextIndex for a follower: send AppendEntries RPC with log entreis starting at nextIndex.
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
            System.out.println("Node " + nodeState.getNodeId() + " sending appendEntries to " + peer);
            ResponseEntity<AppendEntryResponseDto> res = restTemplate.postForEntity(url, dto, AppendEntryResponseDto.class);
            AppendEntryResponseDto body = res.getBody() != null ? res.getBody() : new AppendEntryResponseDto(-1, false);
            if (body.getTerm() > nodeState.getCurrentTerm()) {
                System.out.println("Node " + nodeState.getNodeId() + " found higher term " + body.getTerm() + ", becoming FOLLOWER");
                nodeStateManager.becomeFollower(body.getTerm());
                return false;
            }

            if (body.isSuccess()) {
                System.out.println("Node " + nodeState.getNodeId() + " appendEntries to " + peer + " succeeded");
                nextIndex.put(peer, ni + entries.size());
                matchIndex.put(peer, ni + entries.size() - 1);
                return true;
            } else {
                System.out.println("Node " + nodeState.getNodeId() + " appendEntries to " + peer + " failed, retrying");
                nextIndex.put(peer, Math.max(0, ni - 1));
                return false;
            }
        } catch (Exception e) {
            System.out.println("Node " + nodeState.getNodeId() + " appendEntries to " + peer + " failed: " + e.getMessage());
            return false;
        }
    }

    // If there exists an N such that N > commitIndex, a majority of matchIndex[i] >= N,
    // and log[N].term == currentTerm: set commitIndex = N (§5.3, §5.4)
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

    // If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (§5.3)
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
