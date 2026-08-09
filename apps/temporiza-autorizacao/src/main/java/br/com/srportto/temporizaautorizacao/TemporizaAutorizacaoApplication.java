package br.com.srportto.temporizaautorizacao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TemporizaAutorizacaoApplication {

	// TODO: migrar para void main() (Java 25) quando o maven plugin suportar.
	public static void main(String[] args) {
		SpringApplication.run(TemporizaAutorizacaoApplication.class, args);
	}

}
