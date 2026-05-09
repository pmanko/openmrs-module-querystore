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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Loads a real ONNX model and asserts the output shape matches the auto-detected dimension.
 * Skips when {@code querystore.test.modelPath} / {@code querystore.test.vocabPath} system
 * properties are not set, so CI without a model file stays green.
 *
 * <p>Run locally with:
 * <pre>
 * mvn -pl api -Pintegration \
 *   -Dquerystore.test.modelPath=/path/to/model.onnx \
 *   -Dquerystore.test.vocabPath=/path/to/vocab.txt \
 *   -Dtest=OnnxEmbeddingProviderIntegrationTest test
 * </pre>
 */
public class OnnxEmbeddingProviderIntegrationTest {

	private static OnnxEmbeddingProvider provider;

	@BeforeClass
	public static void setUpClass() {
		String modelPath = System.getProperty("querystore.test.modelPath");
		String vocabPath = System.getProperty("querystore.test.vocabPath");
		assumeTrue("set -Dquerystore.test.modelPath and -Dquerystore.test.vocabPath to run",
				modelPath != null && vocabPath != null);
		assumeTrue("model file does not exist: " + modelPath, new File(modelPath).exists());
		assumeTrue("vocab file does not exist: " + vocabPath, new File(vocabPath).exists());
		provider = new OnnxEmbeddingProvider(modelPath, vocabPath);
	}

	@AfterClass
	public static void tearDownClass() {
		if (provider != null) {
			provider.close();
			provider = null;
		}
	}

	@Test
	public void embed_returnsVectorMatchingAutoDetectedDimension() {
		float[] vector = provider.embed("hello world");

		assertNotNull(vector);
		int dims = provider.getDimensions();
		assertTrue("expected dimensions to be auto-detected as positive, was " + dims, dims > 0);
		assertEquals("vector length must match getDimensions()", dims, vector.length);
	}

	@Test
	public void embed_producesL2NormalizedVector() {
		float[] vector = provider.embed("temperature reading 36.7 celsius");

		double sumSquares = 0;
		for (float v : vector) {
			sumSquares += v * v;
		}
		// L2-normalised vectors have unit length; tolerance covers float32 accumulation error.
		assertEquals("expected L2-normalised vector", 1.0, sumSquares, 1e-5);
	}

	@Test
	public void embed_isIdempotentAcrossCalls() {
		float[] first = provider.embed("hello");
		float[] second = provider.embed("hello");
		assertEquals(first.length, second.length);
		assertEquals(provider.getDimensions(), first.length);
		// Same input must yield the same vector — sanity check that the session is stable.
		for (int i = 0; i < first.length; i++) {
			assertEquals("mismatch at index " + i, first[i], second[i], 1e-6);
		}
	}

	@Test
	public void embedQuery_singleEncoderModelDelegatesToEmbed() {
		float[] viaEmbed = provider.embed("query text");
		float[] viaEmbedQuery = provider.embedQuery("query text");
		assertEquals(viaEmbed.length, viaEmbedQuery.length);
		for (int i = 0; i < viaEmbed.length; i++) {
			assertEquals(viaEmbed[i], viaEmbedQuery[i], 1e-6);
		}
	}
}
