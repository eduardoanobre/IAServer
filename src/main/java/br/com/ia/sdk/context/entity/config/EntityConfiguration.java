package br.com.ia.sdk.context.entity.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ia.sdk.context.repository.LogIARepository;
import br.com.ia.sdk.context.repository.ModeloIARepository;
import br.com.ia.sdk.context.repository.ShardRepository;
import br.com.ia.sdk.context.service.LogIAService;
import br.com.ia.sdk.context.service.ModeloIAService;
import br.com.ia.sdk.context.service.ShardService;

@AutoConfiguration
@EnableJpaRepositories(basePackages = "br.com.ia.sdk.context.repository")
@EntityScan(basePackages = "br.com.ia.sdk.context.entity")
public class EntityConfiguration {

	@org.springframework.context.annotation.Bean
	@ConditionalOnMissingBean
	public ShardService contextShardService(ShardRepository repo, ObjectMapper mapper) {
		return new ShardService(repo, mapper);
	}

	@Bean
	@ConditionalOnMissingBean(LogIAService.class)
	public LogIAService logIAService(LogIARepository repo) {
		return new LogIAService(repo);
	}

	@Bean
	@ConditionalOnMissingBean(ModeloIAService.class)
	public ModeloIAService modeloIAService(ModeloIARepository repo) {
		return new ModeloIAService(repo);
	}

}
