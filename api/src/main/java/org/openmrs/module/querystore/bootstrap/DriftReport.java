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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, serialization-friendly per-type drift snapshot (ADR: Sync reliability and
 * reconciliation): for each resource type, the {@code coreCount} the scan would visit
 * ({@link TypeBootstrapper#countIndexable()}) versus the {@code indexedCount} live in the backend
 * ({@link org.openmrs.module.querystore.backend.BackendStore#countByType(String)}). This is
 * <em>detection</em> only — remediation reuses the existing reindex paths.
 *
 * <p>{@code drift = coreCount - indexedCount}, but only when both counts are known: either count can
 * be {@code -1} ("unknown" — a backend with no count impl, or a non-Hibernate bootstrapper), in which
 * case {@code drift} is {@code null}. And {@code coreCount} is an <em>upper bound</em> on
 * {@code indexedCount} (the serializer legitimately null-serializes some visited records — obs group
 * parents, value-less diagnoses), so a small stable per-type positive drift is expected baseline; the
 * actionable signal is a large or growing gap, not merely non-zero drift. A <em>negative</em> drift
 * (more indexed than core) means the index holds rows core no longer visits — stale docs for source
 * records that were deleted/voided but not yet reaped — and is also worth investigating. The derivation
 * lives in the api layer so it is unit-testable; the omod REST controller is a thin adapter over
 * {@link #toMap()}.
 */
public class DriftReport {

	private final List<TypeDrift> types;

	public DriftReport(List<TypeDrift> types) {
		this.types = Collections.unmodifiableList(new ArrayList<TypeDrift>(types));
	}

	public List<TypeDrift> getTypes() {
		return types;
	}

	/**
	 * Serialization-friendly view: {@code {types:[{resourceType, coreCount, indexedCount, drift}]}}
	 * with stable key order. {@code drift} is {@code null} when either count is unknown ({@code -1}).
	 * Lives here (not the controller) so the response key contract is unit-tested.
	 */
	public Map<String, Object> toMap() {
		List<Map<String, Object>> typeMaps = new ArrayList<Map<String, Object>>(types.size());
		for (TypeDrift t : types) {
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("resourceType", t.getResourceType());
			m.put("coreCount", t.getCoreCount());
			m.put("indexedCount", t.getIndexedCount());
			m.put("drift", t.getDrift());
			typeMaps.add(m);
		}
		Map<String, Object> response = new LinkedHashMap<String, Object>();
		response.put("types", typeMaps);
		return response;
	}

	/** Per-resource-type drift entry. A count of {@code -1} means "unknown" (uncomputable on this tier). */
	public static final class TypeDrift {

		private final String resourceType;

		private final long coreCount;

		private final long indexedCount;

		public TypeDrift(String resourceType, long coreCount, long indexedCount) {
			this.resourceType = resourceType;
			this.coreCount = coreCount;
			this.indexedCount = indexedCount;
		}

		public String getResourceType() {
			return resourceType;
		}

		public long getCoreCount() {
			return coreCount;
		}

		public long getIndexedCount() {
			return indexedCount;
		}

		/** {@code coreCount - indexedCount}, or {@code null} when either count is unknown ({@code -1}). */
		public Long getDrift() {
			if (coreCount < 0 || indexedCount < 0) {
				return null;
			}
			return coreCount - indexedCount;
		}
	}
}
