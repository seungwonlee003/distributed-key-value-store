package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.dto.AppendEntriesRequestDto;
import com.example.distributed_key_value_store.dto.AppendEntriesResponseDto;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import com.example.distributed_key_value_store.storage.StateMachine;
import com.example.distributed_key_value_store.util.LockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppendEntriesHandler {
    @Autowired
    @Lazy
    private RaftNodeStateManager stateManager;
    private final RaftLog raftLog;
    private final RaftNodeState nodeState;
    private final StateMachine stateMachine;
    private final LockManager lockManager;

    public AppendEntriesResponseDto handle(AppendEntriesRequestDto dto) {
        lockManager.getLogWriteLock().lock();
        lockManager.getStateWriteLock().lock();
        lockManager.getStateMachineWriteLock().lock();
        try {
            int term = nodeState.getCurrentTerm();
            int leaderTerm = dto.getTerm();

            // Reply false if term < currentTerm (§5.1)
            if (leaderTerm < term) return new AppendEntriesResponseDto(term, false);

            // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower (§5.1)
            if (leaderTerm > term) {
                stateManager.becomeFollower(leaderTerm);
                term = leaderTerm;
            }

            nodeState.setCurrentLeader(dto.getLeaderId());

            // Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm (§5.3)
            if (dto.getPrevLogIndex() > 0 &&
                    (!raftLog.containsEntryAt(dto.getPrevLogIndex()) ||
                            raftLog.getTermAt(dto.getPrevLogIndex()) != dto.getPrevLogTerm())) {
                log.warn("Node {}: log inconsistency detected at prevLogIndex {} (expected term {}, found term {})",
                        nodeState.getNodeId(), dto.getPrevLogIndex(), dto.getPrevLogTerm(),
                        raftLog.containsEntryAt(dto.getPrevLogIndex()) ? raftLog.getTermAt(dto.getPrevLogIndex()) : -1);
                stateManager.resetElectionTimer();
                return new AppendEntriesResponseDto(term, false);
            }

            // If an existing entry conflicts with a new one (same index but different terms), delete the existing
            // entry and all that follow it (§5.3). Append any new entries not already in the log.
            int index = dto.getPrevLogIndex() + 1;
            List<LogEntry> entries = dto.getEntries();
            if (!entries.isEmpty()) {
                for (int i = 0; i < entries.size(); i++) {
                    int logIndex = index + i;
                    if (raftLog.containsEntryAt(logIndex) && raftLog.getTermAt(logIndex) != entries.get(i).getTerm()) {
                        log.info("Node {}: conflicting entry at index {}, deleting from here and appending new entries",
                                nodeState.getNodeId(), logIndex);
                        raftLog.deleteFrom(logIndex);
                        raftLog.appendAll(entries.subList(i, entries.size()));
                        break;
                    }
                }
                if (!raftLog.containsEntryAt(index)) {
                    log.info("Node {}: appending {} new entries starting at index {}",
                            nodeState.getNodeId(), entries.size(), index);
                    raftLog.appendAll(entries);
                }
            }

            // If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of the last new entry)
            if (dto.getLeaderCommit() > raftLog.getCommitIndex()) {
                int lastNew = dto.getPrevLogIndex() + entries.size();
                raftLog.setCommitIndex(Math.min(dto.getLeaderCommit(), lastNew));
                applyEntries();
            }
            stateManager.resetElectionTimer();
            log.info("Node {}: successfully received {} from leader {}",
                    nodeState.getNodeId(),
                    entries.isEmpty() ? "no-op heartbeat" : "heartbeat with " + entries.size() + " entries",
                    dto.getLeaderId());
            return new AppendEntriesResponseDto(term, true);
        } finally {
            lockManager.getStateMachineWriteLock().unlock();
            lockManager.getStateWriteLock().unlock();
            lockManager.getLogWriteLock().unlock();
        }
    }

    // If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (§5.3)
    private void applyEntries() {
        int commit = raftLog.getCommitIndex();
        int lastApplied = nodeState.getLastApplied();
        for (int i = lastApplied + 1; i <= commit; i++) {
            try {
                LogEntry entry = raftLog.getEntryAt(i);
                stateMachine.apply(entry);
                nodeState.setLastApplied(i);
            } catch (Exception e) {
                System.out.println("Failed to apply entry " + i);
                System.exit(1);
            }
        }
    }
}
