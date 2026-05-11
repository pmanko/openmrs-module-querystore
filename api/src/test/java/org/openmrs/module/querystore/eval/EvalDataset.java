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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Ported from chartsearchai. Loads {@link EvalCase}s from a JSON file on the test classpath.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalDataset {

	private String dataset;

	private String description;

	private List<EvalCase> cases;

	public String getDataset() {
		return dataset;
	}

	public void setDataset(String dataset) {
		this.dataset = dataset;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<EvalCase> getCases() {
		return cases;
	}

	public void setCases(List<EvalCase> cases) {
		this.cases = cases;
	}

	public static EvalDataset load(String resourcePath) throws IOException {
		try (InputStream is = EvalDataset.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (is == null) {
				throw new IOException("Eval dataset not found on classpath: " + resourcePath);
			}
			return new ObjectMapper().readValue(is, EvalDataset.class);
		}
	}
}
