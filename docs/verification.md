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



Docker has not been built or run. The S3 adapter passed the live API check above. A private bucket and IAM user/policy were created manually in AWS, not through Terraform. Terraform deployment, durable retries, distributed scaling, and CloudWatch integrations remain pending. The application runs as one local process with volatile metadata; compute has not been deployed to AWS.



The Codex Windows environment denied Java dependency path resolution and local Git metadata writes. GitHub CI resolved the build-verification blocker. Files were published through the authenticated GitHub browser interface with milestone commits. A fresh clone of https://github.com/Harshithreddy345/cloudscale is the canonical Git history; the original draft repository has an unrelated initial history and should not be pushed over it.

