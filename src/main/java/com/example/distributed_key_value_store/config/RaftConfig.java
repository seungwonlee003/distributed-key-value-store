package com.example.distributed_key_value_store.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Getter
@Setter
@EnableAutoConfiguration
@ConfigurationProperties(prefix = "raft")
@Component
public class RaftConfig {
    private Integer nodeId;
    private Map<Integer, String> peerUrls;
    private int electionTimeoutMillisMin;      // 4000
    private int electionTimeoutMillisMax;      // 6000
    private int heartbeatIntervalMillis;       // 1000
    private int electionRpcTimeoutMillis;      // 2000
    private int clientRequestTimeoutMillis;    // 2000

}