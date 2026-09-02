"""Classifica o estado observado da partição alvo -- lógica pura, recebe dados já lidos.

Não decide sozinha se deve truncar: só traduz "o que existe na partição" em um dos três
estados de `estado.EstadoParticao`. A decisão de agir (ou recusar) é da rotina.
"""

from __future__ import annotations

import datetime as dt

from expurgo_particao.estado import EstadoParticao

# A retenção deliberada é de 98 semanas (ver design.md, D2). Um dado corretamente
# esvaziável na partição alvo tem, por construção, idade de ~98 semanas (a variação de
# +-3.5 dias vem de onde dentro da semana ele foi escrito). Uma margem de segurança
# ampla -- 90 semanas -- separa "dado do ciclo anterior" (idade real) de "dado recente"
# (a anomalia): generosa o bastante para não confundir drift de execução ou de teste com
# escrita fora do fluxo esperado, e ainda assim muito abaixo do valor esperado de 98.
IDADE_MINIMA_SEMANAS_CICLO_ANTERIOR = 90
IDADE_MINIMA_DIAS_CICLO_ANTERIOR = IDADE_MINIMA_SEMANAS_CICLO_ANTERIOR * 7


def classificar_estado(
    data_mais_recente_na_particao: dt.datetime | None,
    data_referencia: dt.date,
) -> EstadoParticao:
    """
    `data_mais_recente_na_particao` é o MAX(data_hora_ultima_atlz) da partição alvo, ou
    None se a partição estiver vazia. `data_referencia` é o "hoje" simulado (ou real) da
    execução -- o mesmo usado para calcular a partição alvo.
    """
    if data_mais_recente_na_particao is None:
        return EstadoParticao.VAZIA

    idade_dias = (data_referencia - data_mais_recente_na_particao.date()).days
    if idade_dias >= IDADE_MINIMA_DIAS_CICLO_ANTERIOR:
        return EstadoParticao.DADO_CICLO_ANTERIOR
    return EstadoParticao.DADO_RECENTE
