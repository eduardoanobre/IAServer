package br.com.ia.services;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import br.com.ia.model.ChatCompletionRequest;

@Service
@Qualifier("router")
public class IaRouterClient implements IAClient {

	private final Map<String, IAClient> providers;

	public IaRouterClient(
		@Qualifier("openai") IAClient openAiClient,
		@Qualifier("assistants") IAClient assistantsClient
	) {
		this.providers = Map.of(
			"openai", openAiClient,
			"assistants", assistantsClient
		);
	}

	@Override
	public String call(ChatCompletionRequest req) {
		String provider = req.getProvider();
		if (provider == null) throw new IllegalArgumentException("provider não informado");

		IAClient client = providers.get(provider.toLowerCase());
		if (client == null) throw new IllegalArgumentException("IA provider desconhecido: " + provider);

		return client.call(req);
	}
}
