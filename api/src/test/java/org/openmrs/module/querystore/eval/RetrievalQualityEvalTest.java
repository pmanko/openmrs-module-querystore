/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.eval;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openmrs.module.querystore.api.QueryStoreService;
import org.openmrs.module.querystore.api.impl.QueryStoreServiceImpl;
import org.openmrs.module.querystore.backend.lucene.LuceneBackendStore;
import org.openmrs.module.querystore.embedding.OnnxEmbeddingProvider;
import org.openmrs.module.querystore.model.QueryDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieval-quality eval against the 153-record chartsearchai benchmark. Indexes every record as
 * a {@link QueryDocument} through {@link QueryStoreService} backed by {@link LuceneBackendStore}
 * and {@link OnnxEmbeddingProvider}, then replays each case in
 * {@code eval/retrieval-eval-dataset.json} and computes recall@30 against
 * {@code expectedRecordIndices}.
 *
 * <p>Skipped cleanly when {@code -Dquerystore.eval.modelDir} is not set or the model/vocab files
 * are not present, so CI and unconfigured local runs stay green. The model directory must point
 * at a self-contained all-MiniLM-L6-v2 export (or compatible BERT-class sentence encoder):
 * external-data variants whose {@code model.onnx.data} sibling is missing will fail to load.
 *
 * <p>Run locally with:
 * <pre>
 * mvn -pl api -Dtest=RetrievalQualityEvalTest \
 *   -Dquerystore.eval.modelDir=/abs/path/to/model-and-vocab test
 * </pre>
 */
public class RetrievalQualityEvalTest {

	private static final Logger log = LoggerFactory.getLogger(RetrievalQualityEvalTest.class);

	private static final String MODEL_DIR = System.getProperty("querystore.eval.modelDir");

	private static final String MODEL_PATH = MODEL_DIR != null ? MODEL_DIR + "/model.onnx" : null;

	private static final String VOCAB_PATH = MODEL_DIR != null ? MODEL_DIR + "/vocab.txt" : null;

	private static final String PATIENT_UUID = UUID.nameUUIDFromBytes("retrieval-eval".getBytes()).toString();

	private static final int TOP_K = 30;

	// chartsearchai's RetrievalQualityEvalTest asserts the same overall recall threshold; mirrored
	// here so a querystore regression below the chartsearchai bar fails loudly.
	private static final double MIN_AVG_RECALL = 0.4;

	// chartsearchai also gates per-case latency under 200ms.
	private static final long PER_CASE_LATENCY_BUDGET_MS = 200L;

	private static OnnxEmbeddingProvider provider;

	private static LuceneBackendStore backend;

	private static QueryStoreService service;

	private static Path indexRoot;

	private static EvalDataset evalDataset;

	@BeforeAll
	public static void setUpClass() throws Exception {
		if (!modelFilesExist()) {
			log.info("Skipping retrieval eval: set -Dquerystore.eval.modelDir to a directory "
			        + "containing a self-contained model.onnx and vocab.txt to run "
			        + "(current value: {})", MODEL_DIR);
			return;
		}
		indexRoot = Files.createTempDirectory("querystore-eval-");
		backend = new LuceneBackendStore(indexRoot);
		provider = new OnnxEmbeddingProvider(MODEL_PATH, VOCAB_PATH);
		QueryStoreServiceImpl impl = new QueryStoreServiceImpl();
		impl.setBackend(backend);
		impl.setEmbeddingProvider(provider);
		service = impl;

		evalDataset = EvalDataset.load("eval/retrieval-eval-dataset.json");

		List<String> records = loadDataset();
		long start = System.currentTimeMillis();
		for (int i = 0; i < records.size(); i++) {
			service.index(toDocument(i + 1, records.get(i)));
		}
		log.info("Indexed {} records in {} ms", records.size(), System.currentTimeMillis() - start);
	}

	@AfterAll
	public static void tearDownClass() throws IOException {
		if (provider != null) {
			provider.close();
		}
		if (backend != null) {
			backend.close();
		}
		if (indexRoot != null) {
			deleteRecursively(indexRoot.toFile());
		}
	}

	@Test
	public void retrievalRecall_perCase() {
		assumeTrue(modelFilesExist(),
		        "set -Dquerystore.eval.modelDir to a model+vocab directory to run");

		for (EvalCase evalCase : evalDataset.getCases()) {
			long start = System.currentTimeMillis();
			List<Integer> retrieved = retrieveTopK(evalCase);
			long elapsed = System.currentTimeMillis() - start;
			double recall = EvalMetrics.recall(retrieved, evalCase.getExpectedRecordIndices());

			log.info("[{}] recall@{}={} latency={}ms expected={} top5={}",
			        evalCase.getId(), TOP_K, String.format(Locale.ROOT, "%.3f", recall), elapsed,
			        evalCase.getExpectedRecordIndices(),
			        retrieved.subList(0, Math.min(5, retrieved.size())));

			assertTrue(elapsed < PER_CASE_LATENCY_BUDGET_MS,
			        evalCase.getId() + ": retrieval should complete in < "
			                + PER_CASE_LATENCY_BUDGET_MS + "ms but took " + elapsed + "ms");
		}
	}

