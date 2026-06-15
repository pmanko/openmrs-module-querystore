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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Date;

import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ImmediateDispatcher;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.RecordingService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ZeroEmbedder;
import org.openmrs.module.querystore.serialization.EncounterRecordSerializer;

/**
 * Verifies the {@code querystore.syncMode} gate in {@link AbstractIndexingAdvice}: when the AOP path
 * is gated off (sync mode {@code events}), an advised save produces no index write. ADR Decision 12,
 * "Runtime sync-mode selection." Exercises the gate via the overridable {@link
 * AbstractIndexingAdvice#aopEnabled()} so no running OpenMRS context is required.
 */
public class SyncModeGateTest {

	@Test
	public void afterReturning_aopEnabled_indexes() throws Exception {
		RecordingService service = new RecordingService();
		GateAdvice advice = new GateAdvice(true, service);

		advice.afterReturning(encounter(), saveEncounter(), new Object[] { encounter() }, null);

		assertEquals("AOP enabled: the save should project a document", 1, service.indexed.size());
	}

	@Test
	public void afterReturning_aopGatedOff_doesNotIndex() throws Exception {
		RecordingService service = new RecordingService();
		GateAdvice advice = new GateAdvice(false, service);

		advice.afterReturning(encounter(), saveEncounter(), new Object[] { encounter() }, null);

		assertTrue("AOP gated off (sync mode=events): no document should be projected",
		    service.indexed.isEmpty());
		assertTrue("AOP gated off: nothing dispatched", service.deleted.isEmpty());
	}

	@Test
	public void aopEnabled_defaultsToTrue_whenResolverUnavailable() {
		// With no running OpenMRS context the resolver lookup throws, and the gate must fail safe to
		// AOP-on. This is the load-bearing default that lets the per-type advice unit tests run
		// without a context — assert it directly rather than only by proxy.
		assertTrue(new EncounterIndexingAdvice().aopEnabled());
	}

	private static Method saveEncounter() throws NoSuchMethodException {
		return EncounterService.class.getMethod("saveEncounter", Encounter.class);
	}

	private static Encounter encounter() {
		Encounter enc = new Encounter();
		enc.setUuid("gate-enc");
		enc.setEncounterDatetime(new Date());
		Patient patient = new Patient();
		patient.setUuid("patient-uuid");
		enc.setPatient(patient);
		EncounterType type = new EncounterType();
		type.setUuid("type-uuid");
		type.setName("Adult Outpatient Visit");
		enc.setEncounterType(type);
		return enc;
	}

	/** Bridge advice with an injectable gate verdict and the recording pipeline from the support. */
	private static final class GateAdvice extends EncounterIndexingAdvice {

		private final boolean aopEnabled;

		private final EncounterRecordSerializer serializer = new EncounterRecordSerializer();

		private final BridgeIndexer indexer;

		private final AfterCommitDispatcher dispatcher = new ImmediateDispatcher();

		GateAdvice(boolean aopEnabled, RecordingService service) {
			this.aopEnabled = aopEnabled;
			this.indexer = new BridgeIndexer(service, new ZeroEmbedder());
		}

		@Override
		boolean aopEnabled() {
			return aopEnabled;
		}

		@Override
		protected EncounterRecordSerializer serializer() {
			return serializer;
		}

		@Override
		BridgeIndexer indexer() {
			return indexer;
		}

		@Override
		AfterCommitDispatcher dispatcher() {
			return dispatcher;
		}
	}
}
