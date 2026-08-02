resource "aws_sns_topic" "estados_autorizacao" {
  name = var.sns_topic_name
}

resource "aws_sqs_queue" "eventos_autorizacao_dlq" {
  name = "${var.sqs_queue_name}-dlq"
}

resource "aws_sqs_queue" "eventos_autorizacao" {
  name = var.sqs_queue_name

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
