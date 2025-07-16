package br.com.ia.services.client;

import org.springframework.http.HttpHeaders;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IaCallResult {

	private final String resposta;
	private final HttpHeaders headers;
	private final String modelo;
	private final String runId;
	
}
