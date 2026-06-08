package br.com.srportto.contratocommand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContratocommandApplication {


	//Experimental do java 25, comentado para manter compatibilidade com maven plugin, que não suporta java 25 ainda.
	// void main(String[] args) {
	// 	SpringApplication.run(ContratocommandApplication.class, args);
	// }

	public static void main(String[] args) {
		SpringApplication.run(ContratocommandApplication.class, args);
	}

}
