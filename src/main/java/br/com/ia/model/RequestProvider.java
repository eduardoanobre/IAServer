package br.com.ia.model;

import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.shared.model.enums.EnumIA;

@Component
public class RequestProvider {

	/**
	 * Constrói o IaRequest que será enviado ao Kafka
	 * 
	 * @param prompt
	 * @param correlationId
	 * @param provider
	 * @param apikey
	 * @param model
	 * @return
	 */
    public IaRequest getRequest(
            String prompt,
            String correlationId,
            EnumIA provider,
            String apikey,
            String model) {

        IaRequest req = new IaRequest();
        req.setCorrelationId(correlationId);
        req.setProvider(provider);
        req.setPrompt(prompt);

        req.setOptions(Map.of(
          "api_key", apikey,
          "model",   model
        ));
        return req;
    }
}
