package br.com.ia.sdk.context.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.ia.sdk.context.entity.LogIA;

public interface LogIARepository extends JpaRepository<LogIA, Long> {
	

  
}
