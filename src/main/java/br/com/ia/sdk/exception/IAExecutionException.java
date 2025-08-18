package br.com.ia.sdk.exception;

/**
 * Exception específica para erros na execução de prompts IA
 */
public class IAExecutionException extends RuntimeException {

	private static final long serialVersionUID = -3921481241564056954L;

	public IAExecutionException(String message) {
		super(message);
	}

	public IAExecutionException(String message, Throwable cause) {
		super(message, cause);
	}

	public IAExecutionException(Throwable cause) {
		super(cause);
	}
}