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

import org.openmrs.Condition;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.serialization.ConditionRecordSerializer;

/**
 * Migration-bridge advice on {@link org.openmrs.api.ConditionService}.
 * <pre>Removal trigger: TBD (events-first condition subscriber)</pre>
 */
public class ConditionIndexingAdvice extends AbstractIndexingAdvice<Condition> {

	static final Set<String> TRIGGER_METHODS = new HashSet<>(Arrays.asList(
	        "saveCondition", "voidCondition", "unvoidCondition", "purgeCondition"));

	static final Set<String> PURGE_METHODS = Collections.singleton("purgeCondition");

	@Override
	protected Class<Condition> getSupportedType() {
		return Condition.class;
	}

	@Override
	protected ConditionRecordSerializer serializer() {
		return Context.getRegisteredComponent("querystore.serializer.condition",
		        ConditionRecordSerializer.class);
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
