package com.example.distributed_key_value_store.node;

import com.example.distributed_key_value_store.config.RaftConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InMemoryRaftNodeState extends RaftNodeState {

    @Override
    public void init() {
        this.setNodeId(0);
        this.setCurrentTerm(0);
        this.setVotedFor(null);
        this.setLastApplied(0);
        this.setCurrentRole(Role.FOLLOWER);
        this.setCurrentLeader(null);
    }

    @Override
    public void setCurrentTerm(int term) {
        this.currentTerm = term;
    }

    @Override
    public void incrementTerm() {
        this.currentTerm++;
    }

    @Override
    public void setVotedFor(Integer votedFor) {
        this.votedFor = votedFor;
    }

    @Override
    public void setLastApplied(int lastApplied) {
        this.lastApplied = lastApplied;
    }

    @Override
    public void setCurrentLeader(Integer currentLeader) {
        this.currentLeader = currentLeader;
    }

    @Override
    public void setCurrentRole(Role currentRole) {
        this.currentRole = currentRole;
    }

    @Override
    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public String getCurrentLeaderUrl() {
        if (currentLeader != null) {
            return raftConfig.getPeerUrls().get(currentLeader);
        }
        return null;
    }

    @Override
    public boolean isLeader() {
        return currentRole.equals(Role.LEADER);
    }
}
