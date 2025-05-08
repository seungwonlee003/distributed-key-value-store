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
}
