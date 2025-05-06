## Introduction 
This is a distributed key-value store implemented using the Raft consensus algorithm and custom LSM storage engine. 
It offers a simple key-value lookup endpoints and it is designed to be fault-tolerant (as long as majority of nodes are alive)
and linearizable. It is implemented in Java using Spring Boot for the ease of RPC communication, with Lombok and Log4j dependencies.

## Features
- Leader electioon (§5.2)
- Log replication (§5.3)
- Election restriction (§5.4.1)
- Committing entries from previous terms (§5.4.2)
- Follower and candidate crashes (§5.5)
- Implementing linearizable semantics (§8)

## Usage

## References

## Article
Refer to the article for more details on the project:
