package br.com.ia.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import br.com.ia.model.IaRequest;
import br.com.ia.model.IaResponse;
import br.com.ia.model.responses.ResponsesRequest;
import br.com.ia.model.responses.ResponsesResponse;
import br.com.ia.services.client.responses.ResponsesClient;
import br.com.ia.utils.OpenAICustoUtil;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IaProcessor {

  private static final String CHAT_ID = "chatId";
  private final ResponsesClient responsesClient;

  @Bean
  public Function<Message<IaRequest>, Message<IaResponse>> processIa() { // NOSONAR
    return message -> {
      IaRequest req = message.getPayload();

      // ==== chatId (único identificador do fluxo) ====
      String chatId = message.getHeaders().get(CHAT_ID, String.class);
      if (chatId == null && req != null && req.getChatId() != null) {
        chatId = req.getChatId();
      }
      
      if (chatId == null) {
        throw new IllegalArgumentException("chatId ausente no header/payload.");
      }

      try {
        if (req == null) throw new IllegalArgumentException("IaRequest nulo.");

        // ---- Opções vindas do ERP ----
        Map<String, Object> opts = req.getOptions() != null ? req.getOptions() : Map.of();
        
        String apiKey = (String) opts.getOrDefault("api_key", null);
        if (apiKey == null) throw new IllegalArgumentException("api_key ausente nas opções.");
        
        Double temperature = opts.containsKey("temperature") ? Double.valueOf(String.valueOf(opts.get("temperature"))) : null;
        Integer maxOutputTokens = opts.containsKey("max_output_tokens") ? Integer.valueOf(String.valueOf(opts.get("max_output_tokens"))) : null;
        String instructions = (String) opts.getOrDefault("instructions", null);
        String model = (String) opts.getOrDefault("model", "gpt-5"); // padrão

        // ---- Monta ResponsesRequest (JSON schema vem do ERP) ----
        var input = List.of(
          ResponsesRequest.InputItem.builder()
            .role("user")
            .content(List.of(
              ResponsesRequest.ContentBlock.builder()
                .type("input_text")
                .text(req.getPrompt())
                .build()
            ))
            .build()
        );

        var responsesReq = ResponsesRequest.builder()
          .model(model)
          .instructions(instructions)
          .input(input)
          .temperature(temperature)
          .maxOutputTokens(maxOutputTokens)
          .metadata(Map.of(CHAT_ID, chatId))
          .promptCacheKey(chatId)
          .safetyIdentifier(chatId)
          .build();

        // ---- Chama Responses API ----
        ResponsesResponse res = responsesClient.createResponse(apiKey, responsesReq);

        // ---- Extrai saída textual (se aplicável) ----
        String resposta = null;
        if (res.getOutput() != null && !res.getOutput().isEmpty()
            && res.getOutput().get(0).getContent() != null && !res.getOutput().get(0).getContent().isEmpty()) {
          var first = res.getOutput().get(0).getContent().get(0);
          if (first != null) resposta = first.getText();
        }
        if (resposta == null) resposta = "(sem saída textual)";

        // ---- Usage via body ----
        int tokensPrompt = getInt(res.getUsage(), "input_tokens");
        int tokensResposta = getInt(res.getUsage(), "output_tokens");
        BigDecimal custo = OpenAICustoUtil.calcularCustoPorUsage(res.getModel(), tokensPrompt, tokensResposta);

        IaResponse iaResponse = new IaResponse(
          chatId,
          resposta,
          custo,
          res.getModel(),
          tokensPrompt,
          tokensResposta
        );

        return MessageBuilder
          .withPayload(iaResponse)
          .copyHeaders(message.getHeaders())
          .setHeader(CHAT_ID, chatId) 
          .build();

      } catch (Exception e) {
        // === Política de retry/DLT ===
        if (isTransient(e)) {
          throw new IllegalStateException("Falha transitória ao chamar/processar IA", e);
        }

        IaResponse erro = new IaResponse(
          chatId,
          "Erro ao processar IA: " + e.getMessage(),
          BigDecimal.ZERO,
          "erro",
          0,
          0
        );

        return MessageBuilder
          .withPayload(erro)
          .copyHeaders(message.getHeaders())
          .setHeader(CHAT_ID, chatId)
          .build();
      }
    };
  }

  private int getInt(Map<String, Object> usage, String key) {
    if (usage == null || !usage.containsKey(key) || usage.get(key) == null) return 0;
    return Integer.parseInt(String.valueOf(usage.get(key)));
  }

    /**
     * Heurística simples para distinguir falhas transitórias (reprocessar) de falhas permanentes.
     * Ajuste conforme seu cliente/provedor real.
     */
    private boolean isTransient(Throwable t) { // NOSONAR
        // Percorre a cadeia de causas
        Throwable e = t;
        while (e != null) {
            // Rede/IO/timeout comuns
            if (e instanceof java.io.IOException) return true;
            if (e instanceof java.net.ConnectException) return true;
            if (e instanceof java.net.SocketTimeoutException) return true;
            if (e instanceof java.util.concurrent.TimeoutException) return true;

            // Erros HTTP do Spring (ex.: RestTemplate) -> 429/5xx são transitórios
            if (e instanceof HttpStatusCodeException httpEx) {
                int code = httpEx.getStatusCode().value();
                if (code == 429 || (code >= 500 && code < 600)) return true;
            }

            // Heurística textual (fallback) — útil quando o tipo não está disponível
            String msg = e.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("timeout") || m.contains("timed out")
                        || m.contains("temporarily unavailable")
                        || m.contains("rate limit")
                        || m.contains("429")
                        || m.matches(".*\\b5\\d\\d\\b.*")) {
                    return true;
                }
            }

            e = e.getCause();
        }
        return false;
    }
}
