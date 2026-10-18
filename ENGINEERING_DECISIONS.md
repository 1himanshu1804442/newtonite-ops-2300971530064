# Engineering Decisions & Architectural Trade-offs

**Project:** Newtonite Operations Under Pressure (`newtonite-ops-2300971530064`)  
**Author:** Candidate (Roll No: `2300971530064`)  
**Stack:** Java 17, Spring Boot 3, PostgreSQL 16, React 18, TypeScript, Tailwind CSS  

---

## Executive Summary
This document outlines five pivotal architectural decisions implemented in the **Newtonite Operations Under Pressure** platform. Each decision addresses concrete operational hazards described in the candidate brief—specifically concurrent collisions, stale edits, duplicated network requests, audit accountability, and distributed work processing.

---

## Decision 1: Concurrency Control & Atomic Ticket Claiming

### Context & Problem
Multiple operations agents work concurrently on unassigned urgent queues. When an incident or high-priority payment investigation arrives, two or more engineers may click "Claim" at approximately the same instant. Under naive CRUD (`find -> if unassigned -> save`), both requests read the unassigned state, resulting in a race condition where the second write silently overwrites the first, causing duplicate work or confusion.

### Options Considered
1. **Application-level JVM Mutex / Synchronized Block:**
   - *Pros:* Simple to implement in code.
   - *Cons:* Fails completely when scaling beyond a single application node; introduces thread contention and performance bottlenecks.
2. **Pessimistic Locking (`SELECT FOR UPDATE`):**
   - *Pros:* Strict DB-level locking guarantees single-thread access.
   - *Cons:* Holds database row locks for the duration of the transaction, increasing database connection wait times and risking deadlocks under high load.
3. **Atomic Conditional Update (`UPDATE ... WHERE assignee_id IS NULL`):** *(Chosen)*
   - *Pros:* Leverages PostgreSQL row-level MVCC semantics. Executes in a single database roundtrip without long-held locks. If 0 rows are updated, the application immediately knows another user claimed the ticket.

### Decision & Implementation
We implement an atomic conditional update at the database repository level:
```sql
UPDATE work_items 
SET assignee_id = :userId, version = version + 1, updated_at = :now 
WHERE id = :id AND assignee_id IS NULL;
```
If the affected row count is `0`, the service throws a `WorkItemAlreadyClaimedException`, which the `@ControllerAdvice` global handler maps to HTTP `409 Conflict` with a structured payload:
```json
{
  "errorCode": "ITEM_ALREADY_CLAIMED",
  "message": "This work item has already been claimed by another team member.",
  "claimedBy": "Jane Doe",
  "claimedAt": "2026-10-18T14:10:00Z"
}
```

### Trade-offs
- **Trade-off:** First-to-commit wins; subsequent claimers receive a `409 Conflict`. The UI catches this conflict and presents an informative toast banner, refreshing the item state so the second engineer immediately sees who claimed it.

---

## Decision 2: Stale Edits Prevention via Optimistic Locking

### Context & Problem
An engineer opens an incident ticket to modify priority or notes while another engineer updates the status from another tab or workstation. If Engineer A submits changes based on outdated information, Engineer B's recent changes would be silently obliterated (the "Lost Update" problem).

### Options Considered
1. **Last-Write-Wins (LWW):**
   - *Cons:* Silently overwrites concurrent changes; unacceptable in compliance and incident management.
2. **Pessimistic Row Locking (`SELECT FOR UPDATE` during edits):**
   - *Cons:* Impossible across human "think time" (a user reading or drafting notes for 2 minutes cannot keep a DB row locked).
3. **Optimistic Concurrency Control (OCC) via Versioning:** *(Chosen)*
   - *Pros:* Non-blocking, scalable, zero lock overhead while users read or edit.

### Decision & Implementation
Every `WorkItem` entity includes a `@Version Long version` attribute. When the frontend requests an item, it receives the current `version`. On mutation (`PATCH /api/items/{id}`), the client must supply the version it read. If Hibernate detects that the database version has advanced beyond the client's expected version, an `OptimisticLockException` is thrown, returning HTTP `409 Conflict` containing the fresh state of the work item.

### Trade-offs
- **Trade-off:** The user must review conflicting changes if another user touched the record first. In high-stakes operations (e.g. compliance approval, payment freezes), preventing silent overwrites is strictly more critical than frictionless writing.

---

## Decision 3: Idempotency Protection for Non-Idempotent Operations

### Context & Problem
In high-pressure operational environments with flaky network connections or restless users, double-clicking "Create Incident" or browser retries can lead to duplicate tickets, duplicate payments investigations, or conflicting state mutations.

### Options Considered
1. **UI Button Disabling Only:**
   - *Cons:* Completely ineffective against network retries, script submissions, parallel browser tabs, or impatient page reloads.
2. **Database Unique Constraints on Business Keys:**
   - *Cons:* Work item titles and descriptions are not naturally unique; legitimate similar tickets would be erroneously rejected.
3. **Transactional Idempotency-Key Header & Cached Response:** *(Chosen)*
   - *Pros:* Standardized enterprise architecture (Stripe/IETF standard). Safe for retries.

