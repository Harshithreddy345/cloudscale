# Phase 1 architecture and interview notes

Client -> JobController -> FileStore + JobRepository -> JobDispatcher -> JobWorker -> SalesProcessor.

The controller handles HTTP and upload validation. The processor knows CSV business rules, not HTTP or AWS. Three interfaces isolate infrastructure: FileStore (local files or optional S3), JobRepository (future DynamoDB), and JobDispatcher (future SQS). The worker can move into its own deployable application later; this milestone deliberately has one process. See `s3.md` for the implemented S3 adapter and the pending live AWS check.

## Decisions to explain

- 202 Accepted means processing was scheduled, not completed. A snapshot of QUEUED is returned even if a small job finishes quickly. Poll the status endpoint for current state.
- Two local worker threads and a queue of 100 prevent unlimited thread creation. Queue rejection returns 503 and records a failed job. These are configuration choices, not measured capacity claims.
- Immutable job snapshots and an atomic map update avoid races between readers and worker state transitions in this single JVM.
- UUID storage keys prevent uploaded filenames from controlling filesystem paths.
- Commons CSV handles quoted fields correctly. Rows are read incrementally; aggregation memory still grows with distinct products/categories/regions/months. Reports are currently buffered in memory. Uploads are capped at 10MB.
- BigDecimal preserves decimal prices. Valid transaction lines are counted, not distinct orders. Invalid rows are counted; an invalid-row download, top-product ranking, and customer statistics remain follow-up work.
- The state claim prevents duplicate local execution after a job leaves QUEUED. It does not provide exactly-once distributed processing.

## AWS correctness work still required

Saving an object, writing metadata, and sending an SQS message are not one transaction. Plan an outbox or reconciliation strategy so jobs are not stranded when dispatch fails. A PROCESSING flag alone is insufficient after a crash: add leases, attempt identifiers, conditional ownership updates, visibility extensions, and retry recovery. Publish results under deterministic keys and mark completion only after upload succeeds. Delete SQS messages only after the durable completion update. Validate these failure paths with tests before claiming reliability.

Local metadata grows without pagination or retention and disappears on restart. No tenant isolation, authentication, durable retries, AWS deployment, or performance experiments are implemented.

## Learning walkthrough

1. Read JobController.create and trace the saved file, metadata, dispatch, and response.
2. Read JobWorker.process and explain each failure point and state change.
3. Read SalesProcessor and manually calculate the sample revenue: 899.99 + 159.98 + 120.00 = 1179.97.
4. Run the tests, then upload the sample and a CSV with wrong headers.
5. Explain what is lost if the JVM terminates, then describe how durable storage and a queue will change that behavior.
