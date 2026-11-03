# Sub-ledger Account

`Account` is an event-sourced entity.

## Events

```
opened ─► entry-held ─► entry-settled
                    └─► entry-rolled-back
```

- `opened`: fixes `currency` and possibility of overdrafts (`overdraftAllowed`).
- `entry-held`: records a `LedgerEntry` for one `TransactionLeg`.
- `entry-settled`: moves the held `LedgerEntry` into `balance`.
- `entry-rolled-back`: releases the held `LedgerEntry`.

## Balances

- `balance`: sum of settled entries.
- `heldDebits`: sum of held `DEBIT` entries. Held `CREDIT` entries are not counted.
- `availableBalance = balance − heldDebits`.
- Held funds are not spendable to prevent double spending in case of transaction abortion.

## Rules

- One `LedgerEntry` per `Transaction` per `Account`.
- `hold` rejects a leg in another currency (`CurrencyMismatchException`).
- `hold` rejects a `DEBIT` above `availableBalance` unless `overdraftAllowed` (`InsufficientFundsException`).
- `settle` requires a held entry.
- `settle` and `rollback` are mutually exclusive per `Transaction`.

## Guarantees

Commands arrive at least once, in any order.

- One `Account` cannot be modified in parallel due to the framework constraints. No lost updates are possible.
- Changes happening to single Account and events published on those are single unit of consistency.
- `hold`, `settle`, `rollback` are idempotent per `TransactionId`.
- `settledTransactions` and `rolledBackTransactions` are the dedup markers.
- `rollback` is accepted before `hold`. The marker makes a late `hold` a no-op, so funds of an aborted `Transaction` are never held.

## Boundaries

- `Account` knows only its own leg. It never reads other accounts.
- `Account` does not decide the outcome of a `Transaction`. It only accepts or rejects its leg.
- `Account` entities can be stored in different data stores enabling horizontal scaling as per events-caravan framework.