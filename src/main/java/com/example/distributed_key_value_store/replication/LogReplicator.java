package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.config.RaftConfig;
import com.example.distributed_key_value_store.dto.AppendEntriesRequestDto;
import com.example.distributed_key_value_store.dto.AppendEntriesResponseDto;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import com.example.distributed_key_value_store.node.Role;
import com.example.distributed_key_value_store.storage.StateMachine;
import com.example.distributed_key_value_store.util.LockManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogReplicator {
    @Autowired
    @Lazy
    private RaftNodeStateManager nodeStateManager;
    private final RaftConfig config;
    private final RaftLog raftLog;
    private final RaftNodeState nodeState;
    private final StateMachine stateMachine;

    private final Map<String, Integer> nextIndex = new ConcurrentHashMap<>();
    private final Map<String, Integer> matchIndex = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pendingReplication = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;
    private ScheduledExecutorService executor;
    private final LockManager lockManager;

    @PostConstruct
    private void initExecutor() {
        this.executor = Executors.newScheduledThreadPool(config.getPeerUrls().size());
    }

    public void initializeIndices() {
        int lastIndex = raftLog.getLastIndex();
        for (String peer : config.getPeerUrls().values()) {
            nextIndex.put(peer, lastIndex + 1);
            matchIndex.put(peer, 0);
        }
    }

    // Upon election: send initial empty AppendEntries RPCs (heartbeat) to each server; repeat during idle periods
    // to prevent election timeouts (§5.2)
    public void start() {
        lockManager.getStateReadLock().lock();
        try {
            if (nodeState.getCurrentRole() != Role.LEADER) return;
        } finally {
            lockManager.getStateReadLock().unlock();
        }

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
        while (true) {
            lockManager.getStateReadLock().lock();
            try {
                if (nodeState.getCurrentRole() != Role.LEADER) break;
            } finally {
                lockManager.getStateReadLock().unlock();
            }

            lockManager.getLogWriteLock().lock();
            lockManager.getStateWriteLock().lock();
            lockManager.getStateMachineWriteLock().lock();
            long startTime = System.currentTimeMillis();
            try {
                boolean ok = replicate(peer);
                if (ok) updateCommitIndex();
            } finally {
                lockManager.getStateMachineWriteLock().unlock();
                lockManager.getStateWriteLock().unlock();
                lockManager.getLogWriteLock().unlock();
            }
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

    // If last log index >= nextIndex for a follower: send AppendEntries RPC with log entries starting at nextIndex.
    private boolean replicate(String peer) {
        int ni = nextIndex.get(peer);
        int prevIdx = ni - 1;
        int prevTerm = (prevIdx >= 0) ? raftLog.getTermAt(prevIdx) : 0;
        List<LogEntry> entries = raftLog.getEntriesFrom(ni);

        AppendEntriesRequestDto dto = new AppendEntriesRequestDto(
                nodeState.getCurrentTerm(), nodeState.getNodeId(),
                prevIdx, prevTerm, entries, raftLog.getCommitIndex()
        );

        String peerId = config.getPeerUrls().entrySet().stream()
                .filter(entry -> entry.getValue().equals(peer))
                .map(Map.Entry::getKey)
                .map(String::valueOf)
                .findFirst()
                .orElse("unknown");

        try {
            String url = peer + "/raft/appendEntries";
            log.info("Node {} sending appendEntries to follower {}", nodeState.getNodeId(), peerId);
            ResponseEntity<AppendEntriesResponseDto> res = restTemplate.postForEntity(url, dto, AppendEntriesResponseDto.class);
            AppendEntriesResponseDto body = res.getBody() != null ? res.getBody() : new AppendEntriesResponseDto(-1, false);
            if (body.getTerm() > nodeState.getCurrentTerm()) {
                nodeStateManager.becomeFollower(body.getTerm());
                return false;
            }
            if (body.isSuccess()) {
                nextIndex.put(peer, ni + entries.size());
                matchIndex.put(peer, ni + entries.size() - 1);
                return true;
            } else {
                int newNextIndex = Math.max(0, ni - 1);
                log.warn("Node {}: follower {} is behind, decrementing nextIndex from {} to {} to catch up",
                        nodeState.getNodeId(), peerId, ni, newNextIndex);
                nextIndex.put(peer, newNextIndex);
                return false;
            }
        } catch (ResourceAccessException e) {
            log.error("Node {} failed to send appendEntries to Node {}: Connection refused", nodeState.getNodeId(), peerId);
            return false;
        } catch (Exception e) {
            log.error("Node {} failed to send appendEntries to follower {}: {}", nodeState.getNodeId(), peerId, e.getMessage(), e);
            return false;
        }
    }

    // If there exists an N such that N > commitIndex, a majority of matchIndex[i] >= N,
    // and log[N].term == currentTerm: set commitIndex = N (§5.3, §5.4)
    private void updateCommitIndex() {
        int majority = (config.getPeerUrls().size() + 1) / 2 + 1;
        int term = nodeState.getCurrentTerm();
        for (int i = raftLog.getLastIndex(); i > raftLog.getCommitIndex(); i--) {
            int count = 1;
            for (int idx : matchIndex.values()) {
                if (idx >= i) count++;
            }
            if (count >= majority && raftLog.getTermAt(i) == term) {
                raftLog.setCommitIndex(i);
                applyEntries();
                break;
            }
        }
    }

    // If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (§5.3)
    private void applyEntries() {
        int commit = raftLog.getCommitIndex();
        int lastApplied = nodeState.getLastApplied();
        for (int i = lastApplied + 1; i <= commit; i++) {
            try {
                LogEntry entry = raftLog.getEntryAt(i);
                stateMachine.apply(entry);
                nodeState.setLastApplied(i);
            } catch (Exception e) {
                System.exit(1);
            }
        }
    }
}
