package com.example.distributed_key_value_store.storage;

import com.example.distributed_key_value_store.log.LogEntry;

public interface StateMachine {
    void apply(LogEntry entry);
}
