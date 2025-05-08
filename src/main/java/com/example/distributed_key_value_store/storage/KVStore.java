package com.example.distributed_key_value_store.storage;

public interface KVStore {
    void put(String key, String value);
    void remove(String key);
    String get(String key);
    Long getLastSequenceNumber(String clientId);
    void setLastSequenceNumber(String clientId, Long sequenceNumber);
}
