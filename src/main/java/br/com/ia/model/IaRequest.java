package br.com.ia.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.shared.model.enums.EnumModeloIA;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload para requisição de processamento de IA via Kafka.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IaRequest {

	/** Identificador para correlação de requisição e resposta */
	private String correlationId;

	/** Modelo de IA a ser utilizado (já inclui provedor e nome do modelo) */
	private EnumModeloIA modeloIA;

	/** Prompt de entrada para a chamada de IA */
	private String prompt;

	/** Opções adicionais (ex: temperatura, max_tokens, etc.) */
	private Map<String, Object> options;
}
