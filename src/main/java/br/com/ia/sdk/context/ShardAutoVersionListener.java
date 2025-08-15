package br.com.ia.sdk.context;

import br.com.ia.utils.ShardUtils;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class ShardAutoVersionListener {

	@PrePersist
	@PreUpdate
	public void touch(Object entity) {
		if (!(entity instanceof ShardTracked st))
			return;

		var fields = st.shardFields();
		if (fields == null || fields.isEmpty())
			return;

		String fp = ShardUtils.fingerprintByFields(entity, fields);
		String prev = st.getShardFingerprint();

		if (!fp.equals(prev)) {
			st.setShardFingerprint(fp);
			Integer v = st.getShardVersion();
			st.setShardVersion(v == null || v <= 0 ? 1 : v + 1);
		}
	}
}
