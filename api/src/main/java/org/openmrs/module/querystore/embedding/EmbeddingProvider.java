/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes dense vector embeddings for serialized clinical text. Implementations must use a
 * multilingual model, per ADR Decision 8.
 *
 * <p>Supports both single-encoder models (e.g. multilingual-e5, all-MiniLM-L6-v2) where the same
 * model encodes both records and queries, and dual-encoder models (e.g. MedCPT) where separate
 * models encode queries and records into a shared vector space. Single-encoder providers need only
 * implement {@link #embed(String)}; dual-encoder providers also override {@link #embedQuery(String)}.
 */
public interface EmbeddingProvider {

	int getDimensions();

	float[] embed(String text);

	/**
	 * Embeds a query for kNN retrieval. For dual-encoder models, this uses the query encoder
	 * which is trained to map questions into the same vector space as record embeddings. For
	 * single-encoder models, the default delegates to {@link #embed(String)}.
	 */
	default float[] embedQuery(String text) {
		return embed(text);
	}

	/**
	 * Embeds a batch of texts. Implementations should override to take advantage of bulk APIs
	 * where the underlying model supports them; the default delegates to {@link #embed(String)}
	 * sequentially. Backfill paths (see ADR open question on initial bootstrap) are the primary
	 * caller.
	 */
	default List<float[]> embed(List<String> texts) {
		List<float[]> out = new ArrayList<>(texts.size());
		for (String text : texts) {
			out.add(embed(text));
		}
		return out;
	}

	/**
	 * Returns the model identifier (typically a file path or known model name) for traceability.
	 * Surfaced through the SPI per ADR Decision 13 — consumers that issue kNN queries against
	 * vectors they computed themselves must use the same model querystore embedded with at index
	 * time. Default implementations return {@code null} when no identifier is available.
	 */
	default String getModelName() {
		return null;
	}
}
