package com.example.distributed_key_value_store.service;

import com.example.distributed_key_value_store.storage.KVStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReadHandler {
    private final KVStore kvStore;
    private final LeadershipManager leadershipManager;

    public String handleRead(String key){
        leadershipManager.confirmLeadership();
        return kvStore.get(key);
    }
}
