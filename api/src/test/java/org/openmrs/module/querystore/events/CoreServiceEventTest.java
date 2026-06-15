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

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.aop.event.SaveServiceEvent;
import org.openmrs.api.context.Context;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * De-risks the events-consumer slice: confirms that a save through a core service publishes a
 * #6084 {@link SaveServiceEvent} AND that a bean in querystore's Spring context receives it via a
 * plain {@code @EventListener}. If this fails, the consumer must register listeners by another
 * mechanism (programmatic {@code ApplicationListener}) — so it is the first thing the slice settles.
 *
 * <p>Runs on the in-memory context test database (not {@code *IntegrationTest} / testcontainers).
 * {@link org.openmrs.api.LocationService} is wired via {@code TransactionProxyFactoryBean} in core,
 * the legacy shape the #6084 advice's {@code isAopProxy} guard de-duplicates rather than suppresses,
 * so this also exercises that the event fires for a legacy-wired core service.
 */
public class CoreServiceEventTest extends BaseModuleContextSensitiveTest {

	@Test
	public void coreServiceSave_isReceivedByAContextBeanEventListener() {
		SaveServiceEventProbe probe = Context.getRegisteredComponent(
		    "querystore.test.saveServiceEventProbe", SaveServiceEventProbe.class);
		int before = probe.savedEntities().size();

		Location location = new Location();
		location.setName("QueryStore event-probe location");
		Context.getLocationService().saveLocation(location);

		assertTrue("a context bean's @EventListener should receive SaveServiceEvent from a core "
		        + "service save (legacy TransactionProxyFactoryBean-wired LocationService)",
		    probe.savedEntities().size() > before);
	}
}
