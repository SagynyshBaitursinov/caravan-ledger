# caravan-ledger

Event-sourced double-entry ledger built on [events-caravan](https://github.com/SagynyshBaitursinov/events-caravan). 

Following the principles of the framework, the architectural decisions are made to prioritize scalability and availability,
to tolerate eventual consistency, make the compromise visible to the business and prevent any negative consequences.

| Module                                  | Purpose                                                        |
|-----------------------------------------|----------------------------------------------------------------|
| caravan-ledger                          | Ledger domain (entities, events, handlers)                     |
| caravan-ledger-integration-tests        | End-to-end tests with the events-caravan starters on local AWS |