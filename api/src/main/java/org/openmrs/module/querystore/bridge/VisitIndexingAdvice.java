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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.serialization.VisitRecordSerializer;

/**
 * Migration-bridge advice on {@link org.openmrs.api.VisitService}.
 * <pre>Removal trigger: TBD (events-first visit subscriber)</pre>
 */
public class VisitIndexingAdvice extends AbstractIndexingAdvice<Visit> {

	static final Set<String> TRIGGER_METHODS = new HashSet<>(Arrays.asList(
	        "saveVisit", "voidVisit", "unvoidVisit", "purgeVisit"));

	static final Set<String> PURGE_METHODS = Collections.singleton("purgeVisit");

	@Override
	protected Class<Visit> getSupportedType() {
		return Visit.class;
	}

	@Override
	protected VisitRecordSerializer serializer() {
		return Context.getRegisteredComponent("querystore.serializer.visit",
		        VisitRecordSerializer.class);
	}

	@Override
	protected Set<String> triggerMethods() {
		return TRIGGER_METHODS;
	}

	@Override
	protected Set<String> purgeMethods() {
		return PURGE_METHODS;
	}
}
