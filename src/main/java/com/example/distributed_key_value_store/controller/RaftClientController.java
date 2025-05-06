package com.example.distributed_key_value_store.controller;

import com.example.distributed_key_value_store.dto.WriteRequestDto;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.replication.ClientRequestHandler;
import com.example.distributed_key_value_store.storage.KVStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/raft/client")
public class RaftClientController {
    private final RaftNodeState nodeState;
    private final ClientRequestHandler clientRequestHandler;
    private final KVStore kvStore;
    private final String NO_OP_ENTRY = "NO_OP_ENTRY";

    @GetMapping("/get")
    public ResponseEntity<String> get(@RequestParam String key) {
        handleRead(new WriteRequestDto(NO_OP_ENTRY, Long.MAX_VALUE, NO_OP_ENTRY, NO_OP_ENTRY));
        return ResponseEntity.ok(kvStore.get(key));
    }

    @PostMapping("/insert")
    public ResponseEntity<String> insert(@RequestBody WriteRequestDto request) {
        return handleWrite(request, LogEntry.Operation.INSERT, "Insert");
    }

    @PostMapping("/update")
    public ResponseEntity<String> update(@RequestBody WriteRequestDto request) {
        return handleWrite(request, LogEntry.Operation.UPDATE, "Update");
    }

    @PostMapping("/delete")
    public ResponseEntity<String> delete(@RequestBody WriteRequestDto request) {
        return handleWrite(request, LogEntry.Operation.DELETE, "Delete");
    }

    private void handleRead(WriteRequestDto request){
        LogEntry entry = new LogEntry(
                nodeState.getCurrentTerm(),
                request.getKey(),
                request.getValue(),
                LogEntry.Operation.INSERT,
                request.getClientId(),
                request.getSequenceNumber()
        );
        boolean committed = clientRequestHandler.handle(entry);
        if(!committed) throw new RuntimeException("Can't process the read");
    }

    private ResponseEntity<String> handleWrite(WriteRequestDto request, LogEntry.Operation op, String label) {
        LogEntry entry = new LogEntry(
                nodeState.getCurrentTerm(),
                request.getKey(),
                request.getValue(),
                op,
                request.getClientId(),
                request.getSequenceNumber()
        );

        boolean committed = clientRequestHandler.handle(entry);
        if (committed) {
            return ResponseEntity.ok(label + " committed");
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(label + " failed (not committed or leadership lost)");
        }
    }
}