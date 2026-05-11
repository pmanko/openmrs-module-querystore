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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ported from chartsearchai. {@code recall = |retrieved ∩ expected| / |expected|}; an empty
 * expected set is treated as perfect recall so cases without ground-truth records don't drag the
 * average down with a sentinel zero.
 */
public final class EvalMetrics {

	private EvalMetrics() {
	}

	public static double recall(List<Integer> predicted, List<Integer> expected) {
		if (expected.isEmpty()) {
			return 1.0;
		}
		Set<Integer> predictedSet = new HashSet<>(predicted);
		int hits = 0;
		for (Integer e : expected) {
			if (predictedSet.contains(e)) {
				hits++;
			}
		}
		return (double) hits / expected.size();
	}
}
