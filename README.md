# CloudScale

An incremental Java project for asynchronous sales CSV reporting. The default local mode uses in-memory metadata and a bounded executor. Optional AWS profiles use S3 for files, DynamoDB for durable job metadata, and SQS with a separate worker process. Live S3, DynamoDB restart, and SQS delivery checks have passed. A live ECS/Fargate deployment test passed, and both demo services were stopped afterward.

Requires Java 21. The Maven wrapper pins Maven 3.9.9: run `sh ./mvnw verify`, then `sh ./mvnw spring-boot:run` (Windows: `./mvnw.cmd`). In this workspace, `./run-local.ps1 -Verify` and `./run-local.ps1` also find the downloaded portable tools.

## API walkthrough

```powershell
curl.exe -i -F "file=@sample-data/sales.csv" http://localhost:8080/jobs
curl.exe http://localhost:8080/jobs
curl.exe http://localhost:8080/jobs/JOB_ID
curl.exe http://localhost:8080/jobs/JOB_ID/result -o report.csv
```

Upload returns 202 with a job ID. Poll status until COMPLETED or FAILED. Results return 409 before completion, unknown IDs return 404. Uploads are limited to 10MB. Invalid data rows are counted and skipped; malformed CSV or wrong headers fail the job. Revenue uses decimal arithmetic. Each valid CSV row is a transaction line, not necessarily a unique order.

In default local mode, restarting loses metadata and pending jobs. AWS profiles preserve metadata and queued messages. Processing crash recovery still requires leases and reconciliation; API authentication is not implemented. This application binds to localhost by default. Do not expose this phase publicly.

## Roadmap

1. Local Job API and verified CSV report processing.
2. S3 file storage adapter: implemented and verified against a private AWS bucket. See [S3 setup and decisions](docs/s3.md).
3. DynamoDB metadata with atomic status claims: 29 tests and live restart verification passed. See [DynamoDB decisions and limitations](docs/dynamodb.md). Recovery leases are future work.
4. SQS dispatch and separate worker: 42 tests and live API restart, duplicate delivery, failed CSV, and dead-letter checks passed. See [delivery guarantees and limits](docs/sqs.md).
5. Docker image build and local container API/report verification passed with 42 tests. Private ECR upload, ECS/Fargate API and worker, and CloudWatch completion verification passed. Both services are stopped. Measured load experiments remain pending.
6. Terraform compute deployment verified; automated deployment through GitHub Actions using OIDC remains future work.

See `docs/architecture.md` for interview explanations. No throughput or latency claims have been measured.

See `docs/verification.md` for completed checks and pending verification. Docker local verification passed; The AWS compute demo passed and is stopped; image storage, logs and data resources remain. All input prices are assumed to use one currency; currency conversion is outside this milestone.


