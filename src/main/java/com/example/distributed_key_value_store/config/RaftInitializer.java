package com.example.distributed_key_value_store.config;

import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RaftInitializer {
    private final RaftNodeState raftNodeState;
    private final RaftNodeStateManager raftNodeStateManager;
    private final RaftConfig raftConfig;

    @PostConstruct
    public void init() {
        raftNodeState.setNodeId(raftConfig.getNodeId());
        raftNodeStateManager.becomeFollower(0);
    }
}