package com.example.distributed_key_value_store.election;

import com.example.distributed_key_value_store.config.RaftConfig;
import com.example.distributed_key_value_store.dto.VoteRequestDto;
import com.example.distributed_key_value_store.dto.VoteResponseDto;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import com.example.distributed_key_value_store.node.Role;
import com.example.distributed_key_value_store.util.LockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionManager {
    private final RaftConfig config;
    private final RaftLog raftLog;
    private final RaftNodeState nodeState;
    private final RaftNodeStateManager stateManager;
    private final RestTemplate restTemplate;
    private final LockManager lockManager;
    /**
     * Handles a vote request from a candidate node per Raft algorithm.
     * Grants vote if conditions in Raft §5.1, §5.2, and §5.4 are met.
     */
    public VoteResponseDto handleVoteRequest(VoteRequestDto request) {
        lockManager.getLogReadLock().lock();
        lockManager.getStateWriteLock().lock();
        try {
            int currentTerm = nodeState.getCurrentTerm();
            int requestTerm = request.getTerm();
            int candidateId = request.getCandidateId();
            int candidateLastTerm = request.getLastLogTerm();
            int candidateLastIndex = request.getLastLogIndex();

            // Reply false if term < currentTerm (§5.1)
            if (requestTerm < currentTerm) {
                return new VoteResponseDto(currentTerm, false);
            }

            // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower (§5.1)
            if (requestTerm > currentTerm) {
                stateManager.becomeFollower(requestTerm);
                nodeState.setCurrentTerm(requestTerm);
            }

            // If votedFor is null or candidateId, and candidate's log is at least as up-to-date as receiver's log,
            // grant vote (§5.2, §5.4)
            Integer votedFor = nodeState.getVotedFor();
            if (votedFor != null && !votedFor.equals(candidateId)) {
                return new VoteResponseDto(currentTerm, false);
            }

            int localLastTerm = raftLog.getLastTerm();
            int localLastIndex = raftLog.getLastIndex();
            if (candidateLastTerm < localLastTerm ||
                    (candidateLastTerm == localLastTerm && candidateLastIndex < localLastIndex)) {
                return new VoteResponseDto(currentTerm, false);
            }

            nodeState.setVotedFor(candidateId);
            stateManager.resetElectionTimer();
            return new VoteResponseDto(currentTerm, true);
        } finally {
            lockManager.getStateWriteLock().unlock();
            lockManager.getLogReadLock().unlock();
        }
    }

    /**
     * On conversion to candidate, start election:
     * Increment currentTerm, vote for self, reset election timer, send RequestVote RPCs to all other servers.
     */
    public void startElection() {
        lockManager.getStateWriteLock().lock();
        lockManager.getLogReadLock().lock();
        try {
            if (nodeState.getCurrentRole() == Role.LEADER) {
                return;
            }

            log.info("Node {} starting election", nodeState.getNodeId());
            nodeState.setCurrentRole(Role.CANDIDATE);
            nodeState.incrementTerm();
            nodeState.setVotedFor(nodeState.getNodeId());

            int currentTerm = nodeState.getCurrentTerm();
            int lastLogIndex = raftLog.getLastIndex();
            int lastLogTerm = raftLog.getLastTerm();

            List<CompletableFuture<VoteResponseDto>> voteFutures = new ArrayList<>();
            ExecutorService executor = Executors.newCachedThreadPool();

            for (String peerUrl : config.getPeerUrls().values()) {
                CompletableFuture<VoteResponseDto> voteFuture = CompletableFuture
                        .supplyAsync(() -> requestVote(
                                currentTerm,
                                nodeState.getNodeId(),
                                lastLogIndex,
                                lastLogTerm,
                                peerUrl
                        ), executor)
                        .orTimeout(config.getElectionRpcTimeoutMillis(), TimeUnit.MILLISECONDS)
                        .exceptionally(throwable -> {
                            return new VoteResponseDto(currentTerm, false);
                        });
                voteFutures.add(voteFuture);
            }

            int majority = (config.getPeerUrls().size() + 1) / 2 + 1;
            AtomicInteger voteCount = new AtomicInteger(1);

            // If votes received from majority of servers: become leader (§5.2).
            for (CompletableFuture<VoteResponseDto> future : voteFutures) {
                future.thenAccept(response -> {
                    lockManager.getStateWriteLock().lock();
                    try {
                        if (nodeState.getCurrentRole() != Role.CANDIDATE || nodeState.getCurrentTerm() != currentTerm) {
                            return;
                        }
                        if (response != null && response.isVoteGranted()) {
                            int newVoteCount = voteCount.incrementAndGet();
                            if (newVoteCount >= majority) {
                                log.info("Node {} achieved majority, becoming LEADER", nodeState.getNodeId());
                                stateManager.becomeLeader();
                            }
                        }
                    } finally {
                        lockManager.getStateWriteLock().unlock();
                    }
                });
            }
            stateManager.resetElectionTimer();
        } finally {
            lockManager.getLogReadLock().unlock();
            lockManager.getStateWriteLock().unlock();
        }
    }

    private VoteResponseDto requestVote(int term, int candidateId, int lastLogIndex, int lastLogTerm, String peerUrl) {
        try {
            String url = peerUrl + "/raft/requestVote";
            VoteRequestDto dto = new VoteRequestDto(term, candidateId, lastLogIndex, lastLogTerm);
            ResponseEntity<VoteResponseDto> response = restTemplate.postForEntity(url, dto, VoteResponseDto.class);
            VoteResponseDto body = response.getBody() != null ? response.getBody() : new VoteResponseDto(term, false);

            // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower (§5.1)
            if (body.getTerm() > nodeState.getCurrentTerm()) {
                stateManager.becomeFollower(body.getTerm());
            }
            return body;
        } catch (Exception e) {
            return new VoteResponseDto(term, false);
        }
    }
}
