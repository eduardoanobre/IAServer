package br.com.ia.sdk.context.service;

import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import br.com.ia.sdk.context.dto.FiltroModeloIADTO;
import br.com.ia.sdk.context.entity.ModeloIA;
import br.com.ia.sdk.context.repository.ModeloIARepository;
import br.com.ia.sdk.exception.IAExecutionException;
import br.com.ia.utils.FiltroUtils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ModeloIAService {
	private final ModeloIARepository repository;

	@Transactional(readOnly = true)
	public Page<ModeloIA> obterLogs(FiltroModeloIADTO filtro) {
		return repository.obtermodelos(filtro.getPageRequest(), filtro.getId(),
				FiltroUtils.filtroLike(filtro.getNome()), FiltroUtils.filtroLike(filtro.getModelo()));
	}

	@Transactional(readOnly = true)
	public ModeloIA obterModeloIA(String modelo) {
		if (FiltroUtils.isBlank(modelo)) {
			return obterModeloPadrao();
		}
		return repository.findByModelo(modelo.trim()).orElseGet(this::obterModeloPadrao);
	}

	public ModeloIA obterModeloPadrao() {
		return repository.findFirstByPadraoTrue().orElseThrow(() -> new IAExecutionException(
				"Nenhum ModeloIA padrão configurado. Configure um registro com 'padrao = true'."));
	}

	public ModeloIA obterModeloIA(Long id) throws IAExecutionException {
		return repository.findById(id).orElseThrow(() -> new IAExecutionException("Modelo de IA não encontrado"));
	}
}
