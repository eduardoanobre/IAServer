package br.com.ia.services.ias;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GeminiService {

	public String consultar(String apiKey, String prompt) throws IOException, InterruptedException {
		var mapper = new ObjectMapper();
		var client = HttpClient.newHttpClient();

		var requestBody = Map.of("model", "gpt-4", "messages",
				new Object[] { Map.of("role", "user", "content", prompt) });

		var body = mapper.writeValueAsString(requestBody);

		var request = HttpRequest.newBuilder().uri(URI.create("https://api.openai.com/v1/chat/completions"))
				.timeout(Duration.ofSeconds(60)).header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + apiKey).POST(HttpRequest.BodyPublishers.ofString(body)).build();

		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		var json = mapper.readTree(response.body());

		return json.get("choices").get(0).get("message").get("content").asText();
	}

}
