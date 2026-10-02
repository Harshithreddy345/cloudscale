# CloudScale

[![Java verification](https://github.com/Harshithreddy345/cloudscale/actions/workflows/ci.yml/badge.svg)](https://github.com/Harshithreddy345/cloudscale/actions/workflows/ci.yml)

An incremental Java project for asynchronous sales CSV reporting. The application uses one Spring Boot process, in-memory job metadata, and a bounded local executor. File storage defaults to local files; Phase 2 adds an optional S3 adapter. A real AWS deployment is pending.

Requires Java 21. The Maven wrapper pins Maven 3.9.9: run `sh ./mvnw verify`, then `sh ./mvnw spring-boot:run` (Windows: `./mvnw.cmd`). In this workspace, `./run-local.ps1 -Verify` and `./run-local.ps1` also find the downloaded portable tools.

## API walkthrough

```powershell
curl.exe -i -F "file=@sample-data/sales.csv" http://localhost:8080/jobs
curl.exe http://localhost:8080/jobs
curl.exe http://localhost:8080/jobs/JOB_ID
curl.exe http://localhost:8080/jobs/JOB_ID/result -o report.csv
```

Upload returns 202 with a job ID. Poll status until COMPLETED or FAILED. Results return 409 before completion, unknown IDs return 404. Uploads are limited to 10MB. Invalid data rows are counted and skipped; malformed CSV or wrong headers fail the job. Revenue uses decimal arithmetic. Each valid CSV row is a transaction line, not necessarily a unique order.

Restarting loses metadata and pending jobs. Local files remain but are not recovered. No authentication, durable queue, retries, or multi-instance coordination exists yet. This application binds to localhost by default. Do not expose this phase publicly.

## Roadmap

1. Local Job API and verified CSV report processing.
2. S3 file storage adapter: implemented; live AWS verification pending. See [S3 setup and decisions](docs/s3.md).
3. DynamoDB metadata with conditional claims and recovery leases.
4. SQS dispatch and separate worker; retry/DLQ and duplicate-delivery tests.
5. Docker, ECR, ECS/Fargate, CloudWatch and measured load experiments.
6. Terraform and deployment through GitHub Actions using OIDC.

See `docs/architecture.md` for interview explanations. No throughput or latency claims have been measured.

See `docs/verification.md` for completed checks and pending verification. Docker and AWS deployment remain pending. All input prices are assumed to use one currency; currency conversion is outside this milestone.
