resource "aws_sns_topic" "estados_autorizacao" {
  name = var.sns_topic_name
}

resource "aws_sqs_queue" "eventos_autorizacao_dlq" {
  name = "${var.sqs_queue_name}-dlq"
}

resource "aws_sqs_queue" "eventos_autorizacao" {
  name                       = var.sqs_queue_name
  visibility_timeout_seconds = var.sqs_visibility_timeout_seconds

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.eventos_autorizacao_dlq.arn
    maxReceiveCount     = var.sqs_dlq_max_receive_count
  })
}

# Permite o SNS publicar na fila. Mantido mesmo no emulador para ter
# fidelidade com o comportamento exigido em AWS real.
data "aws_iam_policy_document" "eventos_autorizacao_sqs_policy" {
  statement {
    sid     = "AllowSnsPublish"
    effect  = "Allow"
    actions = ["sqs:SendMessage"]

    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }

    resources = [aws_sqs_queue.eventos_autorizacao.arn]

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.estados_autorizacao.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "eventos_autorizacao" {
  queue_url = aws_sqs_queue.eventos_autorizacao.id
  policy    = data.aws_iam_policy_document.eventos_autorizacao_sqs_policy.json
}

# raw_message_delivery=true: o body na fila e o JSON puro publicado no topico,
# sem o envelope SNS (Type/TopicArn/Message).
resource "aws_sns_topic_subscription" "eventos_autorizacao" {
  topic_arn            = aws_sns_topic.estados_autorizacao.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.eventos_autorizacao.arn
  raw_message_delivery = true

  depends_on = [aws_sqs_queue_policy.eventos_autorizacao]
}

# Fila consumida pela aplicacao temporiza-autorizacao. Recebe, via filter policy da
# subscription abaixo, apenas eventos de recepcao de PIX_AUTO na jornada SPI_J1 — o
# unico caso temporizado por esta mudanca (ver capacidade temporizacao-jornada-01).
resource "aws_sqs_queue" "temporizacao_autorizacao_dlq" {
  name = "${var.sqs_temporizacao_queue_name}-dlq"
}

resource "aws_sqs_queue" "temporizacao_autorizacao" {
  name                       = var.sqs_temporizacao_queue_name
  visibility_timeout_seconds = var.sqs_temporizacao_visibility_timeout_seconds

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.temporizacao_autorizacao_dlq.arn
    maxReceiveCount     = var.sqs_temporizacao_dlq_max_receive_count
  })
}

data "aws_iam_policy_document" "temporizacao_autorizacao_sqs_policy" {
  statement {
    sid     = "AllowSnsPublish"
    effect  = "Allow"
    actions = ["sqs:SendMessage"]

    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }

    resources = [aws_sqs_queue.temporizacao_autorizacao.arn]

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.estados_autorizacao.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "temporizacao_autorizacao" {
  queue_url = aws_sqs_queue.temporizacao_autorizacao.id
  policy    = data.aws_iam_policy_document.temporizacao_autorizacao_sqs_policy.json
}

# filter_policy restringe a entrega, por message attribute, a RECEPCAO + PIX_AUTO + SPI_J1
# (ver AutorizacaoEventoPublisher no arj-contratocommand para os attributes publicados).
# Se o emulador local nao suportar filter policy por attribute, a subscription passa a
# entregar todos os eventos do topico — a divergencia fica isolada aqui, nunca na
# aplicacao consumidora (ver capacidade local-messaging-environment).
resource "aws_sns_topic_subscription" "temporizacao_autorizacao" {
  topic_arn            = aws_sns_topic.estados_autorizacao.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.temporizacao_autorizacao.arn
  raw_message_delivery = true

  filter_policy = jsonencode({
    tipoEvento  = ["RECEPCAO"]
    tipoProduto = ["PIX_AUTO"]
    tipoJornada = ["SPI_J1"]
  })

  depends_on = [aws_sqs_queue_policy.temporizacao_autorizacao]
}
