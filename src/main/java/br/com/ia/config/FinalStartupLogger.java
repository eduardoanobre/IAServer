package br.com.ia.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.ia.utils.AmbienteUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class FinalStartupLogger {

	private final AmbienteUtils ambienteUtils;

	@EventListener(ApplicationReadyEvent.class)
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void logFinalConfig() {
		log.info("[FinalStartupLogger] ApplicationReady -> imprimindo snapshot final");
		ambienteUtils.logSpringCloudConfiguration();
	}
}
