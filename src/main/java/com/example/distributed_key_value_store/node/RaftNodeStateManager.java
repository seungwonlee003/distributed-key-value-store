package com.example.distributed_key_value_store.node;

import com.example.distributed_key_value_store.election.ElectionTimer;
import com.example.distributed_key_value_store.replication.HeartbeatManager;
import com.example.distributed_key_value_store.replication.RaftReplicationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RaftNodeStateManager {
    private final RaftNodeState state;
    @Autowired
    @Lazy
    private HeartbeatManager heartbeatManager;
    @Autowired
    @Lazy
    private ElectionTimer electionTimer;
    private final RaftReplicationManager raftReplicationManager;

    public synchronized void becomeLeader() {
        state.setCurrentRole(Role.LEADER);
        System.out.printf("Node %s became leader for term %d%n", state.getNodeId(), state.getCurrentTerm());
        raftReplicationManager.initializeIndices();
        heartbeatManager.startHeartbeats();
    }

    public synchronized void becomeFollower(int newTerm) {
        state.setCurrentTerm(newTerm);
        state.setCurrentRole(Role.FOLLOWER);
        state.setVotedFor(null);
        state.setCurrentLeader(null);
        heartbeatManager.stopHeartbeats();
        electionTimer.reset();
    }

    public void resetElectionTimer() {
        electionTimer.reset();
    }
}
