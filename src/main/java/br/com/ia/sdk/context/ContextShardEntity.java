package br.com.ia.sdk.context;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ia_context_shard", uniqueConstraints = {
		@UniqueConstraint(name = "uk_shard_chat_type_ver", columnNames = { "chat_id", "shard_type",
				"version" }) }, indexes = { @Index(name = "idx_shard_chat_type", columnList = "chat_id, shard_type") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextShardEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "chat_id", nullable = false, length = 64)
	private String chatId;

	@Column(name = "shard_type", nullable = false, length = 64)
	private String shardType;

	@Column(name = "version", nullable = false)
	private int version;

	@Column(name = "stable", nullable = false)
	private boolean stable;

	/** JSON leve do payload */
	@Lob
	@Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
	private String payloadJson;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;

}
