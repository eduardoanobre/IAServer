package br.com.ia.model;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class RequestProvider {

    /**
     * Constrói o IaRequest para envio ao Kafka (Responses API).
     *
     * @param prompt        Texto de entrada para a IA
     * @param chatId        UUID único do chat/conversa (gerado no ERP)
     * @param apiKey        Chave da API (override por requisição)
     * @param additionalOpts Outras opções (ex.: temperature, max_output_tokens, instructions, model, tools, text.response_format, etc.)
     * @return IaRequest completo
     */
    public IaRequest getRequest(
            String prompt,
            String chatId,
            String apiKey,
            Map<String, Object> additionalOpts) {

        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("chatId é obrigatório.");
        }
        if (prompt == null) {
            prompt = "";
        }

        // Monta options (com defaults sensatos para Responses API)
        Map<String, Object> options = new HashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            options.put("api_key", apiKey);
        }

        // Defaults úteis (podem ser sobrescritos por additionalOpts)
        options.put("model", "gpt-5");
        options.put("prompt_cache_key", chatId);
        options.put("safety_identifier", chatId);

        // metadata.chatId para telemetria/observabilidade no objeto da OpenAI
        Map<String, String> metadata = new HashMap<>();
        metadata.put("chatId", chatId);
        options.put("metadata", metadata);

        // Mescla opções externas (sobrescrevem os defaults acima, se presentes)
        if (additionalOpts != null && !additionalOpts.isEmpty()) {
            options.putAll(additionalOpts);
        }

        // Monta o IaRequest final
        IaRequest req = new IaRequest();
        req.setChatId(chatId);
        req.setPrompt(prompt);
        req.setOptions(options);

        return req;
    }

}
