package br.com.ia.publisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import br.com.ia.messaging.MessageType;
import br.com.ia.utils.ObjectToBytesConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class IaPublisher {

	/**
	 * Template Kafka configurado para publicações com chave e valor em byte[].
	 * Usado por todos os serviços do IAServer para manter consistência.
	 */
	private final KafkaTemplate<byte[], byte[]> kafkaTemplate;

	/**
	 * Tópico Kafka de destino para todas as publicações. Configurável via
	 * propriedades do Spring para diferentes ambientes.
	 */
	@Value("${spring.cloud.stream.bindings.processIaConsumer-in-0.destination:ia.requests}")
	private String topic;

	/**
	 * 
	 * @param tipoMensagem
	 * @param object
	 * @param chatId
	 * @return
	 */
	public CompletableFuture<SendResult<byte[], byte[]>> publish(MessageType tipoMensagem, Object object,
			String chatId) {

		if (object == null) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Payload não pode ser null"));
		}

		if (chatId == null) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("ChatId não pode ser null"));
		}

		if (tipoMensagem == null) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Tipo de mensagem não pode ser null"));
		}

		try {

			// partição (null = automática)
			Integer particao = null;

			ProducerRecord<byte[], byte[]> rec = new ProducerRecord<>(topic, particao,
					chatId.getBytes(StandardCharsets.UTF_8), ObjectToBytesConverter.objectToBytes(object));

			rec.headers().add("message-type", tipoMensagem.name().getBytes(StandardCharsets.UTF_8));

			return kafkaTemplate.send(rec);

		} catch (IOException e) {
			return CompletableFuture.failedFuture(new IllegalArgumentException(e));
		}
	}

}