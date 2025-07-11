package br.com.ia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuração de propriedades para provedores de IA.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ia")
public class IaProperties {
	private ProviderConfig openai;
	private ProviderConfig gemini;

	@Data
	public static class ProviderConfig {
		
		/** URL base da API */
		private String url;
		/** Chave de autenticação (Bearer token) */
		private String key;
		/** Modelo padrão a ser usado */
		private String model;
	}
}