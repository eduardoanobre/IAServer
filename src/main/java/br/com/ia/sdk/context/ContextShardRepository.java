// src/main/java/br/com/ia/sdk/context/ContextShardRepository.java
package br.com.ia.sdk.context;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContextShardRepository extends JpaRepository<ContextShardEntity, Long> {
	
	  List<ContextShardEntity> findByChatIdAndShardTypeIn(String chatId, List<String> types);
	  List<ContextShardEntity> findByChatId(String chatId);
	  Optional<ContextShardEntity> findTopByChatIdAndShardTypeOrderByVersionDesc(String chatId, String shardType);
	  Optional<ContextShardEntity> findTopByChatIdAndTypeOrderByVersionDescIdDesc(String chatId, String type);
  
}
