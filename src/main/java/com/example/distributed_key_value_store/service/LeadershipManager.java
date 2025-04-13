package com.example.distributed_key_value_store.service;

import com.example.distributed_key_value_store.config.RaftConfig;
import com.example.distributed_key_value_store.dto.ConfirmLeadershipRequestDto;
import com.example.distributed_key_value_store.dto.HeartbeatResponseDto;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import com.example.distributed_key_value_store.node.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
public class LeadershipManager {
    private final RestTemplate restTemplate;
    private final RaftNodeState raftNodeState;
    private final RaftNodeStateManager raftNodeStateManager;
    private final RaftConfig raftConfig;

    public void confirmLeadership() {
        if (!raftNodeState.isLeader()) {
            throw new IllegalStateException("Not leader.");
        }
        int currentTerm = raftNodeState.getCurrentTerm();
        List<CompletableFuture<HeartbeatResponseDto>> confirmationFutures = new ArrayList<>();
        ExecutorService executor = Executors.newCachedThreadPool();

        for (String peerUrl : raftConfig.getPeerUrls().values()) {
            confirmationFutures.add(
                    CompletableFuture.supplyAsync(() -> requestLeadershipConfirmation(
                                    raftNodeState.getNodeId(), currentTerm, peerUrl
                            ), executor)
                            .orTimeout(raftConfig.getElectionRpcTimeoutMillis(), TimeUnit.MILLISECONDS)
                            .exceptionally(throwable -> new HeartbeatResponseDto(false, currentTerm))
            );
        }

        int totalNodes = raftConfig.getPeerUrls().size() + 1;
        int majority = totalNodes / 2 + 1;
        int requiredPeerConfirmations = majority - 1; // Subtract self-confirmation
        CountDownLatch latch = new CountDownLatch(requiredPeerConfirmations);

        for (CompletableFuture<HeartbeatResponseDto> future : confirmationFutures) {
            future.thenAccept(response -> {
                synchronized (this) {
                    if (!raftNodeState.isLeader() || raftNodeState.getCurrentTerm() != currentTerm) {
                        return;
                    }
                    if (response != null && response.isSuccess() && response.getTerm() == currentTerm) {
                        latch.countDown();
                    }
                }
            });
        }

        try {
            latch.await(raftConfig.getElectionRpcTimeoutMillis() * 2, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while confirming leadership.");
        }

        if (latch.getCount() > 0) {
            throw new IllegalStateException("Leadership confirmation failed.");
        }
    }

    private HeartbeatResponseDto requestLeadershipConfirmation(int leaderId, int term, String peerUrl) {
        try {
            String url = peerUrl + "/raft/confirmLeadership";
            ConfirmLeadershipRequestDto dto = new ConfirmLeadershipRequestDto(leaderId, term);
            ResponseEntity<HeartbeatResponseDto> response = restTemplate.postForEntity(url, dto, HeartbeatResponseDto.class);
            HeartbeatResponseDto body = response.getBody() != null ? response.getBody() : new HeartbeatResponseDto(term, false);
            if (body.getTerm() > raftNodeState.getCurrentTerm()) {
                raftNodeStateManager.becomeFollower(body.getTerm());
            }
            return body;
        } catch (Exception e) {
            return new HeartbeatResponseDto(false, term);
        }
    }

    public HeartbeatResponseDto handleConfirmLeadership(ConfirmLeadershipRequestDto request) {
        if (request.getTerm() > raftNodeState.getCurrentTerm()) {
            raftNodeStateManager.becomeFollower(request.getTerm());
            return new HeartbeatResponseDto(false, raftNodeState.getCurrentTerm());
        }
        if (raftNodeState.getCurrentRole() != Role.FOLLOWER) {
            return new HeartbeatResponseDto(false, raftNodeState.getCurrentTerm());
        }
        boolean success = request.getTerm() == raftNodeState.getCurrentTerm() &&
                (raftNodeState.getCurrentLeader() == null ||
                        raftNodeState.getCurrentLeader().equals(request.getNodeId()));
        return new HeartbeatResponseDto(success, raftNodeState.getCurrentTerm());
    }
}
