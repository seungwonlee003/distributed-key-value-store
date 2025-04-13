package com.example.distributed_key_value_store.controller;

import com.example.distributed_key_value_store.dto.WriteRequestDto;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.Role;
import com.example.distributed_key_value_store.replication.ClientRequestHandler;
import com.example.distributed_key_value_store.service.ReadHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/raft/client")
public class RaftClientController {
    private final ReadHandler readHandler;
    private final RaftNodeState nodeState;
    private final ClientRequestHandler clientRequestHandler;

    @GetMapping("/get")
    public ResponseEntity<String> get(@RequestParam String key) {
        String val = readHandler.handleRead(key);
        return ResponseEntity.ok(val);
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

    private ResponseEntity<String> handleWrite(WriteRequestDto request, LogEntry.Operation op, String label) {
        if (nodeState.getCurrentRole() != Role.LEADER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not the leader");
        }

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