// br.com.ia.sdk.context.ContextShardService.java
package br.com.ia.sdk.context;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ContextShardService {

	private final ContextShardRepository repo;
	private final ObjectMapper mapper;

	@Transactional
	public void upsert(String chatId, ContextShard shard) {
		try {
			String json = mapper.writeValueAsString(shard.payload());
			// upsert por (chatId, type, version)
			Optional<ContextShardEntity> existing = repo
					.findTopByChatIdAndShardTypeOrderByVersionDesc(chatId, shard.type())
					.filter(e -> e.getVersion() == shard.version());

			ContextShardEntity e = existing.orElseGet(ContextShardEntity::new);
			e.setChatId(chatId);
			e.setShardType(shard.type());
			e.setVersion(shard.version());
			e.setStable(shard.stable());
			e.setPayloadJson(json);

			repo.save(e);
		} catch (Exception ex) {
			throw new IllegalStateException("Falha ao persistir shard " + shard.type(), ex);
		}
	}

	public List<ContextShard> findByTypes(String chatId, List<String> types) {
		if (types == null || types.isEmpty())
			return List.of();
		return repo.findByChatIdAndShardTypeIn(chatId, types).stream().map(this::toShard).toList();
	}

	public List<ContextShard> findAll(String chatId) {
		return repo.findByChatId(chatId).stream().map(this::toShard).toList();
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

	private ContextShard toShard(ContextShardEntity e) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> payload = mapper.readValue(e.getPayloadJson(), Map.class);
			return new BasicContextShard(e.getShardType(), e.getVersion(), e.isStable(), payload);
		} catch (Exception ex) {
			return new BasicContextShard(e.getShardType(), e.getVersion(), e.isStable(),
					Map.of("erro", "falha ao desserializar payload_json"));
		}
	}
}
