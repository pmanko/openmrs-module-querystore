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

import org.openmrs.Diagnosis;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.serialization.DiagnosisRecordSerializer;

/**
 * Migration-bridge advice on {@link org.openmrs.api.DiagnosisService}. The save method is named
 * {@code save} (not {@code saveDiagnosis}); the type-token guard on {@code args[0] instanceof
 * Diagnosis} keeps {@code saveDiagnosisAttributeType} and other same-named overloads from being
 * advised by mistake.
 * <pre>Removal trigger: TBD (events-first diagnosis subscriber)</pre>
 */
public class DiagnosisIndexingAdvice extends AbstractIndexingAdvice<Diagnosis> {

	static final Set<String> TRIGGER_METHODS = new HashSet<>(Arrays.asList(
	        "save", "voidDiagnosis", "unvoidDiagnosis", "purgeDiagnosis"));

	static final Set<String> PURGE_METHODS = Collections.singleton("purgeDiagnosis");

	@Override
	protected Class<Diagnosis> getSupportedType() {
		return Diagnosis.class;
	}

	@Override
	protected DiagnosisRecordSerializer serializer() {
		return Context.getRegisteredComponent("querystore.serializer.diagnosis",
		        DiagnosisRecordSerializer.class);
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
