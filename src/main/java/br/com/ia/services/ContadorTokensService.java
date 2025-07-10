package br.com.ia.services;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContadorTokensService {

	public static void main(String[] args) {
		System.out.println(contarTokens("Eduardo Flávio Alves Nobre"));
		System.out.println(custo(10));
	}

	public static int contarTokens(String texto) {
		EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
		Encoding secondEnc = registry.getEncodingForModel(ModelType.TEXT_EMBEDDING_ADA_002);
		return secondEnc.countTokens(texto);
	}

	public static BigDecimal custo(int tokens) {
		return BigDecimal.valueOf(tokens).divide(BigDecimal.valueOf(1000)).multiply(BigDecimal.valueOf(0.001));
	}
}
