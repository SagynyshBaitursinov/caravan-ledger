# caravan-ledger

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**Demo:** Event-sourced double-entry ledger built on [events-caravan](https://github.com/SagynyshBaitursinov/events-caravan). 

The caravan-ledger architecture is designed to scale horizontally with the number of accounts and transactions between them.
Following the principles of the framework, design decisions are made to prioritize partition tolerance and high availability,
while acknowledging eventual consistency as a compromise. The business processes must be built with this fact in mind.

## Scalability

- Every write into DB touches one entity only at a time: one `Account` or one `Transaction`. No locks, no DB transaction spanning several entities.
- A `Transaction` is a choreography saga over `Account`s ([docs/transaction_saga.md](docs/transaction_saga.md)).
- Different `Account`s and `Transaction`s can be stored in different data stores. Throughput grows with number of nodes in the cluster.
- The price is eventual consistency: between `started` and the last `settle`, funds are held, not spendable and not lost.
- The demo leaves out General Ledger, "hot" Account handling and Account snapshotting, while focusing on Transaction saga and entities' design around events-caravan.

| Module                                  | Purpose                                                        |
|-----------------------------------------|----------------------------------------------------------------|
| caravan-ledger                          | Ledger domain (entities, events, handlers)                     |
| caravan-ledger-integration-tests        | End-to-end tests with the events-caravan starters on local AWS |

## License

[Apache License 2.0](LICENSE)
