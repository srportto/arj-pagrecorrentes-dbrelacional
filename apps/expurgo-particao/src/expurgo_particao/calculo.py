"""Cálculo puro de partição do ring buffer de expurgo -- sem I/O, sem efeito colateral.

Espelha `ControleExpurgoAutorizacao` (apps/contratocommand, Java). Qualquer mudança na
fórmula do lado Java precisa ser replicada aqui -- não há módulo compartilhado entre as
duas linguagens (mesma convenção de espelhamento manual do resto do monorepo, ver
CLAUDE.md da raiz).
"""

from __future__ import annotations

import datetime as dt

EPOCH = dt.date(1970, 1, 1)

QUANTIDADE_GAVETAS_EXPURGO = 100
PARTICAO_EXPURGO_INICIO = 900
PARTICAO_EXPURGO_FIM = 999

# Duas semanas de folga a frente do ponteiro de escrita -- ver design.md (D2) da change
# reclamar-particao-expurgo-ciclo para o racional completo (retencao de 98 semanas,
# deliberada, nao um numero redondo).
OFFSET_PARTICAO_ALVO = 2


def obter_particao_expurgo_write(data_finalizacao: dt.date) -> int:
    """900 + (semanas desde o Epoch 1970-01-01 % 100).

    Mesma origem temporal e mesma definicao de semana do lado Java
    (`ChronoUnit.WEEKS.between(LocalDate.ofEpochDay(0), data)`, floor de dias/7). Quem
    chama esta funcao decide antes qual e "a data de hoje" e a passa ja resolvida -- ver
    `agora_utc_date`, que fixa isso em UTC.
    """
    semanas_totais = (data_finalizacao - EPOCH).days // 7
    gaveta = semanas_totais % QUANTIDADE_GAVETAS_EXPURGO
    return PARTICAO_EXPURGO_INICIO + gaveta


def obter_particao_alvo(data_referencia: dt.date) -> int:
    """Particao alvo do ciclo: escrita + 2, com retorno ciclico a faixa 900..999.

    Nunca coincide com a particao de escrita do mesmo instante -- offset fixo de 2 dentro
    de um ciclo de 100 gavetas nunca produz zero.
    """
    particao_escrita = obter_particao_expurgo_write(data_referencia)
    alvo = particao_escrita + OFFSET_PARTICAO_ALVO
    if alvo > PARTICAO_EXPURGO_FIM:
        alvo -= QUANTIDADE_GAVETAS_EXPURGO
    return alvo


def agora_utc_date(agora: dt.datetime | None = None) -> dt.date:
    """"Hoje", fixado em UTC -- nunca no fuso horario local do processo.

    A gaveta vira toda quinta-feira 00:00 UTC (o epoch, 1970-01-01, foi quinta). Se a
    rotina calculasse a semana no fuso local do container, ela discordaria do
    `contratocommand` (que roda no fuso do host/JVM) por horas ao redor de cada virada --
    ver design.md, secao Risks, "Divergencia de relogio".
    """
    if agora is None:
        agora = dt.datetime.now(dt.timezone.utc)
    elif agora.tzinfo is None:
        raise ValueError("agora precisa ser timezone-aware; passe um datetime com tzinfo")
    else:
        agora = agora.astimezone(dt.timezone.utc)
    return agora.date()
