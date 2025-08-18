package br.com.ia.sdk.context.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.ia.sdk.context.entity.Shard;

public interface ShardRepository extends JpaRepository<Shard, Long> {
	
	  List<Shard> findByChatIdAndShardTypeIn(String chatId, List<String> types);
	  List<Shard> findByChatId(String chatId);
	  Optional<Shard> findTopByChatIdAndShardTypeOrderByVersionDesc(String chatId, String shardType);
	  Optional<Shard> findTopByChatIdAndShardTypeOrderByVersionDescIdDesc(String chatId, String type);
  
}
