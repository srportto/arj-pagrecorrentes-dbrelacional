# syntax=docker/dockerfile:1

FROM public.ecr.aws/lambda/python:3.13

COPY requirements.txt ${LAMBDA_TASK_ROOT}/
RUN pip install --no-cache-dir -r ${LAMBDA_TASK_ROOT}/requirements.txt

COPY src/expurgo_particao ${LAMBDA_TASK_ROOT}/expurgo_particao

CMD ["expurgo_particao.handler.lambda_handler"]
