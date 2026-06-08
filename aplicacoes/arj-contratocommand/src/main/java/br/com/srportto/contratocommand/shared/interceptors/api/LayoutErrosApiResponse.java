package br.com.srportto.contratocommand.shared.interceptors.api;

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

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
