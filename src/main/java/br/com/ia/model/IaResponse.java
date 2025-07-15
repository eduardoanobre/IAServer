package br.com.ia.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload com o resultado do processamento de IA via Kafka.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IaResponse {
	
	/** Mesmo correlationId da requisição */
	private String correlationId;

	/** Conteúdo retornado pela IA */
	private String resposta;
}
