# Transaction Saga

`Transaction` is an event-sourced entity and a choreography saga over sub-ledger `Account`s.
No orchestrator, no lock, no write spanning several entities.

## Events

```
started ─► completed
        └─► aborted
```

- `started`: carries the `TransactionDefinition` (`startTimestamp`, `legs`).
- `completed`: every leg is successfully held on corresponding accounts.
- `aborted`: some legs could not be held. Carries the `reason`.
- `completed` and `aborted` are terminal and mutually exclusive. Optimistic concurrency on the entity lets exactly one win.

## Definition

- A `Transaction` is a set of `TransactionLeg`s: `accountId`, `type` (`DEBIT` / `CREDIT`), `amount`.
- Validated at `start`, before any event:
  - at least one leg;
  - one leg per `Account`;
  - one currency;
  - credits and debits sum up to zero.
- Legs are ordered deterministically debits first.

## Flow

| Event       | Handler                        | Step                                                                    |
|-------------|--------------------------------|-------------------------------------------------------------------------|
| `started`   | `TransactionStartedHandler`    | Clearing: `hold` every leg, then `complete`. Any rejection → `abort`.   |
| `completed` | `TransactionCompletedHandler`  | Settlement: `settle` every leg.                                         |
| `aborted`   | `TransactionAbortedHandler`    | Clearing: `rollback` every leg, held or not.                            |

- Each step writes to one entity at a time.
- Each step reloads the `Transaction` and acts on its current state, not on the event payload.

## Guarantees

- Events are delivered at least once, in any order.
- Every handler is idempotent and safe to retry.
- When a late `hold` is requested on an `Account`, and if `Account` is aware that transaction is rolled back, `hold` won't succeed.
- Between `started` and `settle` of the last leg, funds are visible as held, never disappeared from DB.
