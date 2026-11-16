# ==============================================================================
# Enterprise AI Platform — AWS Cloud Reference Architecture (Terraform)
# ==============================================================================
# NOTICE: This architecture is for validated reference and cost-estimation only.
# Under project governance (Cost = ₹0), this is NOT applied to live cloud infrastructure.
# ==============================================================================

terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "enterprise-ai-platform"
      Environment = var.environment
      ManagedBy   = "terraform"
      CostCenter  = "zero-cloud-spend-reference"
    }
  }
}

# ------------------------------------------------------------------------------
# 1. Virtual Private Cloud (VPC) & Networking
# ------------------------------------------------------------------------------
resource "aws_vpc" "platform_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${var.environment}-enterprise-ai-vpc"
  }
}

resource "aws_subnet" "public_subnets" {
  count                   = 2
  vpc_id                  = aws_vpc.platform_vpc.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.environment}-public-subnet-${count.index + 1}"
  }
}

resource "aws_subnet" "private_subnets" {
  count             = 2
  vpc_id            = aws_vpc.platform_vpc.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + 4)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = {
    Name = "${var.environment}-private-subnet-${count.index + 1}"
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

# ------------------------------------------------------------------------------
# 2. Amazon EKS Cluster (Control Plane & Worker Nodes)
# ------------------------------------------------------------------------------
resource "aws_eks_cluster" "platform_eks" {
  name     = "${var.environment}-enterprise-ai-eks"
  role_arn = aws_iam_role.eks_cluster_role.arn
  version  = "1.30"

  vpc_config {
    subnet_ids              = aws_subnet.private_subnets[*].id
    endpoint_private_access = true
    endpoint_public_access  = false
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy
  ]
}

resource "aws_iam_role" "eks_cluster_role" {
  name = "${var.environment}-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role = aws_iam_role.eks_cluster_role.name
}

# ------------------------------------------------------------------------------
# 3. Amazon RDS PostgreSQL 16 (Multi-AZ Multi-Tenant Database)
# ------------------------------------------------------------------------------
resource "aws_db_subnet_group" "db_subnets" {
  name       = "${var.environment}-db-subnet-group"
  subnet_ids = aws_subnet.private_subnets[*].id
}

resource "aws_db_instance" "postgres" {
  identifier          = "${var.environment}-enterprise-ai-postgres"
  allocated_storage   = 50
  max_allocated_storage = 200
  engine              = "postgres"
  engine_version      = "16.3"
  instance_class      = "db.t4g.medium"
  multi_az            = true
  db_subnet_group_name = aws_db_subnet_group.db_subnets.name
  skip_final_snapshot = true
  deletion_protection = false

  username = var.db_username
  password = var.db_password

  tags = {
    Name = "${var.environment}-postgres"
  }
}

# ------------------------------------------------------------------------------
# 4. Amazon Managed Streaming for Apache Kafka (MSK)
# ------------------------------------------------------------------------------
resource "aws_msk_cluster" "kafka" {
  cluster_name           = "${var.environment}-enterprise-ai-kafka"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = 2

  broker_node_group_info {
    instance_type   = "kafka.t3.small"
    client_subnets  = aws_subnet.private_subnets[*].id
    security_groups = [aws_security_group.kafka_sg.id]
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "PLAINTEXT"
      in_cluster    = true
    }
  }
}

resource "aws_security_group" "kafka_sg" {
  name        = "${var.environment}-kafka-sg"
  description = "Security group for MSK cluster"
  vpc_id      = aws_vpc.platform_vpc.id

  ingress {
    from_port   = 9092
    to_port     = 9092
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
}

# ------------------------------------------------------------------------------
# 5. Amazon S3 Storage (Multi-Tenant Document Archival)
# ------------------------------------------------------------------------------
resource "aws_s3_bucket" "knowledge_bucket" {
  bucket        = "${var.environment}-enterprise-ai-knowledge-docs"
  force_destroy = false

  tags = {
    Name        = "Knowledge Documents Bucket"
    MultiTenant = "true"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "s3_encryption" {
  bucket = aws_s3_bucket.knowledge_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
