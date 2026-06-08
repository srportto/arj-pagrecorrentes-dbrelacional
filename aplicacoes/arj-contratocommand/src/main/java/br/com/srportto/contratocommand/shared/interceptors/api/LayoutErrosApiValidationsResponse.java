package br.com.srportto.contratocommand.shared.interceptors.api;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LayoutErrosApiValidationsResponse extends LayoutErrosApiResponse {

    private List<BodyOcorrenciasErrosValidations> occurrences = new ArrayList<>();

    public void addOccurrences(String fieldName, String message) {
        occurrences.add(new BodyOcorrenciasErrosValidations(fieldName, message));
    }
}
