package com.example.distributed_key_value_store.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "raft")
public class RaftConfig {
    private Integer nodeId;
    private Map<Integer, String> peerUrls;
    private int electionTimeoutMillisMin;
    private int electionTimeoutMillisMax;
    private int heartbeatIntervalMillis;
    private int electionRpcTimeoutMillis;
    private int clientRequestTimeoutMillis;
}
