## Introduction
Demo video.

This blog post walks through the code.

This is a distributed key-value store implemented using the Raft consensus algorithm and custom LSM storage engine. 
It offers a simple key-value lookup endpoints and it is designed to be fault-tolerant (as long as majority of nodes are alive)
and linearizable. It is implemented in Java using Spring Boot with Lombok and SLF4J dependencies.

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
