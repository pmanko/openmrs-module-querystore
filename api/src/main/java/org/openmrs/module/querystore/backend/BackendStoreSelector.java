/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.backend;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.QueryStoreConstants;

/**
 * Picks the active {@link BackendStore} based on the {@code querystore.backend} global property per
 * ADR Decision 3. Spring wires every candidate backend bean into this selector at context-refresh
 * time; {@code QueryStoreActivator.started()} then calls {@link #getStore()} to read the GP via
 * {@link AdministrationService} and inject the chosen backend into the service. The lookup runs
 * after Spring context refresh completes — calling it during bean construction self-deadlocks
 * because {@code ServiceContext.getService()} blocks on the in-progress refresh (issue #10).
 * Unknown GP values fall back to {@link QueryStoreConstants#DEFAULT_BACKEND} with a logged warning,
 * so an admin can correct the GP and restart. Startup only fails when the default candidate itself
 * is missing from the wiring, which is a misconfiguration rather than a recoverable runtime state.
 */
public class BackendStoreSelector {

	private static final Log log = LogFactory.getLog(BackendStoreSelector.class);

	private final Map<String, BackendStore> candidates;

	public BackendStoreSelector(Map<String, BackendStore> candidates) {
		// Normalize keys to lower case at construction time so the GP-lookup branch (also lower
		// cased) doesn't have to care how the wiring spelled them.
		Map<String, BackendStore> normalized = new LinkedHashMap<>(candidates.size());
		for (Map.Entry<String, BackendStore> entry : candidates.entrySet()) {
			normalized.put(entry.getKey().toLowerCase(), entry.getValue());
		}
		this.candidates = normalized;
	}

	/**
	 * Resolves the active backend by reading the {@code querystore.backend} GP. Must be called after
	 * Spring context refresh completes (typically from {@code QueryStoreActivator.started()}) — see
	 * the class-level javadoc for the deadlock that prompted this constraint.
	 */
	public BackendStore getStore() {
		String chosen = resolveBackendName();
		BackendStore store = candidates.get(chosen);
		if (store == null) {
			log.warn("Unknown querystore.backend='" + chosen + "'; falling back to "
			        + QueryStoreConstants.DEFAULT_BACKEND);
			store = candidates.get(QueryStoreConstants.DEFAULT_BACKEND);
		}
		if (store == null) {
			throw new IllegalStateException(
			        "No BackendStore candidate registered for querystore.backend=" + chosen
			                + " and no default candidate '" + QueryStoreConstants.DEFAULT_BACKEND + "' wired");
		}
		log.info("Query Store backend resolved: " + chosen);
		return store;
	}

	private static String resolveBackendName() {
		AdministrationService admin = Context.getAdministrationService();
		String value = admin.getGlobalProperty(QueryStoreConstants.GP_BACKEND,
		    QueryStoreConstants.DEFAULT_BACKEND);
		if (value == null || value.trim().isEmpty()) {
			return QueryStoreConstants.DEFAULT_BACKEND;
		}
		return value.trim().toLowerCase();
	}
}
