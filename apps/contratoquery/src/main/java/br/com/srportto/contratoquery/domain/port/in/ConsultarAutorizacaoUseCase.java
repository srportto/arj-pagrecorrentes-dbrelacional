package br.com.srportto.contratoquery.domain.port.in;

import br.com.srportto.contratoquery.domain.model.Autorizacao;

import java.util.UUID;

/** Porta de entrada da consulta por id. */
public interface ConsultarAutorizacaoUseCase {

    Autorizacao consultarPorId(UUID autorizacaoId);
}
