package br.com.srportto.contratoquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContratoqueryApplication {

	// Experimental do java 25, comentado para manter compatibilidade com o maven
	// plugin, que ainda nao suporta java 25.
	// void main(String[] args) {
	// 	SpringApplication.run(ContratoqueryApplication.class, args);
	// }

	public static void main(String[] args) {
		SpringApplication.run(ContratoqueryApplication.class, args);
	}

}
