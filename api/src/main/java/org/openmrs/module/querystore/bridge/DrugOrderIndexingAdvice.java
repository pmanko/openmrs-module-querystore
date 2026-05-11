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

import org.openmrs.DrugOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.serialization.DrugOrderRecordSerializer;

/**
 * Migration-bridge advice on the drug-order methods of {@link org.openmrs.api.OrderService}.
 * Drug, test, and referral orders all share the same service surface (saveOrder / voidOrder /
 * unvoidOrder / purgeOrder), so three separate aspects register on {@code OrderService} and each
 * one's {@code instanceof <Subtype>Order} guard via {@link #getSupportedType()} selects only its
 * own subtype. Spring AOP fires every registered advice for every advised call, and the type
 * filter is what keeps the three aspects from cross-projecting.
 * <pre>Removal trigger: TBD (events-first drug-order subscriber)</pre>
 */
public class DrugOrderIndexingAdvice extends AbstractIndexingAdvice<DrugOrder> {

	static final Set<String> TRIGGER_METHODS = new HashSet<>(Arrays.asList(
	        "saveOrder", "saveRetrospectiveOrder", "voidOrder", "unvoidOrder", "purgeOrder"));

	static final Set<String> PURGE_METHODS = Collections.singleton("purgeOrder");

	@Override
	protected Class<DrugOrder> getSupportedType() {
		return DrugOrder.class;
	}

	@Override
	protected DrugOrderRecordSerializer serializer() {
		return Context.getRegisteredComponent("querystore.serializer.drug_order",
		        DrugOrderRecordSerializer.class);
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
