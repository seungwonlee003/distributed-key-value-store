package com.example.distributed_key_value_store.log;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InMemoryRaftLog implements RaftLog{
    private final List<LogEntry> logEntries = new ArrayList<>();
    private int commitIndex = 0;

    public InMemoryRaftLog() {
        LogEntry dummy = new LogEntry(0, "__dummy__", null, LogEntry.Operation.DELETE, "dummy", 0);
        logEntries.add(dummy);
    }

    @Override
    public void append(LogEntry entry) {
        logEntries.add(entry);
    }

    @Override
    public void appendAll(List<LogEntry> entries) {
        if (entries != null && !entries.isEmpty()) {
            logEntries.addAll(entries);
        }
    }

    @Override
    public boolean containsEntryAt(int index) {
        return index >= 1 && index < logEntries.size();
    }

    @Override
    public int getTermAt(int index) {
        LogEntry entry = getEntryAt(index);
        return entry != null ? entry.getTerm() : -1;
    }

    @Override
    public void deleteFrom(int fromIndex) {
        if (fromIndex >= 1 && fromIndex < logEntries.size()) {
            logEntries.subList(fromIndex, logEntries.size()).clear();
        }
    }

    @Override
    public int getLastIndex() {
        return logEntries.size() - 1;
    }

    @Override
    public int getLastTerm() {
        return logEntries.isEmpty() ? 0 : logEntries.get(logEntries.size() - 1).getTerm();
    }

    @Override
    public int getCommitIndex() {
        return commitIndex;
    }

    @Override
    public void setCommitIndex(int newCommitIndex) {
        if (newCommitIndex > commitIndex && newCommitIndex <= getLastIndex()) {
            commitIndex = newCommitIndex;
        }
    }

    @Override
    public LogEntry getEntryAt(int index) {
        return (index >= 0 && index < logEntries.size()) ? logEntries.get(index) : null;
    }

    @Override
    public List<LogEntry> getEntriesFrom(int startIndex) {
        List<LogEntry> entries = new ArrayList<>();
        for (int i = startIndex; i < logEntries.size(); i++) {
            entries.add(logEntries.get(i));
        }
        return entries;
    }
}
