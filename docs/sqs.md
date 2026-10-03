# Phase 4: SQS dispatch and a separate worker

Run the API with profiles `s3,dynamodb,sqs` and the worker in another process with `s3,dynamodb,sqs-worker`. Set AWS_REGION, CLOUDSCALE_S3_BUCKET, CLOUDSCALE_DYNAMODB_TABLE and CLOUDSCALE_SQS_QUEUE_URL. The worker has no web server or JobController, and the API does not start a queue consumer. Local mode remains the default without the SQS profile. SQS mode requires durable S3 and DynamoDB configuration.

## Delivery contract

The API stores input bytes in S3, creates a QUEUED record in DynamoDB, sends only the job UUID to SQS, then returns HTTP 202. The three writes are not a transaction. A crash or ambiguous send can leave a record without a message; a 503 may follow a successful send. The record stays QUEUED, and the error response advises inspecting the listing before retrying. Outbox publication and idempotent submission are not implemented.

A single worker long-polls for one message at a time. It atomically claims QUEUED -> PROCESSING in DynamoDB, writes the report to S3 and confirms COMPLETED before acknowledging the message. A completed or failed duplicate is acknowledged without reading or rewriting files. An active PROCESSING duplicate is not acknowledged. A delete failure permits redelivery; a later terminal-state check prevents rerunning a completed job.

Invalid CSV or file processing failures retain the existing FAILED-job behavior and are acknowledged when the terminal state is confirmed. This milestone does not automatically retry failed CSV processing. Database outages or unresolved claims retain the message for transport redelivery. Malformed job IDs remain in the queue and eventually reach the configured DLQ. Poison-message bodies are not logged.

## Queue setup

The manually provisioned standard queues are cloudscale-jobs and cloudscale-jobs-dlq in us-east-2. Main visibility timeout: 30 seconds. Long polling: 5 seconds. DLQ redrive threshold: 3 receives. Both use SQS-managed encryption; DLQ retention is 14 days. These resources are not managed by Terraform yet. The DLQ is for messages the consumer cannot resolve, not every failed application job.

## Honest reliability limits

QUEUED work with a successfully published SQS message survives API restart and does not require the API to remain running for processing. This is not exactly-once delivery. Atomic status checks suppress duplicate file processing once a job is claimed, but there is no lease expiry, heartbeat or fencing token. A worker killed after claiming can leave PROCESSING permanently; its message may reach the DLQ and requires reconciliation. Processing longer than the visibility timeout may cause duplicates or premature redrive. Do not deploy this milestone as crash-recoverable processing until leases and visibility renewal are added.

There is one sequential worker per process. No throughput, latency or scaling claims are measured. ECS, monitoring and deployment remain future milestones.

## Verification

New tests cover UUID-only publication, acknowledgement decisions, malformed-message retention, duplicate terminal claims, unresolved active claims, publish outages, long polling, configuration and missing settings. The automatic Windows verifier builds/tests before live AWS use, starts the API without a worker, submits a job, restarts the API, then starts a separate worker. It checks report totals, duplicate acknowledgement without changing report/status, invalid CSV FAILED status, and malformed-message redrive to the DLQ. The verifier leaves its malformed test message in the DLQ for inspection. Verification is pending until recorded evidence exists.

Runtime access should be limited by role: API needs SendMessage on the main queue; worker needs ReceiveMessage and DeleteMessage on the main queue plus S3/DynamoDB processing access. Provisioning and DLQ inspection require separate permissions. Current verification uses the previously approved broader temporary account role. No credentials are in source files.

Sources: [SQS at-least-once delivery](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html), [visibility and redelivery](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html).

The example SQS policy in infrastructure/sqs-policy.example.json is a template. For deployment, assign ApiPublish to the API role and WorkerConsume to the worker role separately. It has not been applied to the current account session.
