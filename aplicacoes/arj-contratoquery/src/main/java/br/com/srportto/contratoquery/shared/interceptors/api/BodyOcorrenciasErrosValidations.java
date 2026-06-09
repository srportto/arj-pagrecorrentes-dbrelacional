package br.com.srportto.contratoquery.shared.interceptors.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BodyOcorrenciasErrosValidations {

	private String fieldName;
	private String message;
}
