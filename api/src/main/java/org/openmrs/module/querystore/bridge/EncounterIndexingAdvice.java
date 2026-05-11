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

import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.serialization.EncounterRecordSerializer;

/**
 * Migration-bridge advice on {@link org.openmrs.api.EncounterService} (ADR Decision 12 "Migration
 * bridge"). Projects each {@code saveEncounter / voidEncounter / unvoidEncounter / purgeEncounter}
 * call through the {@code serialize → embed → index} pipeline.
 *
 * <p><b>Removal marker.</b> Delete this class and its {@code <advice>} entry when the events-first
 * subscriber for encounters ships and has been verified at parity.
 * <pre>Removal trigger: TBD (events-first encounter subscriber)</pre>
 *
 * <p><b>Obs cascade is handled by the obs advice, not here.</b> {@code voidEncounter} and
 * {@code purgeEncounter(encounter, true)} iterate the encounter's obs and route each through
 * {@code Context.getObsService().voidObs / purgeObs} — those calls re-enter Spring AOP and fire
 * {@link ObsIndexingAdvice}. This advice therefore only projects the encounter document itself;
 * the cascade-delete gap the ADR accepts for the bridge window is limited to obs that bypass
 * {@code ObsService} entirely (e.g., new obs persisted via Hibernate cascade when
 * {@code saveEncounter} runs).
 *
 * <p><b>{@code transferEncounter} is not covered.</b> {@code EncounterServiceImpl.transferEncounter}
 * invokes {@code voidEncounter} and {@code saveEncounter} on {@code this} via direct
 * {@code invokevirtual} rather than through {@code Context.getEncounterService()}, so the inner
 * calls do not re-enter Spring AOP. The original encounter therefore stays in the read store and
 * the copied encounter is not projected until the bootstrap reconciles. This is the same "AOP
 * misses self-cascade" shape ADR Decision 12 accepts for the bridge window; reconciliation under
 * the Sync-reliability open question is the long-term fix.
 */
public class EncounterIndexingAdvice extends AbstractIndexingAdvice<Encounter> {

	static final Set<String> TRIGGER_METHODS = new HashSet<>(Arrays.asList(
	        "saveEncounter", "voidEncounter", "unvoidEncounter", "purgeEncounter"));

	static final Set<String> PURGE_METHODS = Collections.singleton("purgeEncounter");

	@Override
	protected Class<Encounter> getSupportedType() {
		return Encounter.class;
	}

	@Override
	protected EncounterRecordSerializer serializer() {
		return Context.getRegisteredComponent("querystore.serializer.encounter",
		        EncounterRecordSerializer.class);
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
