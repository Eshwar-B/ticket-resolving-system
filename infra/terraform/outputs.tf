output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.platform_vpc.id
}

output "eks_cluster_name" {
  description = "EKS Cluster Name"
  value       = aws_eks_cluster.platform_eks.name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL Endpoint"
  value       = aws_db_instance.postgres.endpoint
}

output "s3_bucket_name" {
  description = "S3 Knowledge Bucket Name"
  value       = aws_s3_bucket.knowledge_bucket.id
}
