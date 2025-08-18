package br.com.ia.services.client.responses;

import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import br.com.ia.model.responses.ResponsesRequest;
import br.com.ia.model.responses.ResponsesResponse;
import br.com.ia.sdk.exception.IAExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
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
  public ResponsesResponse createResponse(String apiKey, ResponsesRequest req) throws IAExecutionException {
	  log.info(req.getModel());
    try {
      return client(apiKey)
          .post()
          .uri("/responses")
          .bodyValue(req)
          .retrieve()
          .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), r ->
	          r.bodyToMono(String.class).flatMap(body -> {
	            log.error("OpenAI error {} body={}", r.statusCode(), body);
	            return reactor.core.publisher.Mono.error(
	              new RuntimeException("Responses API error: " + body));
	          })
      )
          .bodyToMono(ResponsesResponse.class)
          .block();
    } catch (Exception e) {
      throw new IAExecutionException("Falha ao chamar Responses API: " + e.getMessage(), e);
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
