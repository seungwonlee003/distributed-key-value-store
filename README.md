## Introduction

[Demo Video](https://www.youtube.com/watch?v=gdX7VxVnL6U)

[Blog Post](https://dev.to/sashaonion/javaraft-raft-based-distributed-key-value-store-5h0a)

This is a distributed key-value store prototype powered by the **Raft consensus algorithm**.  

The project is implemented in Java, using Spring Boot, with Lombok and SLF4J as dependencies.

An embedded database such as LevelDB can be plugged in by implementing the provided state machine and log interfaces — enabling full durability and persistent storage.


## Features
- Leader election (§5.2)
- Log replication (§5.3)
- Election restriction (§5.4.1)
- Committing entries from previous terms (§5.4.2)
- Follower and candidate crashes (§5.5)
- Implementing linearizable semantics (§8)

## Usage
Try out the distributed key-value API.

Configure application properties for nodes and build the JAR (skipping tests):
```
mvn clean package -DskipTests
```

### Terminal 1
```
java -jar target/distributed_key_value_store-0.0.1-SNAPSHOT.jar --spring.profiles.active=node1
```

### Terminal 2
```
java -jar target/distributed_key_value_store-0.0.1-SNAPSHOT.jar --spring.profiles.active=node2
```

### Terminal 3
```
java -jar target/distributed_key_value_store-0.0.1-SNAPSHOT.jar --spring.profiles.active=node3
```

## Endpoints
All operations must be sent to the leader node. Redirection is not implemented, and follower reads/writes are blocked.

### Get operation:
```
curl -X GET "http://localhost:9090/raft/client/get?key=myKey"
```

### Put operation:
```
curl -X POST "http://localhost:9090/raft/client/insert" \
     -H "Content-Type: application/json" \
     -d '{"clientId": "client1", "sequenceNumber": 1, "key": "myKey", "value": "myValue"}'
```

### Delete operation:
```
curl -X POST "http://localhost:9090/raft/client/delete" \
     -H "Content-Type: application/json" \
     -d '{"clientId": "client1", "sequenceNumber": 1, "key": "myKey"}'
```
