package br.com.ia.services.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import br.com.ia.model.ChatCompletionRequest;
import br.com.ia.services.IAClient;
import br.com.ia.services.OpenAiAssistantsClient;

@Service
@Qualifier("assistants")
public class OpenAiAssistantsClientAdapter implements IAClient {

	private final OpenAiAssistantsClient assistantsClient;

	public OpenAiAssistantsClientAdapter(OpenAiAssistantsClient assistantsClient) {
		this.assistantsClient = assistantsClient;
	}

	@Override
	public String call(ChatCompletionRequest req) {
		String apiKey = req.getApiKey();
		String assistantId = req.getAssistantId(); // precisa vir no request
		String prompt = req.getMessages().get(req.getMessages().size() - 1).getContent();

		// Cria thread (ou futura lógica para reutilizar via idChat)
		String threadId = assistantsClient.createThread(apiKey);

		// Cria run
		String runId = assistantsClient.createRun(threadId, assistantId, prompt, apiKey);

		// Poll até a conclusão
		for (int i = 0; i < 20; i++) {
			String status = assistantsClient.getRunStatus(threadId, runId, apiKey);
			if ("completed".equals(status)) break;
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// Retorna a última resposta
		return assistantsClient.getLastResponse(threadId, apiKey);
	}
}
