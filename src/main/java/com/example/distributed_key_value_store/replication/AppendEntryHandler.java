package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.dto.AppendEntryRequestDto;
import com.example.distributed_key_value_store.dto.AppendEntryResponseDto;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import com.example.distributed_key_value_store.storage.StateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AppendEntryHandler {
    private final RaftLog log;
    @Autowired
    @Lazy
    private RaftNodeStateManager stateManager;
    private final RaftNodeState nodeState;
    private final StateMachine stateMachine;

    public synchronized AppendEntryResponseDto handle(AppendEntryRequestDto dto) {
        int term = nodeState.getCurrentTerm();
        int leaderTerm = dto.getTerm();

        // Reply false if term < currentTerm (§5.1)
        if (leaderTerm < term) return new AppendEntryResponseDto(term, false);

        // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower (§5.1)
        if (leaderTerm > term) {
            stateManager.becomeFollower(leaderTerm);
            term = leaderTerm;
        }

        nodeState.setCurrentLeader(dto.getLeaderId());

        // Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm (§5.3)
        if (dto.getPrevLogIndex() > 0 &&
                (!log.containsEntryAt(dto.getPrevLogIndex()) ||
                        log.getTermAt(dto.getPrevLogIndex()) != dto.getPrevLogTerm())) {
            return new AppendEntryResponseDto(term, false);
        }

        // If an existing entry conflicts with a new one (same index but different terms), delete the existing
        // entry and all that follow it (§5.3). Append any new entries not already in the log.
        int index = dto.getPrevLogIndex() + 1;
        List<LogEntry> entries = dto.getEntries();
        if (!entries.isEmpty()) {
            for (int i = 0; i < entries.size(); i++) {
                int logIndex = index + i;
                if (log.containsEntryAt(logIndex) && log.getTermAt(logIndex) != entries.get(i).getTerm()) {
                    log.deleteFrom(logIndex);
                    log.appendAll(entries.subList(i, entries.size()));
                    break;
                }
            }
            if (!log.containsEntryAt(index)) {
                log.appendAll(entries);
            }
        }

        // If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of the last new entry)
        if (dto.getLeaderCommit() > log.getCommitIndex()) {
            int lastNew = dto.getPrevLogIndex() + entries.size();
            log.setCommitIndex(Math.min(dto.getLeaderCommit(), lastNew));
            applyEntries();
        }

        stateManager.resetElectionTimer();
        return new AppendEntryResponseDto(term, true);
    }

    // If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (§5.3)
    private void applyEntries() {
        int commit = log.getCommitIndex();
        int lastApplied = nodeState.getLastApplied();
        for (int i = lastApplied + 1; i <= commit; i++) {
            try {
                LogEntry entry = log.getEntryAt(i);
                stateMachine.apply(entry);
                nodeState.setLastApplied(i);
            } catch (Exception e) {
                System.out.println("Failed to apply entry " + i);
                System.exit(1);
            }
        }
    }
}
