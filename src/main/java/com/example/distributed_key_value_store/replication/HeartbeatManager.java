package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import com.example.distributed_key_value_store.node.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class HeartbeatManager {
    @Autowired
    @Lazy
    private LogReplicator logReplicator;
    private final RaftNodeState nodeState;
    @Autowired
    @Lazy
    private RaftNodeStateManager nodeStateManager;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeatFuture;

    public void startHeartbeats(){
        stopHeartbeats();
        nodeStateManager.resetElectionTimer();
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (nodeState.getCurrentRole() == Role.LEADER) {
                logReplicator.start();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public void stopHeartbeats() {
        if (heartbeatFuture != null && !heartbeatFuture.isDone()) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }
}
