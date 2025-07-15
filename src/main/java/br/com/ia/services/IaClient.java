package br.com.ia.services;

import org.springframework.stereotype.Service;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.services.client.IAIClient;
import br.com.ia.services.client.assitants.OpenAiAssistantsClient;
import br.com.ia.services.client.assitants.OpenAiClient;
import br.com.shared.model.enums.EnumIA;

@Service
public class IaClient {

	public String call(ChatCompletionRequest req) {
		EnumIA provider = req.getProvider();
		
		if (provider == null) {
			throw new IllegalArgumentException("provider não informado");
		}
		
		IAIClient client = null;
		
		switch (provider) {
		case CHATGPT -> client = new OpenAiClient();
		case CHATGPT_ASSISTANTS -> client = new OpenAiAssistantsClient();
		default -> throw new IllegalArgumentException("IA provider desconhecido: " + provider);
		}
		
		return client.call(req);
	}

}
