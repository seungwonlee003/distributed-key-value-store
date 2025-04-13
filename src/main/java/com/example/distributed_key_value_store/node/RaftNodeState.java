package com.example.distributed_key_value_store.node;

import com.example.distributed_key_value_store.config.RaftConfig;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
public abstract class RaftNodeState {
    private RaftConfig raftConfig;
    private int nodeId;
    private int currentTerm;
    private Integer votedFor;
    private int lastApplied;

    private Role currentRole;
    private Integer currentLeader;

    public abstract void init();

    public abstract void setCurrentTerm(int term);

    public abstract void incrementTerm();

    public abstract void setVotedFor(Integer votedFor);

    public abstract void setLastApplied(int lastApplied);

    public abstract void setCurrentLeader(Integer currentLeader);

    public abstract void setCurrentRole(Role currentRole);

    public abstract void setNodeId(int nodeId);

    public String getCurrentLeaderUrl(){
        return raftConfig.getPeerUrls().get(currentLeader);
    }

    public boolean isLeader(){
        return currentRole.equals(Role.LEADER);
    }
}
