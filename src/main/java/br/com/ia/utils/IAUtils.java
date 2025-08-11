package br.com.ia.utils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
     * Aguarda a resposta da IA com timeout default de 30s.
     */
    public static IaResponse aguardarRespostaIA(
            String idChat,
            CompletableFuture<IaResponse> future,
            PendingIaRequestStore pendingStore) throws IAException {
        return aguardarRespostaIA(idChat, future, pendingStore, Duration.ofSeconds(30), true);
    }

    /**
     * Aguarda a resposta da IA com timeout configurável (em ms).
     */
    public static IaResponse aguardarRespostaIA(
            String idChat,
            CompletableFuture<IaResponse> future,
            PendingIaRequestStore pendingStore,
            long timeoutMs,
            boolean removeOnTimeout) throws IAException {
        return aguardarRespostaIA(idChat, future, pendingStore, Duration.ofMillis(timeoutMs), removeOnTimeout);
    }

    /**
     * Aguarda a resposta da IA com timeout configurável (Duration) e controle de limpeza em timeout.
     *
     * @param idChat          identificador da sessão/chat
     * @param future          future registrado no PendingIaRequestStore
     * @param pendingStore    store para completar/falhar/limpar pendências
     * @param timeout         duração máxima de espera
     * @param removeOnTimeout se true, marca falha e remove a pendência ao estourar o timeout
     */
    public static IaResponse aguardarRespostaIA(
            String idChat,
            CompletableFuture<IaResponse> future,
            PendingIaRequestStore pendingStore,
            Duration timeout,
            boolean removeOnTimeout) throws IAException {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            if (removeOnTimeout) {
                // completa excepcionalmente e remove do store
                pendingStore.fail(idChat, te);
            }
            throw new IAException("Timeout aguardando resposta da IA após " + timeout.toSeconds() + "s", te);
        } catch (ExecutionException ee) {
            Throwable cause = (ee.getCause() != null) ? ee.getCause() : ee;
            pendingStore.fail(idChat, cause);
            throw new IAException("Falha ao executar IA: " + cause.getMessage(), cause);
        } catch (InterruptedException ie) {
            // respeita a interrupção da thread
            Thread.currentThread().interrupt();
            pendingStore.fail(idChat, ie);
            throw new IAException("Thread interrompida enquanto aguardava a IA", ie);
        } catch (Exception e) {
            // fallback: qualquer outra falha inesperada
            pendingStore.fail(idChat, e);
            throw new IAException("Erro aguardando resposta da IA: " + e.getMessage(), e);
        }
    }

    // ===== utilitários de tokens/custo (mantidos) =====

    public static int contarTokens(String texto) {
        Encoding secondEnc = registry.getEncodingForModel(ModelType.TEXT_EMBEDDING_ADA_002);
        return secondEnc.countTokens(texto);
    }

    public static BigDecimal custo(int tokens) {
        return BigDecimal.valueOf(tokens)
                .divide(BigDecimal.valueOf(1000))
                .multiply(BigDecimal.valueOf(0.001));
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
        IntArrayList truncatedTokens = new IntArrayList(maxTokens);
        for (int i = 0; i < maxTokens; i++) {
            truncatedTokens.add(tokens.get(i));
        }
        return encoding.decode(truncatedTokens);
    }
}
