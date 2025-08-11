package br.com.ia.services.client.responses;

import br.com.ia.model.responses.ResponsesRequest;
import br.com.ia.model.responses.ResponsesResponse;
import br.com.shared.exception.IAException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ResponsesClient {

  private static final String BASE_URL = "https://api.openai.com/v1";

  private final WebClient.Builder builder;

  private WebClient client(String apiKey) {
    return builder
        .baseUrl(BASE_URL)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  /** Chamada síncrona (sem streaming) */
  public ResponsesResponse createResponse(String apiKey, ResponsesRequest req) throws IAException {
    try {
      if (req.getModel() == null) req.setModel("gpt-5");
      return client(apiKey)
          .post()
          .uri("/responses")
          .bodyValue(req)
          .retrieve()
          .bodyToMono(ResponsesResponse.class)
          .block();
    } catch (Exception e) {
      throw new IAException("Falha ao chamar Responses API: " + e.getMessage(), e);
    }
  }

  /** Streaming SSE semântico (se quiser usar) */
  public Flux<String> streamResponse(String apiKey, ResponsesRequest req) {
    if (req.getModel() == null) req.setModel("gpt-5");
    req.setStream(true);
    return client(apiKey)
        .post()
        .uri("/responses")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(req)
        .retrieve()
        .bodyToFlux(String.class);
  }

  /** Helper para consumir streaming de forma simples */
  public void streamResponse(String apiKey, ResponsesRequest req, Consumer<String> onEvent) {
    streamResponse(apiKey, req).subscribe(onEvent);
  }
}
