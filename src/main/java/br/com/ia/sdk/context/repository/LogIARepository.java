package br.com.ia.sdk.context.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import br.com.ia.sdk.context.entity.LogIA;

public interface LogIARepository extends JpaRepository<LogIA, Long> {

	@Modifying
	@Query("""
			UPDATE LogIA l
			SET l.enviado = TRUE
			WHERE l.id = :requestId
			""")
	void setEnviado(long requestId);

}
