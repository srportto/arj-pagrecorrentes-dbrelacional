"""Tipos que descrevem o estado observado da partição alvo e a ação tomada sobre ela.

Três estados, não dois -- ver design.md (D3) da change `reclamar-particao-expurgo-ciclo`:
uma partição vazia é resultado normal (a rotina não terá efeito por ~87 semanas, e isso
não pode ser confundido com falha); dado do ciclo anterior é o caso a esvaziar; dado
recente é a anomalia que a rotina recusa em vez de apagar.
"""

from __future__ import annotations

import dataclasses
import datetime as dt
import enum


class EstadoParticao(str, enum.Enum):
    VAZIA = "VAZIA"
    DADO_CICLO_ANTERIOR = "DADO_CICLO_ANTERIOR"
    DADO_RECENTE = "DADO_RECENTE"


class Acao(str, enum.Enum):
    NENHUMA = "NENHUMA"
    TRUNCATE = "TRUNCATE"
    RECUSA_DADO_RECENTE = "RECUSA_DADO_RECENTE"
    RECUSA_LOCK_TIMEOUT = "RECUSA_LOCK_TIMEOUT"


@dataclasses.dataclass(frozen=True)
class ResultadoExecucao:
    """O que toda execução produz -- inclusive quando não faz nada.

    Registrar o que foi CALCULADO (não só o que foi feito) é o que torna uma execução sem
    efeito distinguível de uma rotina quebrada durante as ~87 semanas em que o anel ainda
    não completou a primeira volta.
    """

    semana: int
    particao_escrita: int
    particao_alvo: int
    # None só no caso de RECUSA_LOCK_TIMEOUT: a verificação nem chegou a rodar, então não
    # há estado observado para relatar -- não é o mesmo que VAZIA.
    estado: EstadoParticao | None
    acao: Acao
    modo_consulta: bool
    executado_em: dt.datetime

    def como_registro(self) -> dict:
        return {
            "semana": self.semana,
            "particao_escrita": self.particao_escrita,
            "particao_alvo": self.particao_alvo,
            "estado": self.estado.value if self.estado is not None else None,
            "acao": self.acao.value,
            "modo_consulta": self.modo_consulta,
            "executado_em": self.executado_em.isoformat(),
        }
