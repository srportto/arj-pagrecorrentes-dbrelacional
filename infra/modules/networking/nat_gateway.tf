## -----------------------------------------------------------------------------
## NAT Gateway(s). var.nat_gateway_count controla quantos sao criados:
##   1                       -> 1 NAT compartilhado por todas as AZs privadas
##   length(var.az_suffixes) -> 1 NAT por AZ (HA real, usado em envs/local)
## Cada NAT criado ocupa a subnet publica da mesma AZ (indice count.index).
## -----------------------------------------------------------------------------

resource "aws_eip" "nat" {
  count = var.nat_gateway_count

  domain = "vpc"

  tags = merge(var.tags, {
    Name = format("%s-eip-%s", var.vpc_name, var.az_suffixes[count.index])
  })
}

resource "aws_nat_gateway" "this" {
  count = var.nat_gateway_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(var.tags, {
    Name = format("%s-ngw-%s", var.vpc_name, var.az_suffixes[count.index])
  })

  depends_on = [aws_internet_gateway.this]
}
