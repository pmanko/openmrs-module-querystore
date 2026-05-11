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

import org.openmrs.ReferralOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.serialization.ReferralOrderRecordSerializer;

/**
 * Migration-bridge advice on the referral-order methods of {@link org.openmrs.api.OrderService}.
 * See {@link DrugOrderIndexingAdvice} for the three-aspects-per-service rationale.
 * <pre>Removal trigger: TBD (events-first referral-order subscriber)</pre>
 */
public class ReferralOrderIndexingAdvice extends AbstractIndexingAdvice<ReferralOrder> {

	static final Set<String> TRIGGER_METHODS = new HashSet<>(Arrays.asList(
	        "saveOrder", "saveRetrospectiveOrder", "voidOrder", "unvoidOrder", "purgeOrder"));

	static final Set<String> PURGE_METHODS = Collections.singleton("purgeOrder");

	@Override
	protected Class<ReferralOrder> getSupportedType() {
		return ReferralOrder.class;
	}

	@Override
	protected ReferralOrderRecordSerializer serializer() {
		return Context.getRegisteredComponent("querystore.serializer.referral_order",
		        ReferralOrderRecordSerializer.class);
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
