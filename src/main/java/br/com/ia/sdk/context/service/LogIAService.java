package br.com.ia.sdk.context.service;

import br.com.ia.sdk.context.repository.LogIARepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LogIAService {

	private final LogIARepository repository;
}
