package com.example.distributed_key_value_store.controller;

import com.example.distributed_key_value_store.dto.*;
import com.example.distributed_key_value_store.election.ElectionManager;
import com.example.distributed_key_value_store.replication.ClientRequestHandler;
import com.example.distributed_key_value_store.service.ReadHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/raft/rpc")
public class RaftRpcController {
    private final ElectionManager electionManager;
    private final ClientRequestHandler clientRequestHandler;
    private final ReadHandler readHandler;

    @PostMapping("/requestVote")
    public ResponseEntity<VoteResponseDto> vote(@RequestBody VoteRequestDto requestVoteDTO) {
        return ResponseEntity.ok(electionManager.handleVoteRequest(requestVoteDTO));
    }

    @PostMapping("/appendEntries")
    public ResponseEntity<AppendEntryResponseDto> appendEntries(@RequestBody AppendEntryRequestDto dto) {
        return ResponseEntity.ok(clientRequestHandler.handleAppendEntries(dto));
    }

    @PostMapping("/confirmLeadership")
    public ResponseEntity<HeartbeatResponseDto> confirmLeadership(){
        return ResponseEntity.ok(readHandler.handleConfirmLeadership());
    }
}
