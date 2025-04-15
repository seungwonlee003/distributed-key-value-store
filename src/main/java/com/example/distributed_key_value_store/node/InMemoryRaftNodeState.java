package com.example.distributed_key_value_store.node;

import com.example.distributed_key_value_store.config.RaftConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InMemoryRaftNodeState extends RaftNodeState {
    private final RaftConfig raftConfig;

    public InMemoryRaftNodeState(RaftConfig raftConfig, int nodeId) {
        super(raftConfig, nodeId);
    }
}
