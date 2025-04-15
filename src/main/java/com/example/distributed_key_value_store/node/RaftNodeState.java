package com.example.distributed_key_value_store.node;

import com.example.distributed_key_value_store.config.RaftConfig;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RaftNodeState {
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

    public String getCurrentLeaderUrl() {
        return currentLeader != null ? raftConfig.getPeerUrls().get(currentLeader) : null;
    }

    public boolean isLeader() {
        return currentRole == Role.LEADER;
    }
}
