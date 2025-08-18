package br.com.ia.sdk;

import br.com.ia.sdk.exception.IAExecutionException;

public interface PromptExecutor {
	boolean executaPrompt(PromptRequest request) throws IAExecutionException;
}
