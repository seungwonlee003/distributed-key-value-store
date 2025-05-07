package com.example.distributed_key_value_store.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class LockManager {
    private final ReadWriteLock logLock = new ReentrantReadWriteLock();
    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final ReadWriteLock stateMachineLock = new ReentrantReadWriteLock();

    public Lock getLogReadLock() {
        return logLock.readLock();
    }

    public Lock getLogWriteLock() {
        return logLock.writeLock();
    }

    public Lock getStateReadLock() {
        return stateLock.readLock();
    }

    public Lock getStateWriteLock() {
        return stateLock.writeLock();
    }

    public Lock getStateMachineReadLock() {
        return stateMachineLock.readLock();
    }

    public Lock getStateMachineWriteLock() {
        return stateMachineLock.writeLock();
    }
}
