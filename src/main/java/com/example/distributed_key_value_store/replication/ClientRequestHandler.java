package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.config.RaftConfig;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientRequestHandler {
    private final RaftLog raftLog;
    private final RaftNodeState raftNodeState;
    private final RaftConfig raftConfig;

    // If command received from the client: append entry to local log, respond after entry applied
    // to state machine (§5.3)
    public boolean handle(LogEntry clientEntry) {
        raftLog.append(clientEntry);
        int entryIndex = raftLog.getLastIndex();

        long start = System.currentTimeMillis();
        long timeoutMillis = raftConfig.getClientRequestTimeoutMillis();

        while (raftNodeState.getCurrentRole() == Role.LEADER) {
            if (raftNodeState.getLastApplied() >= entryIndex) {
                return true;
            }
            if (System.currentTimeMillis() - start > timeoutMillis) {
                return false;
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
