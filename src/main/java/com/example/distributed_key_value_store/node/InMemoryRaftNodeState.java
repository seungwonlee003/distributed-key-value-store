package com.example.distributed_key_value_store.node;

import com.example.distributed_key_value_store.config.RaftConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InMemoryRaftNodeState implements RaftNodeState {
    private final RaftConfig raftConfig;
    private int nodeId = 0;
    private int currentTerm = 0;
    private Integer votedFor = null;
    private int lastApplied = 0;
    private Role currentRole = Role.LEADER;
    private Integer currentLeader = null;

    @Override
    public int getNodeId() {
        return nodeId;
    }

    @Override
    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public int getCurrentTerm() {
        return currentTerm;
    }

    @Override
    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
    }

    @Override
    public void incrementTerm() {
        this.currentTerm++;
    }

    @Override
    public Integer getVotedFor() {
        return votedFor;
    }

    @Override
    public void setVotedFor(Integer votedFor) {
        this.votedFor = votedFor;
    }

    @Override
    public int getLastApplied() {
        return lastApplied;
    }

    @Override
    public void setLastApplied(int lastApplied) {
        this.lastApplied = lastApplied;
    }

    @Override
    public Role getCurrentRole() {
        return currentRole;
    }

    @Override
    public void setCurrentRole(Role role) {
        this.currentRole = role;
    }

    @Override
    public Integer getCurrentLeader() {
        return currentLeader;
    }

    @Override
    public void setCurrentLeader(Integer leaderId) {
        this.currentLeader = leaderId;
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
        return currentRole == Role.LEADER;
    }
}
