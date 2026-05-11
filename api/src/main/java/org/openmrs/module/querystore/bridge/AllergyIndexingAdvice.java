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

import org.openmrs.Allergy;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.serialization.AllergyRecordSerializer;

/**
 * Migration-bridge advice on the allergy methods of {@link org.openmrs.api.PatientService} —
 * {@code saveAllergy}, {@code voidAllergy}, and {@code removeAllergy}. Two oddities relative to
 * the standard save/void/unvoid/purge shape:
 * <ul>
 *   <li>{@code PatientService} has no {@code unvoidAllergy} method. An allergy that gets unvoided
 *   does so by setting {@code voided = false} and calling {@code saveAllergy} — covered by the
 *   save trigger and the per-record voided policy.</li>
 *   <li>{@code removeAllergy(Allergy, String)} is the purge equivalent. Naming reflects core's
 *   API; the read-store effect is the same as {@code purgeX}.</li>
 * </ul>
 * The {@code instanceof Allergy} guard keeps this advice from acting on the {@code Patient}
 * methods that share the {@code PatientService} surface.
 *
 * <p><b>{@code setAllergies} is not covered.</b> {@code PatientServiceImpl.setAllergies} calls a
 * private {@code voidAllergy(Allergy)} on {@code this} via {@code invokespecial} when diffing the
 * old allergy set against the new one — that bypass doesn't re-enter Spring AOP, so bulk
 * allergy-set updates via {@code setAllergies} miss the bridge. Same "self-call skips AOP"
 * cascade-gap shape ADR Decision 12 accepts for the bridge window.
 * <pre>Removal trigger: TBD (events-first allergy subscriber)</pre>
 */
public class AllergyIndexingAdvice extends AbstractIndexingAdvice<Allergy> {

	static final Set<String> TRIGGER_METHODS = new HashSet<>(Arrays.asList(
	        "saveAllergy", "voidAllergy", "removeAllergy"));

	static final Set<String> PURGE_METHODS = Collections.singleton("removeAllergy");

	@Override
	protected Class<Allergy> getSupportedType() {
		return Allergy.class;
	}

	@Override
	protected AllergyRecordSerializer serializer() {
		return Context.getRegisteredComponent("querystore.serializer.allergy",
		        AllergyRecordSerializer.class);
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
