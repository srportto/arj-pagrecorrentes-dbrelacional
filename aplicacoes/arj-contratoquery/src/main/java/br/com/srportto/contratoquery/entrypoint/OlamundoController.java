package br.com.srportto.contratoquery.entrypoint;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.srportto.contratoquery.application.olamundo.OlamundoService;
import lombok.AllArgsConstructor;

/**
 * Adaptador de entrada (REST). Expoe a rota de disponibilidade da aplicacao.
 */
@RestController
@AllArgsConstructor
public class OlamundoController {

	private final OlamundoService olamundoService;

	@GetMapping("/olamundo")
	public ResponseEntity<String> olamundo() {
		return ResponseEntity.ok(olamundoService.obterSaudacao());
	}
}
