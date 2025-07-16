package br.com.ia.utils;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import com.knuddels.jtokkit.api.ModelType;

import br.com.ia.model.IaResponse;
import br.com.ia.services.PendingIaRequestStore;
import br.com.shared.exception.IAException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class IAUtils {

	private static final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();

	/**
	 * 
	 * @param idChat
	 * @param future
	 * @param pendingStore
	 * @return
	 * @throws IAException
	 */
	public static IaResponse aguardarRespostaIA(String idChat, CompletableFuture<IaResponse> future,
			PendingIaRequestStore pendingStore) throws IAException {
		try {
			return future.get(30, TimeUnit.SECONDS);
		} catch (Exception e) {
			Thread.currentThread().interrupt();
			pendingStore.remove(idChat);
			throw new IAException("Timeout ou erro aguardando resposta da IA: " + e.getMessage());
		}
	}

	public static int contarTokens(String texto) {
		EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
		Encoding secondEnc = registry.getEncodingForModel(ModelType.TEXT_EMBEDDING_ADA_002);
		return secondEnc.countTokens(texto);
	}

	public static BigDecimal custo(int tokens) {
		return BigDecimal.valueOf(tokens).divide(BigDecimal.valueOf(1000)).multiply(BigDecimal.valueOf(0.001));
	}

	/**
	 * Conta o número de tokens de um texto para um modelo específico.
	 * 
	 * @param text  Texto a ser tokenizado
	 * @param model Nome do modelo (ex: gpt-3.5-turbo)
	 * @return Quantidade de tokens
	 */
	public static int countTokens(String text, String model) {
		Encoding encoding = registry.getEncodingForModel(model)
				.orElseGet(() -> registry.getEncoding(EncodingType.CL100K_BASE));
		return encoding.encode(text).size();
	}

	/**
	 * Trunca um texto para que não exceda uma quantidade máxima de tokens.
	 * 
	 * @param text      Texto original
	 * @param maxTokens Máximo de tokens permitidos
	 * @param model     Modelo base para tokenizar
	 * @return Texto truncado dentro do limite
	 */
	public static String truncateToMaxTokens(String text, int maxTokens, String model) {
		Encoding encoding = registry.getEncodingForModel(model)
				.orElseGet(() -> registry.getEncoding(EncodingType.CL100K_BASE));
		IntArrayList tokens = encoding.encode(text);
		if (tokens.size() <= maxTokens) {
			return text;
		}
		// Constrói IntArrayList com os primeiros maxTokens tokens
		IntArrayList truncatedTokens = new IntArrayList(maxTokens);
		for (int i = 0; i < maxTokens; i++) {
			truncatedTokens.add(tokens.get(i));
		}
		return encoding.decode(truncatedTokens);
	}
}
