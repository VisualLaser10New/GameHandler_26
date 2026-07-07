# User uniqueness contract (C-R6)

This note documents the central system's global-username-uniqueness contract and the
first-registration-wins semantics implemented by `UserService.registerFromSync`. It is the
"doc" half of plan §C-R6 (see
[`workflow/analisi/race_condition_analisys_central_local.md`](race_condition_analisys_central_local.md)
§C-R6). The louder-log half is already implemented in
`central-system/.../application/service/UserService.java` (`registerFromSync`).

## The contract

`username` (and `email`) are **globally unique across all buildings** in the central
database. This is enforced at the schema level by the `UNIQUE KEY uk_users_username`
and `UNIQUE KEY uk_users_email` constraints on the central `users` table (see
`infrastructure/mysql-central/init.sql:19-20`).

There is exactly **one central row per username**, regardless of how many buildings a
player registered from. The central `AuthService` authenticates against this single
global user view.

## First-registration-wins semantics

When two buildings register the same username concurrently (or a re-registration reaches
the central system after the first one has already committed), the central system
resolves the collision as follows:

1. `UserService.registerFromSync` first performs idempotency pre-checks by `userId`,
   `username`, and `email`; if any already exists it logs at `INFO` and returns (no-op).
2. If the pre-checks pass but a concurrent insert trips the unique constraint, the
   `DataIntegrityViolationException` thrown by `userRepository.save(user)` is caught and
   the method **keeps the existing user's password hash** — the losing insert is discarded.
   This is an **idempotent no-op**: the losing building's `registerFromSync` call does not
   throw and does not overwrite the winner's `password_hash`, `email`, or `roles`.
3. The collision is logged at **WARN** with a structured message identifying the
   `userId`, `username`, and `buildingId`, and stating that the existing password is kept
   and the losing building remains locally consistent (see `UserService.java`
   `registerFromSync`, the `catch (DataIntegrityViolationException e)` block).

The first registration to commit wins the password hash; all later registrations for the
same username are absorbed as no-ops.

## Per-building password divergence is EXPECTED

Because the central system keeps the **first** password hash and never overwrites it on a
collision, **per-building password divergence is expected** when two buildings register
the same username with different passwords:

- The central `users.password_hash` is whichever building registered first.
- Each building's **local** `users` table keeps the password that building accepted at
  registration time.
- Each building therefore remains **locally consistent with its own view** of the user's
  credentials, even though the central view may differ.

This is an accepted consequence of the global-username + first-registration-wins
contract. Operators should read the WARN log to detect when a collision occurred.

## Future work (NOT currently implemented)

Plan §C-R6 Option 2 — a per-building password store via a new `building_users` table
that would hold one `(buildingId, username, password_hash)` row per building — is a
**major refactor** that breaks the global-user assumption the central `AuthService`
relies on (`AuthService` authenticates a single central `users` row per username). It is
listed here only as a **tracked future option**, not active work. It is NOT recommended
by the plan and is out of scope for C-R6 as implemented.

## Cross-reference

- Plan: §C-R6 in `workflow/analisi/race_condition_analisys_central_local.md`.
- Schema: `infrastructure/mysql-central/init.sql:19-20` (`uk_users_username`,
  `uk_users_email`).
- Implementation: `central-system/.../application/service/UserService.java`
  (`registerFromSync`, first-registration-wins `DataIntegrityViolationException` catch).
