output "vpc_id" {
  description = "ID da VPC criada."
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "CIDR block da VPC."
  value       = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "IDs das subnets publicas, na mesma ordem de var.az_suffixes."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "IDs das subnets privadas, na mesma ordem de var.az_suffixes."
  value       = aws_subnet.private[*].id
}

output "internet_gateway_id" {
  description = "ID do Internet Gateway anexado a VPC."
  value       = aws_internet_gateway.this.id
}

output "nat_gateway_ids" {
  description = "IDs dos NAT Gateways criados."
  value       = aws_nat_gateway.this[*].id
}

output "base_security_group_id" {
  description = "ID do security group base da VPC, reutilizavel pelos modulos de compute."
  value       = aws_security_group.base.id
}

output "availability_zones" {
  description = "Availability zones usadas (region + az_suffixes), na mesma ordem das subnets."
  value       = local.azs
}
