package com.example.distributed_key_value_store.node;

import com.example.distributed_key_value_store.election.ElectionTimer;
import com.example.distributed_key_value_store.replication.RaftReplicationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RaftNodeStateManager {
    @Autowired
    @Lazy
    private ElectionTimer electionTimer;
    private final RaftNodeState state;
    private final RaftReplicationManager replicationManager;

    public void becomeLeader() {
        cancelElectionTimer();
        state.setCurrentRole(Role.LEADER);
        System.out.printf("Node %s became leader for term %d%n", state.getNodeId(), state.getCurrentTerm());
        replicationManager.initializeIndices();
        replicationManager.startLogReplication();
    }

    public void becomeFollower(int newTerm) {
        state.setCurrentTerm(newTerm);
        state.setCurrentRole(Role.FOLLOWER);
        state.setVotedFor(null);
        state.setCurrentLeader(null);
        resetElectionTimer();
    }

    public void cancelElectionTimer() {
        electionTimer.cancel();
    }

    public void resetElectionTimer() {
        electionTimer.reset();
    }
}
