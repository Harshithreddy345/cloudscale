# Phase 3: durable job metadata

Activate the `dynamodb` profile together with `s3`, set AWS_REGION, CLOUDSCALE_S3_BUCKET and CLOUDSCALE_DYNAMODB_TABLE, and configure temporary AWS credentials as described in s3.md. Without this profile the default remains the in-memory repository.

The table must have a string partition key `jobId`, with no sort key. This milestone created the dedicated `cloudscale-jobs` table manually through AWS CLI in us-east-2 using on-demand billing. It is not managed by Terraform. The application does not create tables.

## Architecture and interview explanation

JobRepository separates the API and worker from persistence. Replacing its implementation keeps the public Job API unchanged. S3 stores CSV bytes; DynamoDB stores job IDs, original display filenames, status, timestamps and deterministic input/result object keys.

PutItem uses attribute_not_exists(jobId) to prevent accidental overwrites. UpdateItem checks both existence and the expected status before changing it. This database check is atomic: two workers racing from QUEUED to PROCESSING cannot both win. Legal status transitions match the in-memory repository. Point reads use strong consistency for immediate status checks.

GET /jobs scans all DynamoDB pages and sorts by createdAt for compatibility with Phase 1. This is an educational implementation, not a scalable listing strategy. Scans are unbounded, cost grows with records, and even strongly consistent scans are not a table-wide snapshot. Before scaling, add an indexed access pattern and a paginated API.

## Failure boundaries

S3 storage, DynamoDB metadata and local dispatch are separate operations. A failed metadata create can leave an orphan input object. Ambiguous network failures after a write may mean it actually succeeded; callers should not assume every 503 means no side effects. There is no cross-service transaction or idempotent POST yet.

Once a report is saved, failure to confirm COMPLETED is logged for reconciliation rather than falsely declaring report processing failed. If the database is unavailable during claim, no work is started. SDK retries are not durable job retries.

Completed job records and S3 reports survive an application restart. QUEUED and PROCESSING records also survive, but the local queue does not. This phase does not resume interrupted jobs or reclaim stale claims. Recovery leases, fencing tokens, reconciliation and SQS are later work. Do not describe this milestone as crash-recoverable distributed processing.

Metadata service failures return HTTP 503 with a generic message. Credentials and AWS details are not returned to API clients. The API still has no user authentication and remains bound to localhost.

## Verification status

The implementation adds repository contract tests, configuration selection/fail-fast tests, API error sanitization, and worker completion-failure handling. All 29 tests passed with zero failures, errors or skipped tests on 2026-10-02. A real S3/DynamoDB job completed; after stopping process 10072 and starting process 22220, the same job (3c01f827-2ba9-43aa-860a-0ef987f11af6) and byte-identical report were retrieved and the job remained in the listing. Report totals: 3 valid rows, 1 invalid row, revenue 1179.97. Evidence was saved locally as cloudscale-dynamodb-verification.json. This small test does not measure performance or recovery of interrupted jobs.

The local launcher start-cloudscale-dynamodb.cmd first runs Maven verify and only starts the application if all tests pass. It uses port 8082 so the earlier S3-only process can remain running while verifying this phase. Login/config/cache files remain outside the repository under work/aws.

Runtime permissions required: dynamodb:GetItem, PutItem, UpdateItem and Scan for only the configured table. Table creation/inspection is a separate provisioning permission. The existing approved AccountFullAccessRole session is broader than these permissions. The bucket-only cloudscale-dev user has not been given database permissions or used for this test.

Sources: [AWS condition expressions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ConditionExpressions.html), [scan pagination and consistency](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Scan.html).
