import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class ElectionManager {
    private final RaftNode raftNode;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Random random = new Random();
    private final int electionTimeoutMin = 150;
    private final int electionTimeoutMax = 300;
    private ScheduledFuture<?> electionFuture;

    public ElectionManager(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    public synchronized VoteResponseDTO handleVoteRequest(RequestVoteDTO requestVote) {
        RaftNodeState state = raftNode.getState();
        int requestTerm = requestVote.getTerm();
        int candidateId = requestVote.getCandidateId();
        int candidateLastTerm = requestVote.getLastLogTerm();
        int candidateLastIndex = requestVote.getLastLogIndex();

        int currentTerm = state.getCurrentTerm();
        Integer votedFor = state.getVotedFor();

        if (requestTerm < currentTerm) {
            return new VoteResponseDTO(currentTerm, false);
        }

        if (requestTerm > currentTerm) {
            state.setCurrentTerm(requestTerm);
            state.setRole(Role.FOLLOWER);
            state.setVotedFor(null);
            raftNode.getRaftLogManager().stopHeartbeats(); // Delegate to RaftLogManager if needed
            resetElectionTimer();
            currentTerm = requestTerm;
        }

        if (votedFor != null && !votedFor.equals(candidateId)) {
            return new VoteResponseDTO(currentTerm, false);
        }
        int localLastTerm = state.getLastLogTerm();
        int localLastIndex = state.getLastLogIndex();
        if (candidateLastTerm < localLastTerm || 
            (candidateLastTerm == localLastTerm && candidateLastIndex < localLastIndex)) {
            return new VoteResponseDTO(currentTerm, false);
        }

        state.setVotedFor(candidateId);
        resetElectionTimer();
        return new VoteResponseDTO(currentTerm, true);
    }

    public void resetElectionTimer() {
        cancelElectionTimerIfRunning();
        int timeout = electionTimeoutMin + random.nextInt(electionTimeoutMax - electionTimeoutMin);
        electionFuture = scheduler.schedule(this::startElection, timeout, TimeUnit.MILLISECONDS);
    }

    private void cancelElectionTimerIfRunning() {
        if (electionFuture != null && !electionFuture.isDone()) {
            electionFuture.cancel(false);
        }
    }

    private void startElection() {
        synchronized (this) {
            RaftNodeState state = raftNode.getState();
            if (state.getRole() == Role.LEADER) return;

            state.setRole(Role.CANDIDATE);
            state.incrementTerm();
            state.setVotedFor(state.getNodeId());

            int currentTerm = state.getCurrentTerm();
            List<CompletableFuture<VoteResponseDTO>> voteFutures = new ArrayList<>();
            ExecutorService executor = raftNode.getAsyncExecutor();

            for (String peerUrl : raftNode.getPeerUrls()) {
                CompletableFuture<VoteResponseDTO> voteFuture = CompletableFuture
                    .supplyAsync(() -> requestVote(currentTerm, state.getNodeId(), 
                                                   state.getLastLogIndex(), state.getLastLogTerm(), peerUrl), executor)
                    .orTimeout(1000, TimeUnit.MILLISECONDS)
                    .exceptionally(throwable -> new VoteResponseDTO(currentTerm, false));
                voteFutures.add(voteFuture);
            }

            CompletableFuture.allOf(voteFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
                synchronized (this) {
                    if (state.getRole() != Role.CANDIDATE || state.getCurrentTerm() != currentTerm) {
                        return;
                    }
                    int voteCount = 1; // Self-vote
                    for (CompletableFuture<VoteResponseDTO> future : voteFutures) {
                        try {
                            VoteResponseDTO response = future.get();
                            if (response != null && response.isVoteGranted()) {
                                voteCount++;
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    int majority = (raftNode.getPeerUrls().size() + 1) / 2 + 1;
                    if (voteCount >= majority) {
                        raftNode.becomeLeader(); // Delegate back to RaftNode
                    } else {
                        resetElectionTimer();
                    }
                }
            }).exceptionally(ex -> {
                System.err.println("Election failed: " + ex.getMessage());
                return null;
            });
        }
    }

    private VoteResponseDTO requestVote(int term, int candidateId, int lastLogIndex, int lastLogTerm, String peerUrl) {
        try {
            String url = peerUrl + "/raft/requestVote";
            RequestVoteDTO dto = new RequestVoteDTO(term, candidateId, lastLogIndex, lastLogTerm);
            ResponseEntity<VoteResponseDTO> response = raftNode.getRestTemplate().postForEntity(url, dto, VoteResponseDTO.class);
            VoteResponseDTO body = response.getBody() != null ? response.getBody() : new VoteResponseDTO(term, false);

            synchronized (this) {
                RaftNodeState state = raftNode.getState();
                if (body.getTerm() > state.getCurrentTerm()) {
                    state.setCurrentTerm(body.getTerm());
                    state.setRole(Role.FOLLOWER);
                    state.setVotedFor(null);
                    raftNode.getRaftLogManager().stopHeartbeats(); // Ensure consistency
                    resetElectionTimer();
                }
            }
            return body;
        } catch (Exception e) {
            return new VoteResponseDTO(term, false);
        }
    }
}
