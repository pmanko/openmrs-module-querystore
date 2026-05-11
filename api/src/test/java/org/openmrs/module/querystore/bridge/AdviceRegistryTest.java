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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openmrs.api.ConditionService;
import org.openmrs.api.DiagnosisService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.MedicationDispenseService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.VisitService;

/**
 * Static-ish registry checks across all twelve bridge advices. Each per-type advice declares
 * trigger names as plain strings; a typo (e.g. {@code "saveDiganosis"}) wouldn't surface until
 * production. This test resolves every declared trigger against the corresponding service
 * interface and asserts the trigger name resolves to a method whose first parameter can hold the
 * advice's own {@link AbstractIndexingAdvice#getSupportedType()}.
 *
 * <p>Reading {@code getSupportedType()} from the advice rather than declaring an expected type
 * separately keeps the test honest: an accidental widening (e.g. {@code DrugOrderIndexingAdvice}
 * starting to return {@code Order.class}) would still be caught by the per-class subtype tests
 * that exercise the {@code instanceof} guard, and this test wouldn't silently pass against the
 * stale expectation.
 */
public class AdviceRegistryTest {

	@Test
	public void everyTriggerMethodExistsOnItsService() {
		Map<AbstractIndexingAdvice<?>, Class<?>> registry = registry();

		for (Map.Entry<AbstractIndexingAdvice<?>, Class<?>> entry : registry.entrySet()) {
			AbstractIndexingAdvice<?> advice = entry.getKey();
			Class<?> serviceClass = entry.getValue();
			Class<?> supportedType = advice.getSupportedType();
			List<Method> methods = Arrays.asList(serviceClass.getMethods());
			for (String triggerName : advice.triggerMethods()) {
				boolean match = methods.stream().anyMatch(m -> m.getName().equals(triggerName)
				        && m.getParameterCount() > 0
				        && m.getParameterTypes()[0].isAssignableFrom(supportedType));
				assertTrue(advice.getClass().getSimpleName() + " declares trigger '" + triggerName
				        + "' but no method with that name on " + serviceClass.getSimpleName()
				        + " accepts " + supportedType.getSimpleName() + " as the first arg",
				        match);
			}
		}
	}

	@Test
	public void purgeMethodsAreSubsetOfTriggers() {
		// Every purge method must also appear in the trigger set — the advice uses
		// triggerMethods().contains(name) to decide whether to act at all, then
		// purgeMethods().contains(name) to choose the unconditional-delete branch. A purge name
		// outside the trigger set would never fire.
		for (AbstractIndexingAdvice<?> advice : registry().keySet()) {
			assertFalse(advice.getClass().getSimpleName() + " purge set must be non-empty",
			        advice.purgeMethods().isEmpty());
			assertTrue(advice.getClass().getSimpleName() + " purge methods must be a subset of triggers",
			        advice.triggerMethods().containsAll(advice.purgeMethods()));
		}
	}

	private static Map<AbstractIndexingAdvice<?>, Class<?>> registry() {
		Map<AbstractIndexingAdvice<?>, Class<?>> r = new LinkedHashMap<>();
		r.put(new ObsIndexingAdvice(), ObsService.class);
		r.put(new EncounterIndexingAdvice(), EncounterService.class);
		r.put(new ConditionIndexingAdvice(), ConditionService.class);
		r.put(new DiagnosisIndexingAdvice(), DiagnosisService.class);
		r.put(new VisitIndexingAdvice(), VisitService.class);
		r.put(new AllergyIndexingAdvice(), PatientService.class);
		r.put(new PatientIndexingAdvice(), PatientService.class);
		r.put(new PatientProgramIndexingAdvice(), ProgramWorkflowService.class);
		r.put(new MedicationDispenseIndexingAdvice(), MedicationDispenseService.class);
		r.put(new DrugOrderIndexingAdvice(), OrderService.class);
		r.put(new TestOrderIndexingAdvice(), OrderService.class);
		r.put(new ReferralOrderIndexingAdvice(), OrderService.class);
		return r;
	}
}
