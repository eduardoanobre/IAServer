package br.com.ia.sdk.context.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import lombok.Data;

@Data
public class FiltroModeloIADTO {

	private Long id;
	private String nome;
	private String modelo;
	private String sortBy;
	private Integer sortOrder;
	private int page = 0;
	private int pageSize = 100;

	public PageRequest getPageRequest() {

		if (pageSize <= 0) {
			pageSize = 100;
		}

		if (sortBy == null || sortBy.isEmpty()) {
			return PageRequest.of(page, pageSize);
		}

		if (sortOrder != null && sortOrder.equals(-1)) {
			return PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, sortBy));
		}

		return PageRequest.of(page, pageSize, Sort.by(Sort.Direction.ASC, sortBy));
	}

}
