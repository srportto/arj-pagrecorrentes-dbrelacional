package br.com.srportto.commons.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("Testes do ApplicationException")
class ApplicationExceptionTest {

	@Test
	@DisplayName("construtor de 1 argumento preserva a mensagem e nao tem causa")
	void preservaMensagemSemCausa() {
		var excecao = new ApplicationException("erro inesperado de aplicacao");

		assertEquals("erro inesperado de aplicacao", excecao.getMessage());
		assertNull(excecao.getCause());
	}

	@Test
	@DisplayName("construtor de 2 argumentos preserva a mensagem e a causa")
	void preservaMensagemECausa() {
		var causa = new IllegalStateException("falha tecnica");

		var excecao = new ApplicationException("erro inesperado de aplicacao", causa);

		assertEquals("erro inesperado de aplicacao", excecao.getMessage());
		assertSame(causa, excecao.getCause());
	}

}
