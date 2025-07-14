package br.com.ia.services;

public interface OpenAiAssistantsClient {

	String createThread(String apiKey);

	String createRun(String threadId, String assistantId, String prompt, String apiKey);

	String getRunStatus(String threadId, String runId, String apiKey);

	String getLastResponse(String threadId, String apiKey);
}

