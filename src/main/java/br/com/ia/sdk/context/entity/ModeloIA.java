package br.com.ia.sdk.context.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;

@Data
@Builder
@Generated
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ia_server_modelo_ia")
@Entity
public class ModeloIA implements Serializable {

	private static final long serialVersionUID = 299090286642481063L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "nome")
	private String nome;

	@Column(name = "max_tokens")
	private int maxTokens;

	@Column(name = "api_key")
	private String apiKey;

	@Column(name = "modelo", unique = true)
	private String modelo;

	@Column(name = "temperature")
	private double temperature;

	@Column(name = "padrao")
	private boolean padrao;

}
