package com.example.distributed_key_value_store.controller;

import com.example.distributed_key_value_store.dto.*;
import com.example.distributed_key_value_store.election.ElectionManager;
import com.example.distributed_key_value_store.replication.RaftReplicationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/raft")
public class RaftRpcController {
    private final ElectionManager electionManager;
    private final RaftReplicationManager raftReplicationManager;

    @PostMapping("/requestVote")
    public ResponseEntity<VoteResponseDto> vote(@RequestBody VoteRequestDto requestVoteDTO) {
        return ResponseEntity.ok(electionManager.handleVoteRequest(requestVoteDTO));
    }

    @PostMapping("/appendEntries")
    public ResponseEntity<AppendEntriesResponseDto> appendEntries(@RequestBody AppendEntriesRequestDto dto) {
        return ResponseEntity.ok(raftReplicationManager.handleAppendEntries(dto));
    }
}
