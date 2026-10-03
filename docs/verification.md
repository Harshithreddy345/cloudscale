# CloudScale verification â€” 2026-10-02



## Live DynamoDB persistence: passed

The direct Windows verifier ran Maven verify: **29 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, including executable JAR packaging on 2026-10-02. It submitted job `3c01f827-2ba9-43aa-860a-0ef987f11af6` using the s3,dynamodb profiles and confirmed COMPLETED with 3 valid rows, 1 invalid row and revenue 1179.97. It stopped process 10072, started a new process 22220, retrieved the same job and byte-identical report, and confirmed the job remained listed. Evidence timestamp: 2026-10-02T18:30:32Z.

The table cloudscale-jobs uses string partition key jobId in us-east-2 with on-demand billing. Status updates use expected-status conditions and aliases for reserved attribute names. List scans omit the starting key on the first page and follow subsequent cursors. Initial tests found environment-dependent missing-setting cases and live checks found reserved-word and empty-cursor request issues; these were fixed before this successful run.

Completed-job persistence is verified. Interrupted-job recovery, leases, durable dispatch and SQS remain pending. Authentication used the explicitly approved broader temporary account role, not the bucket-only IAM user.

## Live S3 Job API: passed

On 2026-10-02, the Spring Boot application ran locally with the `s3` profile on localhost port 8081, using a private bucket in us-east-2. `POST /jobs` returned HTTP 202 for sample-data/sales.csv; polling observed PROCESSING and then COMPLETED with no error. Job ID: `26fb9f95-1bca-4d30-9d9e-7bff6b3ca1dc`.

The report downloaded through the API contained 3 valid rows, 1 invalid row, and revenue 1179.97. Direct S3 downloads of `uploads/<job-id>.csv` and `results/<job-id>.csv` matched the fixture and API report by SHA-256. Both objects reported AES256 server-side encryption. No performance or scale conclusions are drawn from this single small fixture.

Authentication used an explicitly approved temporary AccountFullAccessRole session, not the bucket-only cloudscale-dev user. This does not validate that user's permissions. Credentials and login caches remain outside the repository. The launcher ran directly in Windows because the restricted tool environment could not load Java dependencies.

## Phase 2 S3 adapter: passed

GitHub Actions [run 36966258835](https://github.com/Harshithreddy345/cloudscale/actions/runs/36966258835) at commit `f11fefc` reports **17 tests, 0 failures, 0 errors, 0 skipped** and **BUILD SUCCESS**. This includes the original 4 API tests, 5 S3 adapter contract tests, 4 storage configuration tests, 2 HTTP API outage tests, and 2 real AWS SDK HTTP tests against an in-process S3 protocol stub. Fake credentials are used only with the localhost stub. No real AWS account was contacted by these tests.

The checks cover signed PUT/GET requests, a worker report round trip, missing objects, replayable uploads, temporary-file cleanup, response-stream closure, failed-worker state, fail-fast bucket/region configuration, and retryable 503 responses without dispatching failed uploads.

## Phase 1: passed



GitHub Actions [run 36964320227](https://github.com/Harshithreddy345/cloudscale/actions/runs/36964320227) at commit `2d6d164` ran `bash ./mvnw --batch-mode verify` with Java 21 successfully. The logs report **4 tests, 0 failures, 0 errors, 0 skipped** and **BUILD SUCCESS**, including executable Spring Boot JAR packaging.



The automated tests cover completed-job report totals, invalid rows, failed headers, result conflicts, missing jobs, empty uploads, invalid UUIDs, job listing, and duplicate state claims.



A prior local live HTTP smoke check also passed: multipart upload (202), completed report download with revenue 1179.97 and one invalid row, failed headers, unavailable result (409), missing job (404), invalid UUID (400), and job listing.



These checks verify local API behavior; they do not measure capacity or distributed reliability.



## Pending



Docker build and local runtime verification passed, as detailed below. The S3 adapter passed the live API check above. A private bucket and IAM user/policy were created manually in AWS, not through Terraform. Terraform compute deployment and CloudWatch log verification passed as detailed below. Processing-crash recovery and automatic distributed scaling remain pending. The default local profile uses volatile metadata. The verified AWS profiles run the API and worker as separate local processes with durable storage; ECS compute deployment passed and services were stopped after verification.



The Codex Windows environment denied Java dependency path resolution and local Git metadata writes. GitHub CI resolved the build-verification blocker. Files were published through the authenticated GitHub browser interface with milestone commits. A fresh clone of https://github.com/Harshithreddy345/cloudscale is the canonical Git history; the original draft repository has an unrelated initial history and should not be pushed over it.


## Live SQS milestone — 2026-10-03 UTC

All 42 tests passed with zero failures, errors, or skipped tests. API process 1340 submitted job 90a67821-5284-4c9d-995b-9c112f9a7f9a without a worker. After restarting as process 3592, the job remained QUEUED. Separate worker process 2872 completed the report. Two duplicate deliveries were acknowledged without changing job metadata or report bytes. Invalid CSV reached FAILED. A malformed message reached the configured dead-letter queue after redelivery. The automated verifier saved evidence in cloudscale-sqs-verification.json outside the repository. These checks ran locally against real AWS storage and queues; ECS deployment and processing-crash recovery remain pending.

## Docker verification — 2026-10-03 UTC

The multi-stage Docker build completed with 42 tests, zero failures, errors, or skipped tests. A Linux container ran as user 10001:10001 with a localhost-only host port. Upload returned HTTP 202; job 4c621111-0e77-49ab-8463-b2419e9f814f completed, appeared in the job listing, and produced the fixture totals of 3 valid rows, 1 invalid row, and revenue 1179.97. Missing upload returned HTTP 400. The verifier removed only its own test container and retained the image. This check used local storage and in-memory metadata with no AWS credentials; it does not verify ECS or container-to-AWS authentication. Evidence was saved outside the repository in cloudscale-docker-verification.json.



## Live ECS/Fargate and CloudWatch - 2026-10-03 UTC

Terraform provider validation passed. The compute plan created 21 resources with separate execution, API and worker roles. The verified image was pushed to private ECR with digest sha256:5322456944085cf438fcb0dc93971f23b32116d1fc66b9c263b1269f1a54430f. One API task and one worker task reached RUNNING. Upload returned HTTP 202 and job 2291288c-e692-4957-b7b3-05d42136a230 completed with 3 valid rows, 1 invalid row and revenue 1179.97. CloudWatch recorded worker completion and an API log stream existed. Terraform then set both services to zero; AWS confirmed desired, running and pending counts were zero at 2026-10-03T03:55:13Z. Evidence and report are outside Git in cloudscale-ecs-verification.json and cloudscale-ecs-report.csv.

The demo uses one subnet and a changing public task IP with source-IP-restricted HTTP. TLS, API authentication, processing leases, outbox recovery, autoscaling, alarms, load testing, and full data-layer Terraform ownership remain future work. No throughput or latency claims were measured. Image storage, logs and existing data resources remain and may consume credits despite compute being stopped.
