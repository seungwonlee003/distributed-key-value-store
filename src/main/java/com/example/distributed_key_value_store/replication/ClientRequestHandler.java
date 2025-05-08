package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.config.RaftConfig;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.Role;
import com.example.distributed_key_value_store.util.LockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientRequestHandler {
    private final RaftLog raftLog;
    private final RaftNodeState raftNodeState;
    private final RaftConfig raftConfig;
    private final LockManager lockManager;

    // If command received from the client: append entry to local log, respond after entry applied
    // to state machine (§5.3)
    public boolean handle(LogEntry clientEntry) {
        lockManager.getStateReadLock().lock();
        lockManager.getLogWriteLock().lock();
        int entryIndex;
        try {
            if (!raftNodeState.isLeader()) return false;
            raftLog.append(clientEntry);
            entryIndex = raftLog.getLastIndex();
            log.info("Node {}: Client entry appended at index {}", raftNodeState.getNodeId(), entryIndex);
        } finally {
            lockManager.getLogWriteLock().unlock();
            lockManager.getStateReadLock().unlock();
        }

        long start = System.currentTimeMillis();
        long timeoutMillis = raftConfig.getClientRequestTimeoutMillis();

        while (true) {
            lockManager.getLogReadLock().lock();
            lockManager.getStateReadLock().lock();
            try {
                if (raftNodeState.getCurrentRole() != Role.LEADER) {
                    return false;
                }
                if (raftNodeState.getLastApplied() >= entryIndex) {
                    log.info("Node {}: Client entry at index {} successfully applied to state machine", raftNodeState.getNodeId(), entryIndex);
                    return true;
                }
            } finally {
                lockManager.getStateReadLock().unlock();
                lockManager.getLogReadLock().unlock();
            }

            if (System.currentTimeMillis() - start > timeoutMillis) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
