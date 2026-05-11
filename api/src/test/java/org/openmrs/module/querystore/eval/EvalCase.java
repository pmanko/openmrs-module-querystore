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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Ported from chartsearchai. A single retrieval-eval test case from {@code
 * retrieval-eval-dataset.json}: a natural-language clinical question plus the 1-based indices of
 * records in the 153-record dataset that ought to come back. Fields beyond those used today are
 * tolerated so the shared dataset shape survives future eval types.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalCase {

	private String id;

	private String question;

	private List<Integer> expectedRecordIndices;

	private List<String> tags;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public List<Integer> getExpectedRecordIndices() {
		return expectedRecordIndices;
	}

	public void setExpectedRecordIndices(List<Integer> expectedRecordIndices) {
		this.expectedRecordIndices = expectedRecordIndices;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}
}
