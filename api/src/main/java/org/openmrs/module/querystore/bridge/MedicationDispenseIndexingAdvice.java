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

import org.openmrs.MedicationDispense;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.serialization.MedicationDispenseRecordSerializer;

/**
 * Migration-bridge advice on {@link org.openmrs.api.MedicationDispenseService}.
 * <pre>Removal trigger: TBD (events-first medication-dispense subscriber)</pre>
 */
public class MedicationDispenseIndexingAdvice extends AbstractIndexingAdvice<MedicationDispense> {

	static final Set<String> TRIGGER_METHODS = new HashSet<>(Arrays.asList(
	        "saveMedicationDispense", "voidMedicationDispense", "unvoidMedicationDispense",
	        "purgeMedicationDispense"));

	static final Set<String> PURGE_METHODS = Collections.singleton("purgeMedicationDispense");

	@Override
	protected Class<MedicationDispense> getSupportedType() {
		return MedicationDispense.class;
	}

	@Override
	protected MedicationDispenseRecordSerializer serializer() {
		return Context.getRegisteredComponent("querystore.serializer.medication_dispense",
		        MedicationDispenseRecordSerializer.class);
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
