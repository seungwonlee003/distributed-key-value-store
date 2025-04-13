package com.example.distributed_key_value_store.log;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class LogEntry {
    private final int term;
    private final String key;
    private final String value;
    private final Operation operation;

    private final String clientId;
    private final long sequenceNumber;

    public enum Operation {
        INSERT,
        UPDATE,
        DELETE
    }
}
