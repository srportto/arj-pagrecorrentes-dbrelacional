package br.com.srportto.contratoquery.infrastructure.web;

import java.time.Instant;

public record LayoutErrosApiResponse(
		Instant timestamp,
		String error,
		String message,
		String path) {
}
