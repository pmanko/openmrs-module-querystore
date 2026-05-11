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

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.serialization.PatientRecordSerializer;

/**
 * Migration-bridge advice on the patient methods of {@link org.openmrs.api.PatientService}.
 *
 * <p>{@code mergePatients} is deliberately not in the trigger set — the patient-merge open
 * question (see {@code docs/adr.md#patient-merge-handling}) covers re-indexing and cross-patient
 * UUID rewrites on merge, which is structurally larger than this advice's scope. Until that lands,
 * a merged patient's read-store documents are reconciled by the bootstrap.
 * <pre>Removal trigger: TBD (events-first patient subscriber)</pre>
 */
public class PatientIndexingAdvice extends AbstractIndexingAdvice<Patient> {

	static final Set<String> TRIGGER_METHODS = new HashSet<>(Arrays.asList(
	        "savePatient", "voidPatient", "unvoidPatient", "purgePatient"));

	static final Set<String> PURGE_METHODS = Collections.singleton("purgePatient");

	@Override
	protected Class<Patient> getSupportedType() {
		return Patient.class;
	}

	@Override
	protected PatientRecordSerializer serializer() {
		return Context.getRegisteredComponent("querystore.serializer.patient",
		        PatientRecordSerializer.class);
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
