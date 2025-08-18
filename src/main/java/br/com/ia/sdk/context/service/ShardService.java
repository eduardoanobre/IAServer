package br.com.ia.sdk.context.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.sdk.context.BasicContextShard;
import br.com.ia.sdk.context.ContextShard;
import br.com.ia.sdk.context.ContextShards;
import br.com.ia.sdk.context.entity.Shard;
import br.com.ia.sdk.context.repository.ShardRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ShardService {

	private final ShardRepository repository;
	private final ObjectMapper mapper;

	@Transactional
	public void upsert(String chatId, ContextShard shard) {
		try {
			String json = mapper.writeValueAsString(shard.payload());
			// upsert por (chatId, type, version)
			Optional<Shard> existing = repository.findTopByChatIdAndShardTypeOrderByVersionDesc(chatId, shard.type())
					.filter(e -> e.getVersion() == shard.version());

			Shard e = existing.orElseGet(Shard::new);
			e.setChatId(chatId);
			e.setShardType(shard.type());
			e.setVersion(shard.version());
			e.setStable(shard.stable());
			e.setPayloadJson(json);

			repository.save(e);
		} catch (Exception ex) {
			throw new IllegalStateException("Falha ao persistir shard " + shard.type(), ex);
		}
	}

	public List<ContextShard> findByTypes(String chatId, List<String> types) {
		if (types == null || types.isEmpty())
			return List.of();
		return repository.findByChatIdAndShardTypeIn(chatId, types).stream().map(this::toShard).toList();
	}

	public List<ContextShard> findAll(String chatId) {
		return repository.findByChatId(chatId).stream().map(this::toShard).toList();
	}

	/**
	 * Converte shards -> estrutura esperada no opts["context_shards"] do
	 * IAProcessor
	 */
	public List<Map<String, Object>> toOptionPayload(List<ContextShard> shards) {
		// IAProcessor ordena estáveis primeiro, mas já podemos manter consistente aqui
		return shards.stream()
				.sorted(Comparator.comparing((ContextShard s) -> !s.stable()).thenComparing(ContextShard::type)
						.thenComparingInt(ContextShard::version))
				.map(s -> Map.<String, Object>of("type", s.type(), "version", s.version(), "stable", s.stable(),
						"payload", s.payload()))
				.toList();
	}

	private ContextShard toShard(Shard e) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> payload = mapper.readValue(e.getPayloadJson(), Map.class);
			return new BasicContextShard(e.getShardType(), e.getVersion(), e.isStable(), payload);
		} catch (Exception ex) {
			return new BasicContextShard(e.getShardType(), e.getVersion(), e.isStable(),
					Map.of("erro", "falha ao desserializar payload_json"));
		}
	}

	/**
	 * Carrega os shards mais recentes para cada tipo solicitado e converte para
	 * {@link Shard} efêmero (pronto para envio).
	 */
	@Transactional(readOnly = true)
	public List<ContextShard> loadShards(String chatId, String... types) {
		var out = new ArrayList<ContextShard>();
		for (String t : types) {
			findLatest(chatId, t).ifPresent(e -> out
					.add(ContextShards.ephemeral(e.getShardType(), e.getVersion(), e.isStable(), payloadMap(e))));
		}
		return out;
	}

	public Optional<Shard> findLatest(String chatId, String type) {
		return repository.findTopByChatIdAndShardTypeOrderByVersionDescIdDesc(chatId, type);
	}

	/** true se existe pelo menos um shard daquele tipo para o chat. */
	@Transactional(readOnly = true)
	public boolean exists(String chatId, String type) {
		return findLatest(chatId, type).isPresent();
	}

	private Map<String, Object> payloadMap(Shard e) {
		String json = e.getPayloadJson();
		if (json == null || json.isBlank())
			return Map.of();
		try {
			return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
			});
		} catch (Exception ex) {
			return Map.of("_raw", json, "_parse_error", ex.getMessage());
		}
	}

}
