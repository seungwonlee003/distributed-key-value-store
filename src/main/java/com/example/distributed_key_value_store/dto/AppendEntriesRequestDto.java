package com.example.distributed_key_value_store.dto;

import com.example.distributed_key_value_store.log.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AppendEntriesRequestDto {
    private int term;
    private int leaderId;
    private int prevLogIndex;
    private int prevLogTerm;
    private List<LogEntry> entries;
    private int leaderCommit;
}
