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
import org.openmrs.module.querystore.serialization.AllergyRecordSerializer;

public class AllergyIndexingAdviceTest {

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
	public void saveAllergy_indexes() throws Throwable {
		Allergy a = allergy("a-1", false);
		// saveAllergy returns void — return value is null, args[0] supplies the entity.
		advice.afterReturning(null,
		        PatientService.class.getMethod("saveAllergy", Allergy.class),
		        new Object[]{a}, null);
		assertEquals(1, service.indexed.size());
	}

	@Test
	public void voidAllergy_deletes() throws Throwable {
		Allergy a = allergy("a-2", true);
		advice.afterReturning(null,
		        PatientService.class.getMethod("voidAllergy", Allergy.class, String.class),
		        new Object[]{a, "reason"}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void removeAllergy_deletes() throws Throwable {
		// removeAllergy is the purge equivalent — the type-token guard and PURGE_METHODS set
		// route it to delete regardless of the (non-voided) flag.
		Allergy a = allergy("a-3", false);
		advice.afterReturning(null,
		        PatientService.class.getMethod("removeAllergy", Allergy.class, String.class),
		        new Object[]{a, "reason"}, null);
		assertEquals(1, service.deleted.size());
		assertEquals("non-patient purge must not trigger cross-type bulk-delete",
		        0, service.bulkDeletedPatients.size());
	}

	@Test
	public void savePatient_notAdvised() throws Throwable {
		// PatientService has both allergy and patient methods. "savePatient" is not in the
		// allergy advice's trigger-name set, so it's rejected by the name filter before the type
		// guard runs — this keeps the allergy advice from shadowing the patient advice.
		Patient p = new Patient();
		p.setUuid("patient-x");
		advice.afterReturning(p,
		        PatientService.class.getMethod("savePatient", Patient.class),
		        new Object[]{p}, null);
		assertEquals(0, dispatcher.count);
	}

	private static Allergy allergy(String uuid, boolean voided) {
		Allergy a = new Allergy();
		a.setUuid(uuid);
		a.setVoided(voided);
		return a;
	}

	private static class StubSerializer extends AllergyRecordSerializer {
		@Override
		protected void populate(Allergy record, QueryDocument doc) {
			doc.setText("stub-text");
		}
	}

	private static class TestableAdvice extends AllergyIndexingAdvice {
		private final AllergyRecordSerializer serializer;
		private final BridgeIndexer indexer;
		private final AfterCommitDispatcher dispatcher;

		TestableAdvice(AllergyRecordSerializer s, BridgeIndexer i, AfterCommitDispatcher d) {
			this.serializer = s;
			this.indexer = i;
			this.dispatcher = d;
		}

		@Override protected AllergyRecordSerializer serializer() { return serializer; }
		@Override BridgeIndexer indexer() { return indexer; }
		@Override AfterCommitDispatcher dispatcher() { return dispatcher; }
	}
}
