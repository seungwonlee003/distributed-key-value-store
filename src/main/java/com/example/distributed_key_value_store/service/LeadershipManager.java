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
            System.out.println("Node " + raftNodeState.getNodeId() + " not LEADER, cannot confirm leadership");
            throw new IllegalStateException("Not leader.");
        }
        System.out.println("Node " + raftNodeState.getNodeId() + " starting leadership confirmation, term " + raftNodeState.getCurrentTerm());
        int currentTerm = raftNodeState.getCurrentTerm();
        List<CompletableFuture<HeartbeatResponseDto>> confirmationFutures = new ArrayList<>();
        ExecutorService executor = Executors.newCachedThreadPool();

        for (String peerUrl : raftConfig.getPeerUrls().values()) {
            System.out.println("Node " + raftNodeState.getNodeId() + " sending confirmLeadership to " + peerUrl);
            confirmationFutures.add(
                    CompletableFuture.supplyAsync(() -> requestLeadershipConfirmation(
                                    raftNodeState.getNodeId(), currentTerm, peerUrl
                            ), executor)
                            .orTimeout(raftConfig.getElectionRpcTimeoutMillis(), TimeUnit.MILLISECONDS)
                            .exceptionally(throwable -> {
                                System.out.println("Node " + raftNodeState.getNodeId() + " confirmLeadership to " + peerUrl + " failed: " + throwable.getMessage());
                                return new HeartbeatResponseDto(false, currentTerm);
                            })
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
                        System.out.println("Node " + raftNodeState.getNodeId() + " no longer LEADER or term changed, ignoring confirmation");
                        return;
                    }
                    if (response != null && response.isSuccess() && response.getTerm() == currentTerm) {
                        System.out.println("Node " + raftNodeState.getNodeId() + " received confirmation from peer");
                        latch.countDown();
                    }
                }
            });
        }

        try {
            latch.await(raftConfig.getElectionRpcTimeoutMillis() * 2L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            System.out.println("Node " + raftNodeState.getNodeId() + " interrupted during leadership confirmation");
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while confirming leadership.");
        }

        if (latch.getCount() > 0) {
            System.out.println("Node " + raftNodeState.getNodeId() + " leadership confirmation failed");
            throw new IllegalStateException("Leadership confirmation failed.");
        }
        System.out.println("Node " + raftNodeState.getNodeId() + " leadership confirmed successfully");
    }

    private HeartbeatResponseDto requestLeadershipConfirmation(int leaderId, int term, String peerUrl) {
        try {
            String url = peerUrl + "/raft/confirmLeadership";
            ConfirmLeadershipRequestDto dto = new ConfirmLeadershipRequestDto(leaderId, term);
            ResponseEntity<HeartbeatResponseDto> response = restTemplate.postForEntity(url, dto, HeartbeatResponseDto.class);
            HeartbeatResponseDto body = response.getBody() != null ? response.getBody() : new HeartbeatResponseDto(false, term);
            System.out.println("Node " + leaderId + " received confirmLeadership response from " + peerUrl + ": success=" + body.isSuccess());
            if (body.getTerm() > raftNodeState.getCurrentTerm()) {
                System.out.println("Node " + leaderId + " found higher term " + body.getTerm() + ", becoming FOLLOWER");
                raftNodeStateManager.becomeFollower(body.getTerm());
            }
            return body;
        } catch (Exception e) {
            System.out.println("Node " + leaderId + " confirmLeadership to " + peerUrl + " failed: " + e.getMessage());
            return new HeartbeatResponseDto(false, term);
        }
    }

    public HeartbeatResponseDto handleConfirmLeadership(ConfirmLeadershipRequestDto request) {
        if (request.getTerm() > raftNodeState.getCurrentTerm()) {
            System.out.println("Node " + raftNodeState.getNodeId() + " received confirmLeadership with higher term " + request.getTerm() + ", becoming FOLLOWER");
            raftNodeStateManager.becomeFollower(request.getTerm());
            return new HeartbeatResponseDto(false, raftNodeState.getCurrentTerm());
        }
        if (raftNodeState.getCurrentRole() != Role.FOLLOWER) {
            System.out.println("Node " + raftNodeState.getNodeId() + " not FOLLOWER, rejecting confirmLeadership");
            return new HeartbeatResponseDto(false, raftNodeState.getCurrentTerm());
        }
        boolean success = request.getTerm() == raftNodeState.getCurrentTerm() &&
                (raftNodeState.getCurrentLeader() == null ||
                        raftNodeState.getCurrentLeader().equals(request.getNodeId()));
        System.out.println("Node " + raftNodeState.getNodeId() + " handled confirmLeadership: success=" + success);
        return new HeartbeatResponseDto(success, raftNodeState.getCurrentTerm());
    }
}