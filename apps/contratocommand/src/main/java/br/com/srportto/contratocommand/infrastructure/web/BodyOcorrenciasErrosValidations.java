package br.com.srportto.contratocommand.infrastructure.web;

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
