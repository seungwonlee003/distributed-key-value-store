package com.example.distributed_key_value_store.log;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
public class LogEntry {
    private int term;
    private String key;
    private String value;
    private Operation operation;

    private String clientId;
    private long sequenceNumber;

    public enum Operation {
        PUT,
        DELETE
    }

    @Override
    public String toString() {
        return String.format("LogEntry{term=%d, key='%s', value='%s', operation=%s, clientId='%s', sequenceNumber=%d}",
                term, key, value, operation, clientId, sequenceNumber);
    }
}
