package com.example.distributed_key_value_store.controller;

import com.example.distributed_key_value_store.dto.WriteRequestDto;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.replication.RaftReplicationManager;
import com.example.distributed_key_value_store.storage.KVStore;
import com.example.distributed_key_value_store.util.LockManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/raft/client")
public class RaftClientController {
    private final RaftNodeState nodeState;
    private final RaftReplicationManager replicationManager;
    private final KVStore kvStore;
    private final LockManager lockManager;

    @GetMapping("/get")
    public ResponseEntity<String> get(@RequestParam String key) {
        // Ensure linearizable semantics (applies NO_OP_ENTRY to sync with leader)
        String NO_OP_ENTRY = "NO_OP_ENTRY";
        try {
            handleRead(new WriteRequestDto(NO_OP_ENTRY, Long.MAX_VALUE, NO_OP_ENTRY, NO_OP_ENTRY));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process read request: " + e.getMessage());
        }

        lockManager.getStateMachineReadLock().lock();
        try {
            String value = kvStore.get(key);
            if (value == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Key not found: " + key);
            }
            return ResponseEntity.ok(value);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving key: " + e.getMessage());
        } finally {
            lockManager.getStateMachineReadLock().unlock();
        }
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
        boolean committed = replicationManager.handleClientRequest(entry);
        if(!committed) throw new RuntimeException("Can't process the read at this moment");
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

        boolean committed = replicationManager.handleClientRequest(entry);
        if (committed) {
            return ResponseEntity.ok(label + " committed");
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(label + " failed (not committed or leadership lost)");
        }
    }
}