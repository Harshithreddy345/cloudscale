# Five-minute CloudScale demo

## Start locally

Prerequisite: Java 21. From the repository root, run `./mvnw verify` on Linux/macOS or `mvnw.cmd verify` on Windows. Start with `./mvnw spring-boot:run` or `mvnw.cmd spring-boot:run`. The workspace-specific Windows helper `run-local.ps1` also supports `-Verify`.

The API binds to localhost:8080. This mode uses local files, in-memory metadata and a local executor. Metadata is lost when this process stops.

## Demonstrate the API

Use curl (curl.exe in Windows PowerShell):

```text
curl -F "file=@sample-data/sales.csv" http://localhost:8080/jobs
curl http://localhost:8080/jobs/JOB_ID
curl http://localhost:8080/jobs/JOB_ID/result
curl http://localhost:8080/jobs
```

Replace JOB_ID with the returned id. Explain HTTP 202, poll until COMPLETED, then show 3 valid rows, 1 invalid row and revenue 1179.97. Wrong CSV headers lead to FAILED. Empty upload is rejected; an unknown job returns 404; an unfinished result returns 409. These are fixture results, not performance measurements.

## Explain the cloud version

Show the architecture diagram and verification.md. Explain S3 file storage, DynamoDB persistence, SQS delivery, the separate worker, ECS roles and CloudWatch logs. The ECS services are stopped to conserve credits; the live endpoint is not a permanent demo URL. A restart requires the same Terraform state, a reviewed desired_count=1 plan, and checking the task's new public IP. Do not expose the unauthenticated API broadly. Stop both services and confirm zero running tasks after any cloud demo.

## Suggested presentation

- First minute: the business problem and asynchronous request lifecycle.
- Second minute: submit the sample and inspect the report.
- Third minute: explain durable cloud storage and queue delivery.
- Fourth minute: show tested restart, duplicate and DLQ behavior.
- Fifth minute: discuss crash-recovery limits and the next design improvements.
