package com.example.distributed_key_value_store.node;

import com.example.distributed_key_value_store.config.RaftConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@RequiredArgsConstructor
public class RaftNodeState {
    private final RaftConfig raftConfig;
    private int nodeId = 0;
    private int currentTerm = 0;
    private Integer votedFor = null;
    private int lastApplied = 0;
    private Role currentRole = Role.LEADER;
    private Integer currentLeader = null;

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
