package com.example.distributed_key_value_store.election;

import com.example.distributed_key_value_store.config.RaftConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ElectionTimer {
    private final RaftConfig config;
    private final ElectionManager electionManager;
    private ScheduledFuture<?> electionFuture;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    // If election timeout elapses without receiving AppendEntries RPC from current leader or granting vote to candidate:
    // convert to candidate (§5.2)
    public synchronized void reset(){
        cancel();

        long minTimeout = config.getElectionTimeoutMillisMin();
        long maxTimeout = config.getElectionTimeoutMillisMax();
        long timeout = minTimeout + random.nextInt((int)(maxTimeout - minTimeout));

        electionFuture = scheduler.schedule(() -> {
            electionManager.startElection();
        }, timeout, TimeUnit.MILLISECONDS);
    }

    public synchronized void cancel(){
        if (electionFuture != null && !electionFuture.isDone()) {
            electionFuture.cancel(false);
            electionFuture = null;
        }
    }
}
