package com.example.distributed_key_value_store.storage;

import com.example.distributed_key_value_store.log.LogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StateMachineImpl implements StateMachine {
    private final KVStore store;

    @Override
    public void apply(LogEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Log entry cannot be null");
        }

        // deduplication check
        String clientId = entry.getClientId();
        long sequenceNumber = entry.getSequenceNumber();
        Long lastSequenceNumber = store.getLastSequenceNumber(clientId);
        if (lastSequenceNumber != null && sequenceNumber <= lastSequenceNumber) {
            return;
        }

        switch (entry.getOperation()) {
            case INSERT, UPDATE:
                store.put(entry.getKey(), entry.getValue());
                break;
            case DELETE:
                store.remove(entry.getKey());
                break;
            default:
                throw new IllegalStateException("Unknown operation: " + entry.getOperation());
        }
        store.setLastSequenceNumber(clientId, sequenceNumber);
    }
}
