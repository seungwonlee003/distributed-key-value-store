package com.example.distributed_key_value_store.election;

import com.example.distributed_key_value_store.config.RaftConfig;
import com.example.distributed_key_value_store.dto.VoteRequestDto;
import com.example.distributed_key_value_store.dto.VoteResponseDto;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import com.example.distributed_key_value_store.node.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ElectionManager {
    private final RaftConfig config;
    private final RaftLog log;
    private final RaftNodeState nodeState;
    private final RaftNodeStateManager stateManager;
    private final RestTemplate restTemplate;

    public synchronized VoteResponseDto handleVoteRequest(VoteRequestDto request) {
        int currentTerm = nodeState.getCurrentTerm();
        int requestTerm = request.getTerm();
        int candidateId = request.getCandidateId();
        int candidateLastTerm = request.getLastLogTerm();
        int candidateLastIndex = request.getLastLogIndex();


        if (requestTerm < currentTerm) {
            return new VoteResponseDto(currentTerm, false);
        }

        if (requestTerm > currentTerm) {
            stateManager.becomeFollower(requestTerm);
            nodeState.setCurrentTerm(requestTerm);
        }

        Integer votedFor = nodeState.getVotedFor();
        if (votedFor != null && !votedFor.equals(candidateId)) {
            return new VoteResponseDto(currentTerm, false);
        }

        int localLastTerm = log.getLastTerm();
        int localLastIndex = log.getLastIndex();
        if (candidateLastTerm < localLastTerm ||
                (candidateLastTerm == localLastTerm && candidateLastIndex < localLastIndex)) {
            return new VoteResponseDto(currentTerm, false);
        }

        nodeState.setVotedFor(candidateId);
        stateManager.resetElectionTimer();
        return new VoteResponseDto(currentTerm, true);
    }

    public void startElection() {
        synchronized (this) {
            if (nodeState.getCurrentRole() == Role.LEADER) return;

            nodeState.setCurrentRole(Role.CANDIDATE);
            nodeState.incrementTerm();
            nodeState.setVotedFor(nodeState.getNodeId());

            int currentTerm = nodeState.getCurrentTerm();
            List<CompletableFuture<VoteResponseDto>> voteFutures = new ArrayList<>();
            ExecutorService executor = Executors.newCachedThreadPool();

            for (String peerUrl : config.getPeerUrls().values()) {
                CompletableFuture<VoteResponseDto> voteFuture = CompletableFuture
                        .supplyAsync(() -> requestVote(
                                currentTerm,
                                nodeState.getNodeId(),
                                log.getLastIndex(),
                                log.getLastTerm(),
                                peerUrl
                        ), executor)
                        .orTimeout(config.getElectionRpcTimeoutMillis(), TimeUnit.MILLISECONDS)
                        .exceptionally(throwable -> new VoteResponseDto(currentTerm, false));
                voteFutures.add(voteFuture);
            }

            int majority = (config.getPeerUrls().size() + 1) / 2 + 1;
            AtomicInteger voteCount = new AtomicInteger(1);

            for (CompletableFuture<VoteResponseDto> future : voteFutures) {
                future.thenAccept(response -> {
                    synchronized (this) {
                        if (nodeState.getCurrentRole() != Role.CANDIDATE || nodeState.getCurrentTerm() != currentTerm) {
                            return;
                        }
                        if (response != null && response.isVoteGranted()) {
                            if (voteCount.incrementAndGet() >= majority) {
                                stateManager.becomeLeader();
                                return;
                            }
                        }
                    }
                });
            }

            stateManager.resetElectionTimer();
        }
    }

    private VoteResponseDto requestVote(int term, int candidateId, int lastLogIndex, int lastLogTerm, String peerUrl){
        try {
            String url = peerUrl + "/raft/requestVote";
            VoteRequestDto dto = new VoteRequestDto(term, candidateId, lastLogIndex, lastLogTerm);
            ResponseEntity<VoteResponseDto> response = restTemplate.postForEntity(url, dto, VoteResponseDto.class);
            VoteResponseDto body = response.getBody() != null ? response.getBody() : new VoteResponseDto(term, false);

            if (body.getTerm() > nodeState.getCurrentTerm()) {
                stateManager.becomeFollower(body.getTerm());
            }
            return body;
        } catch (Exception e) {
            return new VoteResponseDto(term, false);
        }
    }
}
