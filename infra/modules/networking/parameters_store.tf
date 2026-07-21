resource "aws_ssm_parameter" "vpc_id" {
  name  = format("/%s/vpc/vpc_id", var.vpc_name)
  type  = "String"
  value = aws_vpc.this.id
}

resource "aws_ssm_parameter" "public_subnet_ids" {
  count = length(aws_subnet.public)

  name  = format("/%s/vpc/subnet_public_%s", var.vpc_name, var.az_suffixes[count.index])
  type  = "String"
  value = aws_subnet.public[count.index].id
}

resource "aws_ssm_parameter" "private_subnet_ids" {
  count = length(aws_subnet.private)

  name  = format("/%s/vpc/subnet_private_%s", var.vpc_name, var.az_suffixes[count.index])
  type  = "String"
  value = aws_subnet.private[count.index].id
}
