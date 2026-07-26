package br.com.srportto.eventosconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventosConsumerApplication {

	// TODO: migrar para void main() (Java 25) quando o maven plugin suportar.
	public static void main(String[] args) {
		SpringApplication.run(EventosConsumerApplication.class, args);
	}

}
