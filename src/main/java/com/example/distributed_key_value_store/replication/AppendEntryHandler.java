package com.example.distributed_key_value_store.replication;

import com.example.distributed_key_value_store.dto.AppendEntryRequestDto;
import com.example.distributed_key_value_store.dto.AppendEntryResponseDto;
import com.example.distributed_key_value_store.log.LogEntry;
import com.example.distributed_key_value_store.log.RaftLog;
import com.example.distributed_key_value_store.node.RaftNodeState;
import com.example.distributed_key_value_store.node.RaftNodeStateManager;
import com.example.distributed_key_value_store.storage.StateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class AppendEntryHandler {
    private final RaftLog log;
    private final RaftNodeStateManager stateManager;
    private final RaftNodeState nodeState;
    private final StateMachine stateMachine;
    private final ExecutorService applyExecutor = Executors.newSingleThreadExecutor();

    public synchronized AppendEntryResponseDto handle(AppendEntryRequestDto dto) {
        int term = nodeState.getCurrentTerm();
        int leaderTerm = dto.getTerm();

        if (leaderTerm < term) return new AppendEntryResponseDto(term, false);
        if (leaderTerm > term) {
            stateManager.becomeFollower(leaderTerm);
            term = leaderTerm;
        }

        nodeState.setCurrentLeader(dto.getLeaderId());

        if (dto.getPrevLogIndex() > 0 &&
                (!log.containsEntryAt(dto.getPrevLogIndex()) ||
                        log.getTermAt(dto.getPrevLogIndex()) != dto.getPrevLogTerm())) {
            return new AppendEntryResponseDto(term, false);
        }

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

        if (dto.getLeaderCommit() > log.getCommitIndex()) {
            int lastNew = dto.getPrevLogIndex() + entries.size();
            log.setCommitIndex(Math.min(dto.getLeaderCommit(), lastNew));
            applyExecutor.submit(() -> applyEntries());
        }

        stateManager.resetElectionTimer();
        return new AppendEntryResponseDto(term, true);
    }

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
