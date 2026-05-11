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

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Allergy;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ImmediateDispatcher;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.RecordingService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ZeroEmbedder;
import org.openmrs.module.querystore.model.QueryDocument;
import org.openmrs.module.querystore.serialization.PatientRecordSerializer;

public class PatientIndexingAdviceTest {

	private RecordingService service;

	private ImmediateDispatcher dispatcher;

	private TestableAdvice advice;

	@Before
	public void setUp() {
		service = new RecordingService();
		dispatcher = new ImmediateDispatcher();
		BridgeIndexer indexer = new BridgeIndexer(service, new ZeroEmbedder());
		advice = new TestableAdvice(new StubSerializer(), indexer, dispatcher);
	}

	@Test
	public void savePatient_indexes() throws Throwable {
		Patient p = patient("p-1", false);
		advice.afterReturning(p, PatientService.class.getMethod("savePatient", Patient.class),
		        new Object[]{p}, null);
		assertEquals(1, service.indexed.size());
	}

	@Test
	public void voidPatient_deletes() throws Throwable {
		Patient p = patient("p-2", true);
		advice.afterReturning(p,
		        PatientService.class.getMethod("voidPatient", Patient.class, String.class),
		        new Object[]{p, "reason"}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void purgePatient_deletes() throws Throwable {
		Patient p = patient("p-3", false);
		// purgePatient returns void in the service interface; mirror that by passing null as
		// returnValue. entityFrom falls back to args[0] for void-returning advised methods.
		advice.afterReturning(null,
		        PatientService.class.getMethod("purgePatient", Patient.class),
		        new Object[]{p}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void saveAllergy_notAdvised() throws Throwable {
		// "saveAllergy" is not in the patient advice's trigger-name set, so it's rejected by the
		// name filter before the type guard runs. (The allergy advice handles saveAllergy.)
		Allergy a = new Allergy();
		a.setUuid("allergy-x");
		advice.afterReturning(null,
		        PatientService.class.getMethod("saveAllergy", Allergy.class),
		        new Object[]{a}, null);
		assertEquals(0, dispatcher.count);
	}

	private static Patient patient(String uuid, boolean voided) {
		Patient p = new Patient();
		p.setUuid(uuid);
		p.setVoided(voided);
		return p;
	}

	private static class StubSerializer extends PatientRecordSerializer {
		@Override
		protected void populate(Patient record, QueryDocument doc) {
			doc.setText("stub-text");
		}
	}

	private static class TestableAdvice extends PatientIndexingAdvice {
		private final PatientRecordSerializer serializer;
		private final BridgeIndexer indexer;
		private final AfterCommitDispatcher dispatcher;

		TestableAdvice(PatientRecordSerializer s, BridgeIndexer i, AfterCommitDispatcher d) {
			this.serializer = s;
			this.indexer = i;
			this.dispatcher = d;
		}

		@Override protected PatientRecordSerializer serializer() { return serializer; }
		@Override BridgeIndexer indexer() { return indexer; }
		@Override AfterCommitDispatcher dispatcher() { return dispatcher; }
	}
}
