package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.dto.AppendEntriesRequestDto;
import com.example.distributed_key_value_store.dto.AppendEntriesResponseDto;
import com.example.distributed_key_value_store.log.LogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RaftReplicationManager {
    @Autowired
    @Lazy
    private LogReplicator logReplicator;
    private final ClientRequestHandler clientRequestHandler;
    private final AppendEntriesHandler appendEntriesHandler;

    public boolean handleClientRequest(LogEntry entry) {
        return clientRequestHandler.handle(entry);
    }

    public void startLogReplication() {
        logReplicator.start();
    }

    public AppendEntriesResponseDto handleAppendEntries(AppendEntriesRequestDto dto) {
        return appendEntriesHandler.handle(dto);
    }

    public void initializeIndices() {
        logReplicator.initializeIndices();
    }
}
