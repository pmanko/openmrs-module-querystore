/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openmrs.aop.event.SaveServiceEvent;
import org.springframework.context.event.EventListener;

/**
 * Test-only probe used by {@code CoreServiceEventIntegrationTest} to settle the open question the
 * events-consumer slice hinges on: does a bean defined in querystore's Spring context actually
 * receive core's #6084 {@link SaveServiceEvent} via a plain {@code @EventListener} when a core
 * service save fires? Registered in {@code TestingApplicationContext.xml} so it is wired into the
 * same context the module beans load into. Records the entity from every {@code SaveServiceEvent},
 * synchronously at publish time (the core advice publishes inside the save call), so a test can save
 * then assert without waiting.
 */
public class SaveServiceEventProbe {

	private final List<Object> savedEntities = new CopyOnWriteArrayList<>();

	@EventListener
	public void onSave(SaveServiceEvent<?> event) {
		savedEntities.add(event.getEntity());
	}

	public List<Object> savedEntities() {
		return savedEntities;
	}
}
