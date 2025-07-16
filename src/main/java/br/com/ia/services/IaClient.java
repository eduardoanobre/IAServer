package br.com.ia.services;

import org.springframework.stereotype.Service;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.services.client.IAIClient;
import br.com.ia.services.client.IaCallResult;
import br.com.ia.services.client.assitants.OpenAiAssistantsClient;
import br.com.ia.services.client.assitants.OpenAiClient;
import br.com.shared.exception.IAException;
import br.com.shared.model.enums.EnumModeloIA;

@Service
public class IaClient {

	public IaCallResult call(ChatCompletionRequest req) throws IAException {
		EnumModeloIA provider = req.getModeloIA();

		if (provider == null) {
			throw new IllegalArgumentException("provider não informado");
		}

		IAIClient client = null;

		switch (provider) {
		case CHATGPT_GPT_4_TURBO, 
		     CHATGPT_GPT_4_TURBO_ALIAS, 
		     CHATGPT_GPT_4O, 
		     CHATGPT_GPT_3_5_TURBO,
		     CHATGPT_GPT_3_5_TURBO_1106 ->
			client = new OpenAiClient();
		case ASSISTANTS_GPT_4_TURBO, 
		     ASSISTANTS_GPT_4_TURBO_ALIAS, 
		     ASSISTANTS_GPT_4O, 
		     ASSISTANTS_GPT_3_5_TURBO, 
		     ASSISTANTS_GPT_3_5_TURBO_1106 ->
			client = new OpenAiAssistantsClient();
		default -> throw new IllegalArgumentException("IA provider desconhecido: " + provider);
		}

		return client.call(req);
	}

}
