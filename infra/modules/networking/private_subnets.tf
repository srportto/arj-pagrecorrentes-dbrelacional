resource "aws_subnet" "private" {
  count = length(var.private_subnet_cidrs)

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = local.azs[count.index]

  tags = merge(var.tags, {
    Name = format("%s-private-subnet-%s", var.vpc_name, var.az_suffixes[count.index])
  })
}

## -----------------------------------------------------------------------------
## Route table privada por AZ: rota 0.0.0.0/0 -> NAT Gateway da AZ.
## Quando var.nat_gateway_count < numero de AZs, AZs excedentes compartilham
## o NAT Gateway via indice modulo (round-robin), permitindo egress mesmo
## sem 1 NAT dedicado por AZ.
## -----------------------------------------------------------------------------

resource "aws_route_table" "private" {
  count = length(aws_subnet.private)

  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = format("%s-private-%s", var.vpc_name, var.az_suffixes[count.index])
  })
}

resource "aws_route" "private_nat_access" {
  count = length(aws_subnet.private)

  route_table_id         = aws_route_table.private[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this[count.index % var.nat_gateway_count].id
}

resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}
