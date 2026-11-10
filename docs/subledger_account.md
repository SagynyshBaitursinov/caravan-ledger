# Sub-ledger Account

`Account` is an event-sourced entity.

## Events

```
opened ─► balance-reserved ─► transaction-settled
      │                   └─► transaction-rolled-back
      └─► transaction-settled (credit)
```

- `opened`: fixes `currency` and possibility of overdrafts (`overdraftAllowed`).
- `balance-reserved`: reserves the amount of one `DEBIT` `TransactionLeg`.
- `transaction-settled`: records `SettlementDetails` of one `TransactionLeg` into `balance`, releasing the reservation
  of a `DEBIT`.
- `transaction-rolled-back`: rolls back one `DEBIT` `TransactionLeg`, releasing its reservation if any.

## Balances

- `balance`: sum of settled legs.
- `reservedBalance`: sum of reserved `DEBIT` amounts.
- `availableBalance = balance − reservedBalance`.
- Reserved funds are not spendable to prevent double spending in case of transaction abortion.

## Rules

- One settlement per `Transaction` per `Account`.
- `reserve` and `validateCredit` reject a leg in another currency (`CurrencyMismatchException`).
- `reserve` rejects a `DEBIT` above `availableBalance` unless `overdraftAllowed` (`InsufficientFundsException`).
- `reserve` accepts a `DEBIT` only.
- `validateCredit` accepts a `CREDIT` only. It validates the leg, no event is recorded.
- `rollback` accepts a `DEBIT` only.
- `settle` of a `DEBIT` requires a reservation.
- `settle` and `rollback` are mutually exclusive per `Transaction`.

## Guarantees

Commands arrive at least once, in any order.

- One `Account` cannot be modified in parallel due to the framework constraints. No lost updates are possible.
- Changes happening to single Account and events published on those are single unit of consistency.
- `reserve`, `settle`, `rollback` are idempotent per `TransactionId`.
- `balanceReservations`, `settledTransactions` and `rolledBackTransactions` are the dedup markers.
- `rollback` is accepted before `reserve`. The marker makes a late `reserve` a no-op, so funds of an aborted
  `Transaction` are never reserved.

## Boundaries

- `Account` knows only its own leg. It never reads other accounts.
- `Account` does not decide the outcome of a `Transaction`. It only accepts or rejects its leg.
- `Account` entities can be stored in different data stores enabling horizontal scaling as per events-caravan framework.