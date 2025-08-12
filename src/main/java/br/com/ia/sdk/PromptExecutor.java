package br.com.ia.sdk;

import br.com.ia.model.IaResponse;
import br.com.shared.exception.IAException;

public interface PromptExecutor {
	IaResponse executaPrompt(PromptRequest request) throws IAException;
}
