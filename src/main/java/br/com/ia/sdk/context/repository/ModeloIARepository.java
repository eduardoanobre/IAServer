package br.com.ia.sdk.context.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.ia.sdk.context.entity.ModeloIA;

public interface ModeloIARepository extends JpaRepository<ModeloIA, Long> {

	@Query("""
			SELECT p FROM ModeloIA p
			WHERE (p.modelo = :modelo)
			""")
	Optional<ModeloIA> findByModelo(String modelo);

	@Query("""
			SELECT p FROM ModeloIA p
			WHERE (:id IS NULL OR p.id = :id)
			AND (p.nome LIKE :nome)
			AND (p.modelo LIKE :modelo)
			""")
	Page<ModeloIA> obtermodelos(Pageable pageable, Long id, String nome, String modelo);

	Optional<ModeloIA> findFirstByPadraoTrue();
}
