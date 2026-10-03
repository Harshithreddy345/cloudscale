# ECS demo deployment

This configuration passed provider validation and a live deployment test on 2026-10-03 UTC. API and worker services are currently stopped (desired count zero). Use Terraform 1.13+ and the AWS profile configured outside the repository. Keep Terraform state, variables containing your IP, and saved plans outside Git.

The deployment owns a dedicated VPC, one public subnet, an internet gateway, two security groups, private ECR repository, ECS cluster, separate API and worker task/execution roles, two Fargate services, and CloudWatch log groups with seven-day retention. Both tasks request 0.25 vCPU and 512 MiB memory. They use the same verified Java image with different Spring profiles. Task roles supply temporary AWS credentials through the SDK default provider chain. No access keys are copied into the image.

Existing S3, DynamoDB and SQS resources are read using data sources. Their creation and configuration remain manual; this is not a fully reproducible data-layer deployment. Terraform destroy will not delete them.

The API accepts HTTP on port 8080 only from allowed_cidr (/32). Use synthetic demo data. A single subnet, direct task IP, absent TLS and API authentication, and no processing leases make this a development demo. The task IP changes on replacement. Worker has no inbound rule. Public IPs allow access to ECR and AWS APIs without a NAT gateway; they are billed.

Deploy in stages after reviewing a saved plan: first apply with desired_count=0 to provision the repository and stopped services; push the verified Linux/x86 image under the immutable image_tag; then plan and apply desired_count=1. Verify running tasks, HTTP upload/report generation, and CloudWatch logs before marking deployment complete. Do not apply a plan with deletions or replacements without reviewing them.

To stop compute billing, plan and apply desired_count=0, and confirm both services have zero running tasks. Image storage, logs and data-service usage may still be billed. For full demo teardown, review a Terraform destroy plan using the same state; ECR refuses deletion while images remain, so remove only explicitly selected demo images first. Preserve state until teardown is confirmed. No automatic destroy or image deletion is provided.

Sources: [Fargate networking](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html), [task execution permissions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html), [Fargate pricing](https://aws.amazon.com/fargate/pricing/), [public IPv4 pricing](https://aws.amazon.com/vpc/pricing/).
