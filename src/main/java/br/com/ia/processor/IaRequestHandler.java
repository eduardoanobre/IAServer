package br.com.ia.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.model.IaRequest;
import br.com.ia.model.IaResponse;
import br.com.ia.model.responses.ResponsesRequest;
import br.com.ia.model.responses.ResponsesResponse;
import br.com.ia.services.client.responses.ResponsesClient;
import br.com.ia.utils.OpenAICustoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IaRequestHandler {

    private static final double DEFAULT_TEMP = 0.3;
    private static final String CHAT_ID = "chatId";

    private final ResponsesClient responsesClient;
    private final ObjectMapper mapper;

    public IaResponse handle(Map<String, Object> requestData) {
        IaRequest req = mapper.convertValue(requestData, IaRequest.class);

        String chatId = req.getChatId();
        if (chatId == null) throw new IllegalArgumentException("chatId absent in request.");
        if (chatId.startsWith("test-")) {
            log.info("[IA-HANDLER] Ignoring test chatId: {}", chatId);
            return null;
        }

        try {
            Map<String, Object> opts = req.getOptions() != null ? req.getOptions() : Map.of();

            String apiKey = (String) opts.getOrDefault("api_key", null);
            if (apiKey == null) throw new IllegalArgumentException("api_key absent in options.");

            Double temperature = normalizeTemperature(opts);
            Integer maxOutputTokens = opts.containsKey("max_output_tokens")
                ? Integer.valueOf(String.valueOf(opts.get("max_output_tokens")))
                : null;
            String instructions = (String) opts.getOrDefault("instructions", null);
            String model = (String) opts.getOrDefault("model", "gpt-4");

            List<ResponsesRequest.ContentBlock> blocks = buildBlocksFromContextShards(opts);
            blocks.add(ResponsesRequest.ContentBlock.builder()
                .type("input_text").text(req.getPrompt()).build());

            var input = List.of(ResponsesRequest.InputItem.builder()
                .role("user").content(blocks).build());

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

            log.info("[IA-HANDLER] Calling external AI API");
            ResponsesResponse res = responsesClient.createResponse(apiKey, responsesReq);

            String resposta = extractText(res);
            int tokensPrompt = getInt(res.getUsage(), "input_tokens");
            int tokensResposta = getInt(res.getUsage(), "output_tokens");
            BigDecimal custo = OpenAICustoUtil.calcularCustoPorUsage(res.getModel(), tokensPrompt, tokensResposta);

            return IaResponse.success(chatId, resposta, custo, res.getModel(), tokensPrompt, tokensResposta);

        } catch (Exception e) {
            log.error("[IA-HANDLER] Error processing {}: {}", req.getChatId(), e.getMessage(), e);

            if (isTransient(e)) {
                throw new IllegalStateException("Transient failure when calling/processing IA", e);
            }
            return IaResponse.error(req.getChatId(), "Error processing IA: " + e.getMessage());
        }
    }

    // -----------------
    // Helpers internos
    // -----------------
    private static double normalizeTemperature(Map<String, Object> opts) {
        Object t = opts.get("temperature");
        if (t == null) return DEFAULT_TEMP;

        double v;
        if (t instanceof Number n) v = n.doubleValue();
        else {
            try { v = Double.parseDouble(String.valueOf(t).trim()); }
            catch (Exception e) { return DEFAULT_TEMP; }
        }

        if (!Double.isFinite(v)) return DEFAULT_TEMP;
        v = Math.max(0.0, Math.min(100.0, v));
        double scaled = (v / 100.0) * 2.0;
        return Math.round(scaled * 1000.0) / 1000.0;
    }

    private static boolean isTransient(Throwable t) {
        Throwable e = t;
        while (e != null) {
            if (e instanceof java.io.IOException
             || e instanceof java.net.ConnectException
             || e instanceof java.net.SocketTimeoutException
             || e instanceof java.util.concurrent.TimeoutException) return true;

            if (e instanceof HttpStatusCodeException httpEx) {
                int code = httpEx.getStatusCode().value();
                if (code == 429 || (code >= 500 && code < 600)) return true;
            }

            String msg = e.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("timeout") || m.contains("rate limit") || m.contains("temporarily unavailable")
                 || m.contains("429") || m.matches(".*\\b5\\d\\d\\b.*")) return true;
            }
            e = e.getCause();
        }
        return false;
    }

    private static int getInt(Map<String, Object> usage, String key) {
        if (usage == null || !usage.containsKey(key) || usage.get(key) == null) return 0;
        return Integer.parseInt(String.valueOf(usage.get(key)));
    }

    private static String extractText(ResponsesResponse res) {
        if (res.getOutput() != null && !res.getOutput().isEmpty()
            && res.getOutput().get(0).getContent() != null
            && !res.getOutput().get(0).getContent().isEmpty()
            && res.getOutput().get(0).getContent().get(0) != null) {
            var first = res.getOutput().get(0).getContent().get(0);
            return first.getText() != null ? first.getText() : "(no textual output)";
        }
        return "(no textual output)";
    }

    private List<ResponsesRequest.ContentBlock> buildBlocksFromContextShards(Map<String, Object> opts) {
        Object rawShards = opts.get("context_shards");
        if (!(rawShards instanceof List<?> l) || l.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> shardList = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) m;
                shardList.add(mm);
            }
        }

        shardList.sort(
            Comparator.<Map<String, Object>, Boolean>comparing(s -> !Boolean.TRUE.equals(s.get("stable")))
                .thenComparing(s -> String.valueOf(s.getOrDefault("type", "")))
                .thenComparingInt(s -> parseIntSafe(s.get("version")))
        );

        List<ResponsesRequest.ContentBlock> blocks = new ArrayList<>();
        for (Map<String, Object> s : shardList) {
            String type = String.valueOf(s.getOrDefault("type", ""));
            int version = parseIntSafe(s.get("version"));
            boolean stable = Boolean.TRUE.equals(s.get("stable"));
            Object payload = s.get("payload");

            String header = "### CTX:%s v%d%s".formatted(type, version, stable ? " (stable)" : "");
            String jsonPayload;
            try {
                jsonPayload = mapper.writeValueAsString(payload == null ? Map.of() : payload);
            } catch (Exception e) {
                jsonPayload = "{\"error\":\"failed to serialize shard %s\"}".formatted(type);
            }

            blocks.add(ResponsesRequest.ContentBlock.builder()
                .type("input_text")
                .text(header + "\n" + jsonPayload)
                .build());
        }
        return blocks;
    }

    private static int parseIntSafe(Object v) {
        if (v == null) return 0;
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (Exception e) { return 0; }
    }
}
