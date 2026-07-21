locals {
  # AZs derivadas via concatenacao de string (nao usa data "aws_availability_zones")
  # para nao depender da fidelidade do DescribeAvailabilityZones no Floci.
  azs = [for suffix in var.az_suffixes : format("%s%s", var.region, suffix)]
}

resource "aws_vpc" "this" {
  cidr_block = var.vpc_cidr

  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, {
    Name = var.vpc_name
  })
}
