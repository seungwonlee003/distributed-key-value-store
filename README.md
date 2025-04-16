# Raft-Based Distributed Key-Value Store:
This is a minimal distributed key-value store implemented using the Raft consensus algorithm.

The store is built in Java using Spring. Persistent states and logs are stored on disk using append-only, file-backed logs, while the state machine is persisted with an embedded H2 database. Internal RPCs and client interactions are handled via RESTful HTTP APIs.

Refer to the two articles for more details on the project.

Article 1:

Article 2:
