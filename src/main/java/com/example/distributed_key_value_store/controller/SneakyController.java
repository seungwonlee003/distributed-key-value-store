package com.example.distributed_key_value_store.controller;

import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.util.LockManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/raft/sneaky")
public class SneakyController {
    private final RaftLog raftLog;
    private final LockManager lockManager;

    @GetMapping("/inspect/log")
    public ResponseEntity<String> getLog() {
        StringBuilder logRecord = new StringBuilder();
        logRecord.append("Log Entries: \n");
        int index = 0;

        lockManager.getLogReadLock().lock();
        try {
            for (LogEntry entry : raftLog.getEntriesFrom(0)) {
                logRecord.append(String.format(
                        "Index=%d, Term=%d, Operation=%s, Key=%s, Value=%s, ClientId=%s, SequenceNumber=%d\n",
                        index, entry.getTerm(), entry.getOperation(), entry.getKey(),
                        entry.getValue() != null ? entry.getValue() : "null",
                        entry.getClientId(), entry.getSequenceNumber()
                ));
                index++;
            }
        } finally {
            lockManager.getLogReadLock().unlock();
        }

        if (index == 0) {
            logRecord.append("No log entries found.\n");
        }
        return ResponseEntity.ok(logRecord.toString());
    }

    @PostMapping("/truncate/log")
    public ResponseEntity<Void> truncateLog(@RequestParam int index) {
        lockManager.getLogWriteLock().lock();
        try {
            raftLog.deleteFrom(index);
        } finally {
            lockManager.getLogWriteLock().unlock();
        }
        return ResponseEntity.ok().build();
    }
}
