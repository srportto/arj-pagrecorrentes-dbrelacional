package br.com.srportto.contratoquery.infrastructure.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record LayoutErrosApiValidationsResponse(
		Instant timestamp,
		String error,
		String message,
		String path,
		List<BodyOcorrenciasErrosValidations> occurrences) {

	public LayoutErrosApiValidationsResponse {
		occurrences = occurrences == null ? new ArrayList<>() : new ArrayList<>(occurrences);
	}

	public LayoutErrosApiValidationsResponse(Instant timestamp, String error, String message, String path) {
		this(timestamp, error, message, path, new ArrayList<>());
	}

	public LayoutErrosApiValidationsResponse addOccurrence(String fieldName, String message) {
		this.occurrences.add(new BodyOcorrenciasErrosValidations(fieldName, message));
		return this;
	}
}
