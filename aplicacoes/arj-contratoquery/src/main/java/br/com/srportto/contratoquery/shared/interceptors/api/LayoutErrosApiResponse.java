package br.com.srportto.contratoquery.shared.interceptors.api;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//?---------------------------------------------------------------------------------------
//? Essa classe eh usada para montar a "cara" das mensagens de erro do projeto para api
//?---------------------------------------------------------------------------------------
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LayoutErrosApiResponse {

	private Instant timestamp;
	private String error;
	private String message;
	private String path;
}
