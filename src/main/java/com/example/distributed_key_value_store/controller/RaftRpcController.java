package com.example.distributed_key_value_store.controller;

import com.example.distributed_key_value_store.dto.*;
import com.example.distributed_key_value_store.election.ElectionManager;
import com.example.distributed_key_value_store.replication.RaftReplicationManager;
import com.example.distributed_key_value_store.service.LeadershipManager;
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
    private final LeadershipManager leadershipManager;

    @PostMapping("/requestVote")
    public ResponseEntity<VoteResponseDto> vote(@RequestBody VoteRequestDto requestVoteDTO) {
        return ResponseEntity.ok(electionManager.handleVoteRequest(requestVoteDTO));
    }

    //test
    @PostMapping("/appendEntries")
    public ResponseEntity<AppendEntryResponseDto> appendEntries(@RequestBody AppendEntryRequestDto dto) {
        return ResponseEntity.ok(raftReplicationManager.handleAppendEntries(dto));
    }

    @PostMapping("/confirmLeadership")
    public ResponseEntity<HeartbeatResponseDto> confirmLeadership(@RequestBody ConfirmLeadershipRequestDto dto) {
        return ResponseEntity.ok(leadershipManager.handleConfirmLeadership(dto));
    }
}
