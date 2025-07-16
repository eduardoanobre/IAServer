package br.com.ia.model;

import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.shared.model.enums.EnumModeloIA;

@Component
public class RequestProvider {

    /**
     * Constrói o IaRequest que será enviado ao Kafka.
     *
     * @param prompt         Texto de entrada para a IA
     * @param correlationId  ID para rastreamento
     * @param modelo         Modelo de IA a ser utilizado (EnumModeloIA)
     * @param apiKey         Chave da API
     * @param additionalOpts Outras opções como temperature, max_tokens, etc.
     * @return IaRequest completo
     */
    public IaRequest getRequest(
            String prompt,
            String correlationId,
            EnumModeloIA modelo,
            String apiKey,
            Map<String, Object> additionalOpts) {

        // Monta o mapa de options com api_key e qualquer extra que vier
        Map<String, Object> options = new java.util.HashMap<>();
        if (apiKey != null) options.put("api_key", apiKey);
        if (additionalOpts != null) options.putAll(additionalOpts);

        IaRequest req = new IaRequest();
        req.setCorrelationId(correlationId);
        req.setModeloIA(modelo);
        req.setPrompt(prompt);
        req.setOptions(options);

        return req;
    }
}
