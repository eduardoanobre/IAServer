package br.com.ia.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.model.IaRequest;
import br.com.ia.model.IaResponse;
import br.com.ia.model.responses.ResponsesRequest;
import br.com.ia.model.responses.ResponsesRequest.ResponsesRequestBuilder;
import br.com.ia.model.responses.ResponsesResponse;
import br.com.ia.services.client.responses.ResponsesClient;
import br.com.ia.utils.OpenAICustoUtil;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IaProcessor {

	private static final double DEFAULT_TEMP = 0.3;
    private static final String TOP_P = "top_p";
    private static final String CHAT_ID = "chatId";

    private final ResponsesClient responsesClient;
    private final ObjectMapper mapper;

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

                // sampling/limites
                Double temperature = normalizeTemperatureFromOpts(opts);
                Integer maxOutputTokens = opts.containsKey("max_output_tokens")
                        ? Integer.valueOf(String.valueOf(opts.get("max_output_tokens"))) : null;
                Double topP = normalizeTopP(opts);
                Integer topLogprobs = opts.containsKey("top_logprobs")
                        ? Integer.valueOf(String.valueOf(opts.get("top_logprobs"))) : null;

                // núcleo
                String instructions = (String) opts.getOrDefault("instructions", null);
                String model = (String) opts.getOrDefault("model", "gpt-5");

                // estado/stream/store
                Boolean store = opts.containsKey("store") ? Boolean.valueOf(String.valueOf(opts.get("store"))) : null;
                Boolean background = opts.containsKey("background") ? Boolean.valueOf(String.valueOf(opts.get("background"))) : null;
                Boolean stream = opts.containsKey("stream") ? Boolean.valueOf(String.valueOf(opts.get("stream"))) : null;
                Object streamOptionsRaw = opts.get("stream_options");
                String previousResponseId = (String) opts.getOrDefault("previous_response_id", null);

                // controle
                String promptCacheKey = (String) opts.getOrDefault("prompt_cache_key", chatId);
                Object includeRaw = opts.get("include");
                Object reasoningRaw = opts.get("reasoning");
                Object serviceTierRaw = opts.get("service_tier"); // "auto"|"default"|"flex"|"priority"
                Object truncationRaw = opts.get("truncation");     // "auto"|"disabled"
                Object verbosityRaw = opts.get("verbosity");       // "low"|"medium"|"high"

                // ferramentas
                Object toolsRaw = opts.get("tools");
                Object toolChoiceRaw = opts.get("tool_choice");
                Integer maxToolCalls = opts.containsKey("max_tool_calls")
                        ? Integer.valueOf(String.valueOf(opts.get("max_tool_calls"))) : null;
                Boolean parallelToolCalls = opts.containsKey("parallel_tool_calls")
                        ? Boolean.valueOf(String.valueOf(opts.get("parallel_tool_calls"))) : null;

                // structured outputs
                Object textRaw = opts.get("text");

                // ==== CONTEXT SHARDS -> ContentBlocks (estáveis primeiro) ====
                List<ResponsesRequest.ContentBlock> contextBlocks = buildBlocksFromContextShards(opts);

                // ==== Prompt do usuário como último bloco ====
                contextBlocks.add(
                    ResponsesRequest.ContentBlock.builder()
                        .type("input_text")
                        .text(req.getPrompt())
                        .build()
                );

                var input = List.of(
                    ResponsesRequest.InputItem.builder()
                        .role("user")
                        .content(contextBlocks)
                        .build()
                );

                // ==== Monta ResponsesRequest ====
                var builder = ResponsesRequest.builder()
                    .model(model)
                    .instructions(instructions)
                    .input(input)
                    .temperature(temperature)
                    .maxOutputTokens(maxOutputTokens)
                    .metadata(Map.of(CHAT_ID, chatId))
                    .promptCacheKey(promptCacheKey)
                    .safetyIdentifier(chatId)
                    .store(store)
                    .background(background)
                    .stream(stream)
                    .topP(topP)
                    .topLogprobs(topLogprobs)
                    .maxToolCalls(maxToolCalls)
                    .parallelToolCalls(parallelToolCalls)
                    .previousResponseId(previousResponseId);

                if (textRaw != null) {
                    var textOpts = mapper.convertValue(textRaw, ResponsesRequest.TextOptions.class);
                    builder.text(textOpts);
                }

                if (toolsRaw != null) {
                    List<ResponsesRequest.ToolDefinition> tools;
                    if (toolsRaw instanceof List<?>) {
                        tools = mapper.convertValue(
                            toolsRaw,
                            new TypeReference<List<ResponsesRequest.ToolDefinition>>() {}
                        );
                    } else if (toolsRaw instanceof Map<?, ?>) {
                        ResponsesRequest.ToolDefinition one = mapper.convertValue(
                            toolsRaw, ResponsesRequest.ToolDefinition.class
                        );
                        tools = List.of(one);
                    } else {
                        throw new IllegalArgumentException("options.tools deve ser array ou objeto");
                    }
                    builder.tools(tools);
                }

                if (toolChoiceRaw != null) {
                    builder.toolChoice(toolChoiceRaw);
                }

                if (streamOptionsRaw != null) {
                    var so = mapper.convertValue(streamOptionsRaw, ResponsesRequest.StreamOptions.class);
                    builder.streamOptions(so);
                }

                if (includeRaw != null) {
                    @SuppressWarnings("unchecked")
                    var inc = (List<String>) mapper.convertValue(
                        includeRaw,
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class)
                    );
                    builder.include(inc);
                }

                if (reasoningRaw != null) {
                    var reasoning = mapper.convertValue(reasoningRaw, ResponsesRequest.ReasoningOptions.class);
                    builder.reasoning(reasoning);
                }

                if (serviceTierRaw != null) {
                    obterServiceTier(builder, serviceTierRaw);
                }

                obterTruncationRaw(truncationRaw, builder);
                obterVerbosityRaw(verbosityRaw, builder);

                var responsesReq = builder.build();

                // ---- Chama Responses API ----
                ResponsesResponse res = responsesClient.createResponse(apiKey, responsesReq);

                // ---- Extrai saída textual (se aplicável) ----
                String resposta = null;
                if (res.getOutput() != null && !res.getOutput().isEmpty()
                    && res.getOutput().get(0).getContent() != null
                    && !res.getOutput().get(0).getContent().isEmpty()) {
                    var first = res.getOutput().get(0).getContent().get(0);
                    if (first != null) resposta = first.getText();
                }
                if (resposta == null) resposta = "(sem saída textual)";

                // ---- Usage via body ----
                int tokensPrompt = getInt(res.getUsage(), "input_tokens");
                int tokensResposta = getInt(res.getUsage(), "output_tokens");
                BigDecimal custo = OpenAICustoUtil.calcularCustoPorUsage(res.getModel(), tokensPrompt, tokensResposta);

                IaResponse iaResponse = IaResponse.success(
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

                IaResponse erro = IaResponse.error(
                        chatId,
                        "Erro ao processar IA: " + e.getMessage()
                    );

                return MessageBuilder
                    .withPayload(erro)
                    .copyHeaders(message.getHeaders())
                    .setHeader(CHAT_ID, chatId)
                    .build();
            }
        };
    }

	private void obterVerbosityRaw(Object verbosityRaw, ResponsesRequestBuilder builder) {
		if (verbosityRaw != null) {
		    try {
		        var vb = ResponsesRequest.Verbosity.valueOf(String.valueOf(verbosityRaw));
		        builder.verbosity(vb);
		    } catch (IllegalArgumentException ignore) { /* mantém null */ }
		}
	}

	private void obterTruncationRaw(Object truncationRaw, ResponsesRequestBuilder builder) {
		if (truncationRaw != null) {
		    try {
		        var tr = ResponsesRequest.Truncation.valueOf(String.valueOf(truncationRaw));
		        builder.truncation(tr);
		    } catch (IllegalArgumentException ignore) { /* mantém null */ }
		}
	}

    /**
     * Constrói blocos de conteúdo a partir de context_shards em opts.
     * Espera-se uma lista com elementos {type, version, stable, payload}.
     * Ordena: estáveis primeiro, depois por type e version.
     */
    private List<ResponsesRequest.ContentBlock> buildBlocksFromContextShards(Map<String, Object> opts) {
        Object rawShards = opts.get("context_shards");
        if (!(rawShards instanceof List<?> l) || l.isEmpty()) return new ArrayList<>();

        // cast permissivo para List<Map<String,Object>>
        List<Map<String, Object>> shardList = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) m;
                shardList.add(mm);
            }
        }

        // ordena: estáveis primeiro; depois type; depois version
        shardList.sort(
            Comparator
                .<Map<String, Object>, Boolean>comparing(s -> !Boolean.TRUE.equals(s.get("stable"))) // false<true => stable primeiro
                .thenComparing(s -> String.valueOf(s.getOrDefault("type", "")))
                .thenComparingInt(s -> parseIntSafe(s.get("version")))
        );

        List<ResponsesRequest.ContentBlock> blocks = new ArrayList<>();
        for (Map<String, Object> s : shardList) {
            String type = String.valueOf(s.getOrDefault("type", ""));
            int version = parseIntSafe(s.get("version"));
            boolean stable = Boolean.TRUE.equals(s.get("stable"));
            Object payload = s.get("payload");

            String jsonPayload;
            try {
                jsonPayload = mapper.writeValueAsString(payload == null ? Map.of() : payload);
            } catch (Exception e) {
                jsonPayload = "{\"erro\":\"falha ao serializar shard %s\"}".formatted(type);
            }

            String header = "### CTX:%s v%d%s".formatted(type, version, stable ? " (stable)" : "");
            blocks.add(
                ResponsesRequest.ContentBlock.builder()
                    .type("input_text")
                    .text(header + "\n" + jsonPayload)
                    .build()
            );
        }

        return blocks;
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

            // Heurística textual (fallback)
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

    private void obterServiceTier(ResponsesRequestBuilder builder, Object raw) {
        if (raw == null) return;
        String v = String.valueOf(raw).trim().toLowerCase();
        switch (v) {
            case "auto"     -> builder.serviceTier(ResponsesRequest.ServiceTier.auto);
            case "default"  -> builder.serviceTier(ResponsesRequest.ServiceTier._default);
            case "flex"     -> builder.serviceTier(ResponsesRequest.ServiceTier.flex);
            case "priority" -> builder.serviceTier(ResponsesRequest.ServiceTier.priority);
            default         -> { /* mantém null se inválido */ }
        }
    }

    private int getInt(Map<String, Object> usage, String key) {
        if (usage == null || !usage.containsKey(key) || usage.get(key) == null) return 0;
        return Integer.parseInt(String.valueOf(usage.get(key)));
    }

    private static int parseIntSafe(Object v) {
        if (v == null) return 0;
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (Exception e) { return 0; }
    }

    private static double normalizeTemperatureFromOpts(Map<String, Object> opts) {
        final double def = DEFAULT_TEMP;
        if (opts == null) return def;

        Object t = opts.get("temperature");
        if (t == null) return def;

        double v;
        if (t instanceof Number n) {
            v = n.doubleValue();
        } else {
            try {
                v = Double.parseDouble(String.valueOf(t).trim());
            } catch (Exception e) {
                return def; // inválido -> usa default
            }
        }

        if (!Double.isFinite(v)) return def;

        // clamp para 0..100
        v = Math.max(0.0, Math.min(100.0, v));

        // escala linear 0..100 -> 0..2
        double scaled = (v / 100.0) * 2.0;

        // arredonda para 3 casas para estabilidade
        return Math.round(scaled * 1000.0) / 1000.0;
    }
    
    private static Double normalizeTopP(Map<String, Object> opts) {
        if (opts == null || !opts.containsKey(TOP_P) || opts.get(TOP_P) == null) return null;
        double v;
        try { v = Double.parseDouble(String.valueOf(opts.get(TOP_P)).trim()); }
        catch (Exception e) { return null; }
        if (!Double.isFinite(v)) return null;
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        return Math.round(v * 1000.0) / 1000.0;
    }

}
