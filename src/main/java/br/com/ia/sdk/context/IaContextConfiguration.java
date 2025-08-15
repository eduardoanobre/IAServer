package br.com.ia.sdk.context;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfiguration
@EnableJpaRepositories(basePackageClasses = ContextShardRepository.class)
@EntityScan(basePackageClasses = ContextShardEntity.class)
public class IaContextConfiguration {

	@org.springframework.context.annotation.Bean
	@ConditionalOnMissingBean
	public ContextShardService contextShardService(ContextShardRepository repo, ObjectMapper mapper) {
		return new ContextShardService(repo, mapper);
	}
}
