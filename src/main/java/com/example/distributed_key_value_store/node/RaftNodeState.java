package com.example.distributed_key_value_store.node;

public interface RaftNodeState {
    int getNodeId();
    void setNodeId(int nodeId);
    int getCurrentTerm();
    void setCurrentTerm(int currentTerm);
    void incrementTerm();
    Integer getVotedFor();
    void setVotedFor(Integer votedFor);
    int getLastApplied();
    void setLastApplied(int lastApplied);
    Role getCurrentRole();
    void setCurrentRole(Role role);
    Integer getCurrentLeader();
    void setCurrentLeader(Integer leaderId);
    String getCurrentLeaderUrl();
    boolean isLeader();
}
