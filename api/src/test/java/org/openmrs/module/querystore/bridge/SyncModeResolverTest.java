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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.openmrs.api.AdministrationService;
import org.openmrs.module.querystore.QueryStoreConstants;

public class SyncModeResolverTest {

	@Test
	public void current_defaultsToAop_beforeRefresh() {
		// A context that never seeds the resolver (notably plain advice unit tests) must behave as
		// if the gate did not exist — AOP on.
		assertEquals(SyncMode.AOP, new SyncModeResolver().current());
	}

	@Test
	public void refresh_readsGlobalPropertyAndCaches() {
		AdministrationService admin = mock(AdministrationService.class);
		when(admin.getGlobalProperty(QueryStoreConstants.GP_SYNC_MODE,
		    QueryStoreConstants.DEFAULT_SYNC_MODE)).thenReturn("events");

		SyncModeResolver resolver = new SyncModeResolver();
		resolver.refresh(admin);

		assertEquals(SyncMode.EVENTS, resolver.current());
	}

	@Test
	public void refresh_unknownValue_fallsBackToAop() {
		AdministrationService admin = mock(AdministrationService.class);
		when(admin.getGlobalProperty(anyString(), anyString())).thenReturn("nope");

		SyncModeResolver resolver = new SyncModeResolver();
		resolver.refresh(admin);

		assertEquals(SyncMode.AOP, resolver.current());
	}
}
