/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.bridge;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.AdministrationService;
import org.openmrs.module.querystore.QueryStoreConstants;

/**
 * Caches the active {@link SyncMode} so the gate in {@link AbstractIndexingAdvice} (and, later, the
 * events consumer) is a {@code volatile} read rather than a global-property lookup on every advised
 * clinical save. {@code querystore.syncMode} is a migration control that changes rarely, so the
 * value is read once at startup ({@link #refresh(AdministrationService)}, called from the activator)
 * and changing it takes effect on the next restart — acceptable for an operational cutover.
 *
 * <p>Defaults to {@link SyncMode#AOP} until {@link #refresh} runs, so a context that never seeds it
 * (notably plain unit tests of the advice) behaves exactly as before this gate existed.
 */
public class SyncModeResolver {

	private static final Log log = LogFactory.getLog(SyncModeResolver.class);

	private volatile SyncMode current = SyncMode.AOP;

	public SyncMode current() {
		return current;
	}

	/** Re-reads {@code querystore.syncMode} and caches the resolved mode. */
	public void refresh(AdministrationService administrationService) {
		String value = administrationService.getGlobalProperty(QueryStoreConstants.GP_SYNC_MODE,
		    QueryStoreConstants.DEFAULT_SYNC_MODE);
		this.current = SyncMode.parse(value);
		log.info("Query Store sync mode resolved to " + current);
	}
}
