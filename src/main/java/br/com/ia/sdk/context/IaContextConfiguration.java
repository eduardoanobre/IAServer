package br.com.ia.sdk.context;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EntityScan(basePackageClasses = ContextShardEntity.class)
@EnableJpaRepositories(basePackageClasses = ContextShardRepository.class)
public class IaContextConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ContextShardService contextShardService(ContextShardRepository repo, ObjectMapper mapper) {
		return new ContextShardService(repo, mapper);
	}
}
