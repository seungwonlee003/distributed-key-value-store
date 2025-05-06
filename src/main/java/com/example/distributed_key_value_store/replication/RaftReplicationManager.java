package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.dto.AppendEntryRequestDto;
import com.example.distributed_key_value_store.dto.AppendEntryResponseDto;
import com.example.distributed_key_value_store.log.LogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RaftReplicationManager {
    private final ClientRequestHandler clientRequestHandler;
    @Autowired
    @Lazy
    private LogReplicator logReplicator;
    private final AppendEntryHandler appendEntryHandler;

    public boolean handleClientRequest(LogEntry entry) {
        return clientRequestHandler.handle(entry);
    }

    public void startLogReplication() {
        logReplicator.start();
    }

    public AppendEntryResponseDto handleAppendEntries(AppendEntryRequestDto dto) {
        return appendEntryHandler.handle(dto);
    }

    public void initializeIndices() {
        logReplicator.initializeIndices();
    }
}
