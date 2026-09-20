package com.fava.catalog;

import java.util.Arrays;
import java.util.Objects;

/**
 * Persisted embedding for one Saved Item, with registry model metadata.
 *
 * @param modelId OpenRouter / OpenAI-compatible embedding model id
 */
public record StoredEmbedding(String modelId, int dimensions, float[] vector) {

	public StoredEmbedding {
		if (modelId == null || modelId.isBlank()) {
			throw new IllegalArgumentException("modelId must not be blank");
		}
		if (dimensions < 1) {
			throw new IllegalArgumentException("dimensions must be positive");
		}
		if (vector == null || vector.length != dimensions) {
			throw new IllegalArgumentException("vector length must equal dimensions");
		}
		vector = vector.clone();
	}

	@Override
	public float[] vector() {
		return vector.clone();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StoredEmbedding other)) {
			return false;
		}
		return dimensions == other.dimensions
				&& Objects.equals(modelId, other.modelId)
				&& Arrays.equals(vector, other.vector);
	}

	@Override
	public int hashCode() {
		return 31 * Objects.hash(modelId, dimensions) + Arrays.hashCode(vector);
	}

	@Override
	public String toString() {
		return "StoredEmbedding[modelId=" + modelId + ", dimensions=" + dimensions + ", vector.length="
				+ vector.length + "]";
	}
}
