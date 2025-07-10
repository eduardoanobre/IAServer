package br.com.ia.services;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import br.com.shared.services.IService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatgptService implements IService {

	static RestTemplate restTemplate = new RestTemplate();

	@Value("${gpt.api.url}")
	private String apiUrl;

	@Value("${gpt.api.key}")
	private String apiKey;

	public String enviarMensagem(String mensagem) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(apiKey);

		Map<String, Object> requestPayload = new HashMap<>();
		requestPayload.put("model", "gpt-4");
		requestPayload.put("messages", Collections.singletonList(Map.of("role", "user", "content", mensagem)));

		HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);
		ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

		if (response.getStatusCode() == HttpStatus.OK) {
			return response.getBody();
		} else {
			throw new RuntimeException("Erro ao comunicar com a API do ChatGPT: " + response.getStatusCode());
		}
	}

}
