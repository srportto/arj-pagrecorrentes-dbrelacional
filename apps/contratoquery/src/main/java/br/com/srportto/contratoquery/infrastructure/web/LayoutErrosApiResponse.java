package br.com.srportto.contratoquery.infrastructure.web;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
