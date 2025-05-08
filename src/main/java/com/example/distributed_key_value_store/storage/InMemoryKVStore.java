package com.example.distributed_key_value_store.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class InMemoryKVStore implements KVStore {
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> clientStore = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value) {
        store.put(key, value);
    }

    @Override
    public void remove(String key) {
        if (!store.containsKey(key)) {
            throw new IllegalStateException("Key '" + key + "' does not exist for removal");
        }
        store.remove(key);
    }

    @Override
    public String get(String key) {
        return store.get(key);
    }

    @Override
    public Long getLastSequenceNumber(String clientId) {
        return clientStore.get(clientId);
    }

    @Override
    public void setLastSequenceNumber(String clientId, Long sequenceNumber) {
        clientStore.put(clientId, sequenceNumber);
    }
}
