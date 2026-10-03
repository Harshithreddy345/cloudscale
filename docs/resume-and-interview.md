# Resume and interview wording

## Project entry

**CloudScale - Asynchronous Sales Data Processing Platform**  
Java 21, Spring Boot, AWS S3, DynamoDB, SQS, ECS/Fargate, ECR, CloudWatch, Docker, Terraform, GitHub Actions

- Built an asynchronous CSV reporting API with job-status tracking, decimal revenue aggregation, validation and downloadable reports.
- Integrated S3 file storage, DynamoDB conditional job transitions, and SQS delivery with a separate worker; verified metadata persistence across restart, terminal-job duplicate handling and dead-letter redrive.
- Containerized and deployed the API and worker to ECS/Fargate with Terraform-managed compute infrastructure, separate IAM roles and CloudWatch logs; validated report generation with 42 passing tests and a live AWS smoke test.

Use these bullets once you can explain the code and tradeoffs. Do not add fabricated speedups, user counts, throughput or uptime claims. GitHub Actions runs Java verification; it does not deploy the infrastructure automatically.

## A 45-second introduction

I built CloudScale to process sales CSV files without keeping the client waiting in a long HTTP request. The API saves the input in S3, records job metadata in DynamoDB, publishes the job ID to SQS and returns HTTP 202. A separate worker claims the job using a conditional update, generates revenue breakdowns and stores the report in S3. I deployed the API and worker as separate ECS/Fargate services using Terraform and verified the report and CloudWatch logs. I also tested persistence across restart, duplicate deliveries and dead-letter handling. The next reliability improvement is ownership leases and reconciliation for crashes between storage, metadata and queue operations.

## Questions to practice

**Why a queue?** It separates request acceptance from execution and retains work when workers are unavailable. The tested restart case was an API restart with a queued job.

**Why DynamoDB?** It provides durable status and conditional transitions. The demo uses direct SDK calls. Listing currently scans all jobs; indexing and client pagination are future improvements.

**Can the worker process the same job twice?** Terminal duplicates are skipped after checking stored state. Conditional claims prevent simultaneous QUEUED claims. Crashes and ambiguous writes still require leases and reconciliation; I do not claim exactly-once execution.

**What happens after a worker crash?** Messages can be redelivered, but a PROCESSING record can remain stranded. The current version has no automated lease-based recovery.

**How do you handle money?** BigDecimal preserves decimal arithmetic. The input assumes one currency; there is no currency conversion.

**How did you verify it?** 42 tests, live S3 retrieval, DynamoDB restart persistence, SQS duplicate and DLQ tests, a Docker API smoke test, and a live ECS report with CloudWatch completion evidence. No load benchmark was run.

**What would you improve next?** Leases and fencing, an outbox/reconciler, authenticated TLS access, indexed listing, full infrastructure ownership, and measured load tests before choosing autoscaling thresholds.
