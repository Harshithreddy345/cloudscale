terraform {
  backend "local" {}
  required_version = ">= 1.13.0, < 2.0.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
}
provider "aws" {
  region = "us-east-2"
  default_tags { tags = { Project = "CloudScale", ManagedBy = "Terraform" } }
}
variable "allowed_cidr" {
  type        = string
  description = "Your public IPv4 address with /32; API access is restricted to this address."
  validation {
    condition     = can(cidrhost(var.allowed_cidr, 0)) && endswith(var.allowed_cidr, "/32")
    error_message = "Provide one IPv4 address with /32."
  }
}
variable "image_tag" { type = string }
variable "desired_count" {
  type    = number
  default = 1
  validation {
    condition     = contains([0, 1], var.desired_count)
    error_message = "This demo supports zero or one task per service."
  }
}
data "aws_caller_identity" "current" {}
data "aws_availability_zones" "available" { state = "available" }
data "aws_s3_bucket" "data" { bucket = "cloudscale-harshith-2026-data" }
data "aws_dynamodb_table" "jobs" { name = "cloudscale-jobs" }
data "aws_sqs_queue" "jobs" { name = "cloudscale-jobs" }
locals {
  roles = toset(["api", "worker"])
  trust = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  environment = [
    { name = "AWS_REGION", value = "us-east-2" },
    { name = "CLOUDSCALE_S3_BUCKET", value = data.aws_s3_bucket.data.id },
    { name = "CLOUDSCALE_DYNAMODB_TABLE", value = data.aws_dynamodb_table.jobs.name },
    { name = "CLOUDSCALE_SQS_QUEUE_URL", value = data.aws_sqs_queue.jobs.url },
    { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=60" }
  ]
}
resource "aws_vpc" "demo" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
}
resource "aws_internet_gateway" "demo" { vpc_id = aws_vpc.demo.id }
resource "aws_subnet" "demo" {
  vpc_id            = aws_vpc.demo.id
  cidr_block        = "10.42.1.0/24"
  availability_zone = data.aws_availability_zones.available.names[0]
}
resource "aws_route_table" "demo" {
  vpc_id = aws_vpc.demo.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.demo.id
  }
}
resource "aws_route_table_association" "demo" {
  subnet_id      = aws_subnet.demo.id
  route_table_id = aws_route_table.demo.id
}
resource "aws_security_group" "task" {
  for_each    = local.roles
  name_prefix = "cloudscale-${each.key}-"
  vpc_id      = aws_vpc.demo.id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  dynamic "ingress" {
    for_each = each.key == "api" ? [1] : []
    content {
      from_port   = 8080
      to_port     = 8080
      protocol    = "tcp"
      cidr_blocks = [var.allowed_cidr]
    }
  }
}
resource "aws_ecr_repository" "app" {
  name                 = "cloudscale-app"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
}
resource "aws_cloudwatch_log_group" "task" {
  for_each          = local.roles
  name              = "/cloudscale/${each.key}"
  retention_in_days = 7
}
resource "aws_iam_role" "execution" {
  name               = "cloudscale-task-execution"
  assume_role_policy = local.trust
}
resource "aws_iam_role_policy" "execution" {
  role = aws_iam_role.execution.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
    { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage"], Resource = aws_ecr_repository.app.arn },
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = [for group in aws_cloudwatch_log_group.task : "${group.arn}:*"] }
  ] })
}
resource "aws_iam_role" "task" {
  for_each           = local.roles
  name               = "cloudscale-${each.key}-task"
  assume_role_policy = local.trust
}
resource "aws_iam_role_policy" "task" {
  for_each = local.roles
  role     = aws_iam_role.task[each.key].id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = each.key == "api" ? ["s3:PutObject"] : ["s3:GetObject"], Resource = "${data.aws_s3_bucket.data.arn}/uploads/*" },
    { Effect = "Allow", Action = each.key == "api" ? ["s3:GetObject"] : ["s3:PutObject"], Resource = "${data.aws_s3_bucket.data.arn}/results/*" },
    { Effect = "Allow", Action = each.key == "api" ? ["dynamodb:PutItem", "dynamodb:GetItem", "dynamodb:Scan"] : ["dynamodb:GetItem", "dynamodb:UpdateItem"], Resource = data.aws_dynamodb_table.jobs.arn },
    { Effect = "Allow", Action = each.key == "api" ? ["sqs:SendMessage"] : ["sqs:ReceiveMessage", "sqs:DeleteMessage"], Resource = data.aws_sqs_queue.jobs.arn }
  ] })
}
resource "aws_ecs_cluster" "demo" { name = "cloudscale-demo" }
resource "aws_ecs_task_definition" "app" {
  for_each                 = local.roles
  family                   = "cloudscale-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task[each.key].arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  container_definitions = jsonencode([{
    name         = each.key
    image        = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
    essential    = true
    user         = "10001:10001"
    environment  = local.environment
    command      = ["--spring.profiles.active=s3,dynamodb,${each.key == "api" ? "sqs" : "sqs-worker"}"]
    portMappings = each.key == "api" ? [{ containerPort = 8080, protocol = "tcp" }] : []
    logConfiguration = { logDriver = "awslogs", options = {
      "awslogs-group"         = aws_cloudwatch_log_group.task[each.key].name
      "awslogs-region"        = "us-east-2"
      "awslogs-stream-prefix" = "ecs"
    } }
  }])
}
resource "aws_ecs_service" "app" {
  for_each                           = local.roles
  name                               = "cloudscale-${each.key}"
  cluster                            = aws_ecs_cluster.demo.id
  task_definition                    = aws_ecs_task_definition.app[each.key].arn
  launch_type                        = "FARGATE"
  desired_count                      = var.desired_count
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  network_configuration {
    subnets          = [aws_subnet.demo.id]
    security_groups  = [aws_security_group.task[each.key].id]
    assign_public_ip = true
  }
  depends_on = [aws_iam_role_policy.execution, aws_iam_role_policy.task, aws_route_table_association.demo]
}
output "repository_url" { value = aws_ecr_repository.app.repository_url }
output "cluster_name" { value = aws_ecs_cluster.demo.name }
output "service_names" { value = [for service in aws_ecs_service.app : service.name] }
