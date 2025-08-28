package br.com.ia.sdk.context.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;

@Data
@Generated
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ia_server_log_ia")
@Entity
public class LogIA implements Serializable {

	private static final long serialVersionUID = -4524201924040276181L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "id_chat")
	private String idChat;

	@CreationTimestamp
	@Column(name = "data", nullable = false, updatable = false)
	private LocalDateTime data;

	@Lob
	@Column(name = "prompt", columnDefinition = "LONGTEXT")
	private String prompt;

	@Lob
	@Column(name = "resposta", columnDefinition = "LONGTEXT")
	private String resposta;

	@Lob
	@Column(name = "erro", length = 1000)
	private String erro;

	@Lob
	@Column(name = "error_message", columnDefinition = "LONGTEXT")
	private String errorMessage;

	@Column(name = "sucesso")
	private boolean sucesso;

	@Column(name = "enviado")
	private boolean enviado;

	@Column(name = "duracao_execucao_ms")
	private Long duracaoExecucaoMs;

	@Column(name = "custo")
	private BigDecimal custo;

	@Column(name = "tokens_prompt")
	private int tokensPrompt;

	@Column(name = "tokens_resposta")
	private int tokensResposta;

}
