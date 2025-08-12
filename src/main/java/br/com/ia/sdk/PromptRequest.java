package br.com.ia.sdk;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptRequest {

	
	private String chatId; // obrigatório (UUID do projeto)
	private String prompt; // obrigatório (mensagem do usuário)
	private String instructions; // system message (regras do módulo)
	private Map<String, Object> text; // Structured Outputs (json_schema + strict)
	private String model;
	private String apiKey; // chave do provedor
	private Double temperaturePercent; 
	private Integer maxOutputTokens; 
	private Integer versaoInstrucao; 
	private Integer versaoSchema;
}
