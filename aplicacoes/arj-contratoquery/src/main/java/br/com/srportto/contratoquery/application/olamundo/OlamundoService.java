package br.com.srportto.contratoquery.application.olamundo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.srportto.contratoquery.domain.model.SaudacaoOlamundo;

/**
 * Camada de aplicacao: orquestra o caso de uso "ola mundo".
 * Recupera o nome da aplicacao a partir da configuracao Spring e delega a
 * montagem da mensagem para o modelo de dominio.
 */
@Service
public class OlamundoService {

	private final String nomeAplicacao;

	public OlamundoService(@Value("${spring.application.name}") String nomeAplicacao) {
		this.nomeAplicacao = nomeAplicacao;
	}

	public String obterSaudacao() {
		return new SaudacaoOlamundo(nomeAplicacao).montarMensagem();
	}
}
