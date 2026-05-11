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
import org.openmrs.MedicationDispense;
import org.openmrs.api.MedicationDispenseService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ImmediateDispatcher;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.RecordingService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ZeroEmbedder;
import org.openmrs.module.querystore.model.QueryDocument;
import org.openmrs.module.querystore.serialization.MedicationDispenseRecordSerializer;

public class MedicationDispenseIndexingAdviceTest {

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
	public void saveMedicationDispense_indexes() throws Throwable {
		MedicationDispense md = md("md-1", false);
		advice.afterReturning(md,
		        MedicationDispenseService.class.getMethod("saveMedicationDispense",
		                MedicationDispense.class),
		        new Object[]{md}, null);
		assertEquals(1, service.indexed.size());
	}

	@Test
	public void voidMedicationDispense_deletes() throws Throwable {
		MedicationDispense md = md("md-2", true);
		advice.afterReturning(md,
		        MedicationDispenseService.class.getMethod("voidMedicationDispense",
		                MedicationDispense.class, String.class),
		        new Object[]{md, "reason"}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void purgeMedicationDispense_deletes() throws Throwable {
		MedicationDispense md = md("md-3", false);
		advice.afterReturning(null,
		        MedicationDispenseService.class.getMethod("purgeMedicationDispense",
		                MedicationDispense.class),
		        new Object[]{md}, null);
		assertEquals(1, service.deleted.size());
	}

	private static MedicationDispense md(String uuid, boolean voided) {
		MedicationDispense d = new MedicationDispense();
		d.setUuid(uuid);
		d.setVoided(voided);
		return d;
	}

	private static class StubSerializer extends MedicationDispenseRecordSerializer {
		@Override
		protected void populate(MedicationDispense record, QueryDocument doc) {
			doc.setText("stub-text");
		}
	}

	private static class TestableAdvice extends MedicationDispenseIndexingAdvice {
		private final MedicationDispenseRecordSerializer serializer;
		private final BridgeIndexer indexer;
		private final AfterCommitDispatcher dispatcher;

		TestableAdvice(MedicationDispenseRecordSerializer s, BridgeIndexer i, AfterCommitDispatcher d) {
			this.serializer = s;
			this.indexer = i;
			this.dispatcher = d;
		}

		@Override protected MedicationDispenseRecordSerializer serializer() { return serializer; }
		@Override BridgeIndexer indexer() { return indexer; }
		@Override AfterCommitDispatcher dispatcher() { return dispatcher; }
	}
}
