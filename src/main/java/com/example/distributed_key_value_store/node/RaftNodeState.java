package com.example.distributed_key_value_store.node;

import com.example.distributed_key_value_store.config.RaftConfig;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class RaftNodeState {
    private final RaftConfig raftConfig;
    private final int nodeId;
    private int currentTerm;
    private Integer votedFor;
    private int lastApplied;
    private Role currentRole;
    private Integer currentLeader;

    public RaftNodeState(RaftConfig raftConfig, int nodeId) {
        this.raftConfig = raftConfig;
        this.nodeId = nodeId;
        this.currentTerm = 0;
        this.votedFor = null;
        this.lastApplied = 0;
        this.currentRole = Role.FOLLOWER;
        this.currentLeader = null;
    }

    public void init() {
        setCurrentTerm(0);
        setVotedFor(null);
        setLastApplied(0);
        setCurrentRole(Role.FOLLOWER);
        setCurrentLeader(null);
    }

    public void incrementTerm() {
        this.currentTerm++;
    }

    public String getCurrentLeaderUrl() {
        if (currentLeader != null) {
            return raftConfig.getPeerUrls().get(currentLeader);
        }
        return null;
    }

    public boolean isLeader() {
        return currentRole == Role.LEADER;
    }
}
