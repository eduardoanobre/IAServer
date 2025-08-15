package br.com.ia.services;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import br.com.ia.model.IaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumer que recebe respostas da IA e resolve os futures pendentes
 *
 * Este service roda APENAS no IAServer e gerencia toda a comunicação Kafka.
 * Módulos clientes (Workspace, etc) não precisam saber sobre Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IAResponseConsumer {

	private final PendingIaRequestStore pendingStore;

	/**
	 * Consome respostas da IA e resolve os futures pendentes
	 *
	 * @param response   resposta da IA
	 * @param messageKey chave da mensagem Kafka
	 * @param chatId     ID do chat (usado como correlação)
	 */
	@KafkaListener(topics = "ia.responses", groupId = "ia-server-responses")
	public void processIaResponse(@Payload IaResponse response,
			@Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
			@Header(value = "chatId", required = false) String chatId) {

		try {
			// Prioriza chatId do header, depois messageKey, por fim o chatId da resposta
			String correlationId = chatId != null ? chatId : messageKey != null ? messageKey : response.getChatId();

			if (correlationId == null) {
				log.warn("Mensagem sem correlationId - ignorando response: {}", response);
				return;
			}

			log.info("Recebida resposta da IA para chatId: {} - Sucesso: {}", correlationId, response.isSuccess());

			// Resolve o future pendente
			boolean resolved = pendingStore.complete(correlationId, response);

			if (!resolved) {
				log.warn("Nenhum request pendente encontrado para chatId: {} - Response orfa", correlationId);
			} else {
				log.debug("Future resolvido com sucesso para chatId: {}", correlationId);
			}

		} catch (Exception e) {
			log.error("Erro ao processar resposta da IA: {}", e.getMessage(), e);
		}
	}
}