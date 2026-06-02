/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.bootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** Unit tests for the drift math + JSON shape — the response contract consumers poll. */
public class DriftReportTest {

	@Test
	public void drift_isCoreMinusIndexed_whenBothKnown() {
		assertEquals(Long.valueOf(10), new DriftReport.TypeDrift("obs", 100, 90).getDrift());
		assertEquals("negative drift = stale extras (more indexed than core)",
		        Long.valueOf(-3), new DriftReport.TypeDrift("obs", 7, 10).getDrift());
	}

	@Test
	public void drift_isNull_whenEitherCountUnknown() {
		assertNull("core unknown -> drift uncomputable", new DriftReport.TypeDrift("obs", -1, 90).getDrift());
		assertNull("indexed unknown -> drift uncomputable", new DriftReport.TypeDrift("obs", 100, -1).getDrift());
	}

	@Test
	public void toMap_carriesPerTypeKeys_andNullDriftWhenUnknown() {
		DriftReport report = new DriftReport(Arrays.asList(
		        new DriftReport.TypeDrift("obs", 100, 90),
		        new DriftReport.TypeDrift("diagnosis", -1, 5)));

		Map<String, Object> map = report.toMap();
		List<?> types = (List<?>) map.get("types");
		assertEquals(2, types.size());

		Map<?, ?> obs = (Map<?, ?>) types.get(0);
		assertEquals("obs", obs.get("resourceType"));
		assertEquals(100L, obs.get("coreCount"));
		assertEquals(90L, obs.get("indexedCount"));
		assertEquals(10L, obs.get("drift"));

		Map<?, ?> diag = (Map<?, ?>) types.get(1);
		assertTrue("the drift key is present even when unknown", diag.containsKey("drift"));
		assertNull("drift is null when a count is unknown", diag.get("drift"));
	}
}