### Decision & Implementation
Clients submit a unique `Idempotency-Key` (UUIDv4) header with mutating requests (`POST /api/items`).
A Spring HTTP Interceptor / Service checks the `idempotency_records` table:
- If key does not exist: Execute request inside transaction, record the resulting HTTP status and serialized response body along with the key.
- If key exists: Return the cached response body and status code immediately without re-executing business logic or creating duplicate records.

### Trade-offs
- **Trade-off:** Requires storage for idempotency keys (cleaned up periodically via TTL). We gain absolute protection against accidental double submissions.

---

## Decision 4: Workflow State Machine & Server-Enforced RBAC

### Context & Problem
Operational work involves strict compliance boundaries. For example, a customer issue or incident in `AWAITING_APPROVAL` must never be resolved by the same junior agent who created or worked on it without senior sign-off. UI-only button hiding is insecure and trivial to bypass via curl or browser developer tools.

### Options Considered
1. **Client-side permission checks with generic CRUD backend:**
   - *Cons:* Severe security risk. Any user can send `PATCH /api/items/{id} {"status": "CLOSED"}` directly.
2. **External Rules Engine (e.g. Drools, Camunda):**
   - *Cons:* Massive operational complexity, overkill for a one-day vertical slice assessment.
3. **Domain-Driven Server State Machine with Scoped Team Role Guards:** *(Chosen)*
   - *Pros:* Clear, testable, self-documenting code with 100% server enforcement.

### Decision & Implementation
The backend defines a strict transition graph:
- `OPEN` -> `IN_PROGRESS` (Authorized: Team `MEMBER`, `LEAD`, `ADMIN`)
- `IN_PROGRESS` -> `AWAITING_APPROVAL` (Authorized: Team `MEMBER`, `LEAD`, `ADMIN`)
- `AWAITING_APPROVAL` -> `RESOLVED` (**Authorized: Team `LEAD` or `ADMIN` ONLY**)
- `RESOLVED` -> `CLOSED` (**Authorized: Team `LEAD` or `ADMIN` ONLY**)
- All other jumps (e.g., `OPEN` -> `CLOSED`) are rejected with `400 Bad Request`.
- If a regular `MEMBER` attempts to transition to `RESOLVED`, the system returns `403 Forbidden` with the message: *"Approval requires Team Lead or Admin privilege."*

### Trade-offs
- **Trade-off:** Hardcoded state transitions are less dynamically configurable than a full rules engine, but offer compile-time type safety, zero external runtime dependencies, and instant readability.

---

## Decision 5: Transactional Outbox Pattern for Asynchronous Events

### Context & Problem
When a high-priority work item changes state or assignee, downstream actions (such as sending alerts, triggering webhooks, or syncing with third-party tracking) must occur. Performing secondary actions synchronously in the HTTP request thread introduces latency and causes distributed failure: if the notification service fails, should the ticket update roll back? If the database transaction fails to commit, the notification has already been fired erroneously (Phantom Notification).

### Options Considered
1. **Synchronous In-Thread Execution:**
   - *Cons:* Sluggish UI response times; failures in secondary services fail the primary user action.
2. **Direct Asynchronous Task (`@Async` / ThreadPool):**
   - *Cons:* In-memory message loss if the application crashes or restarts before the thread executes; race condition where the async task runs before the DB transaction commits.
3. **Transactional Outbox Table with Polling Worker:** *(Chosen)*
   - *Pros:* Guarantees **At-Least-Once Delivery**. The outbox record is inserted in the exact same ACID database transaction as the work item and activity log. If the DB rolls back, no phantom event is created.

### Decision & Implementation
We write an `OutboxJob` record in the primary transaction:
```sql
INSERT INTO outbox_jobs (id, event_type, payload, status, attempts, created_at)
VALUES (:id, 'WORK_ITEM_STATUS_CHANGED', :jsonPayload, 'PENDING', 0, NOW());
```
A lightweight `@Scheduled` worker continuously queries pending jobs using `SELECT ... FOR UPDATE SKIP LOCKED` (or status polling), processes them with idempotent handlers, and increments attempt counts with exponential backoff on failure (capped at 5 attempts before marking `FAILED`).

### Trade-offs
- **Trade-off:** Adds slight latency (polling interval) compared to immediate websockets, but eliminates distributed inconsistencies and data loss without requiring external queue infrastructure like Kafka or RabbitMQ.

---

## Intentionally Excluded Scope ("What We Chose Not to Build")
To deliver high engineering depth on correctness within the one-day timeframe:
1. **SSO / OAuth2 / Email Delivery:** Mocked with deterministic seeded personas (Viewer, Member, Lead, Admin) to allow immediate live evaluation of authorization boundaries.
2. **WebSockets:** Server-side cursor pagination and optimistic UI reconciliation were prioritized over live socket meshes to minimize complexity and maximize reliability.
3. **File Attachments:** Skipped to keep focus on state transitions, concurrency, and audit logs.

---

## Known Limitations
1. **Outbox Polling Delay:** Default polling interval is 5 seconds. In large scale deployments (>100k events/min), this should transition to PostgreSQL `LISTEN/NOTIFY` or an event bus like Kafka.
2. **In-Memory JWT vs Stateful Session:** Tokens are signed statelessly; immediate token invalidation requires a distributed Redis blocklist if implemented at enterprise scale.
3. **Text Search:** Implemented using PostgreSQL `ILIKE` and indexing. For millions of documents, PostgreSQL Full-Text Search (`tsvector`) or OpenSearch would be preferred.