	@Test
	public void retrievalRecall_shouldMeetMinimumThreshold() {
		assumeTrue(modelFilesExist(),
		        "set -Dquerystore.eval.modelDir to a model+vocab directory to run");

		int totalCases = 0;
		double totalRecall = 0;
		Map<String, Double> perCase = new LinkedHashMap<>();
		for (EvalCase evalCase : evalDataset.getCases()) {
			totalCases++;
			List<Integer> retrieved = retrieveTopK(evalCase);
			double recall = EvalMetrics.recall(retrieved, evalCase.getExpectedRecordIndices());
			totalRecall += recall;
			perCase.put(evalCase.getId(), recall);
		}
		double avgRecall = totalCases > 0 ? totalRecall / totalCases : 0;

		log.info("Retrieval eval summary: avgRecall@{}={} across {} cases", TOP_K,
		        String.format(Locale.ROOT, "%.3f", avgRecall), totalCases);
		for (Map.Entry<String, Double> e : perCase.entrySet()) {
			log.info("  {} -> {}", e.getKey(), String.format(Locale.ROOT, "%.3f", e.getValue()));
		}

		assertTrue(avgRecall >= MIN_AVG_RECALL,
		        "Average retrieval recall@" + TOP_K + " should be >= " + MIN_AVG_RECALL
		                + " but was " + String.format(Locale.ROOT, "%.3f", avgRecall));
	}

	private static List<Integer> retrieveTopK(EvalCase evalCase) {
		List<QueryDocument> results = service.search(evalCase.getQuestion(), TOP_K);
		List<Integer> indices = new ArrayList<>(results.size());
		for (QueryDocument doc : results) {
			indices.add(Integer.parseInt(doc.getResourceUuid()));
		}
		return indices;
	}

	private static QueryDocument toDocument(int index, String text) {
		QueryDocument doc = new QueryDocument();
		doc.setPatientUuid(PATIENT_UUID);
		doc.setResourceType(resourceTypeForPrefix(text));
		// String index → reliable mapping back to expectedRecordIndices on the query path.
		doc.setResourceUuid(Integer.toString(index));
		doc.setText(text);
		doc.setEmbedding(provider.embed(text));
		return doc;
	}

	/**
	 * Maps the labeled-prose prefix of a serialized record to the querystore resource type the
	 * matching {@code *RecordSerializer} writes. Keeps per-type indices fanning out the same way
	 * as production traffic on the wildcard search path.
	 */
	private static String resourceTypeForPrefix(String text) {
		if (text.startsWith("Clinical observation:")) {
			return "obs";
		}
		if (text.startsWith("Clinical diagnosis:")) {
			return "diagnosis";
		}
		if (text.startsWith("Medical condition:")) {
			return "condition";
		}
		if (text.startsWith("Medication prescription:")) {
			return "drug_order";
		}
		if (text.startsWith("Patient allergy:")) {
			return "allergy";
		}
		if (text.startsWith("Program enrollment:")) {
			return "program";
		}
		throw new IllegalArgumentException("Unrecognised record prefix in: "
		        + text.substring(0, Math.min(40, text.length())));
	}

	private static List<String> loadDataset() throws IOException {
		try (InputStream is = RetrievalQualityEvalTest.class.getClassLoader()
		        .getResourceAsStream("eval/full-patient-dataset.json")) {
			if (is == null) {
				throw new IOException("Dataset not found on classpath: eval/full-patient-dataset.json");
			}
			return new ObjectMapper().readValue(is, new TypeReference<List<String>>() {});
		}
	}

	private static boolean modelFilesExist() {
		return MODEL_PATH != null && VOCAB_PATH != null
		        && new File(MODEL_PATH).exists() && new File(VOCAB_PATH).exists();
	}

	private static void deleteRecursively(File f) {
		if (f.isDirectory()) {
			File[] kids = f.listFiles();
			if (kids != null) {
				for (File k : kids) {
					deleteRecursively(k);
				}
			}
		}
		// Best-effort: the JVM will GC any lingering Lucene file handles when the backend closes;
		// a leftover temp directory on a failed test isn't worth crashing for.
		f.delete();
	}
}
