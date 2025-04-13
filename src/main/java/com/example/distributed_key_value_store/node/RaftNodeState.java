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

    @PostConstruct
    abstract void init();

    abstract void setCurrentTerm(int term);

    abstract void incrementTerm();

    abstract void setVotedFor(Integer votedFor);

    abstract void setLastApplied(int lastApplied);

    abstract void setCurrentLeader(Integer currentLeader);

    public String getCurrentLeaderUrl(){
        return raftConfig.getPeerUrls().get(currentLeader);
    }

    public boolean isLeader(){
        return currentRole.equals(Role.LEADER);
    }
}
