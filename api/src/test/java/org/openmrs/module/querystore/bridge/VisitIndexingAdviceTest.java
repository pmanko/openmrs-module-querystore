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
import org.openmrs.Visit;
import org.openmrs.api.VisitService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ImmediateDispatcher;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.RecordingService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ZeroEmbedder;
import org.openmrs.module.querystore.model.QueryDocument;
import org.openmrs.module.querystore.serialization.VisitRecordSerializer;

public class VisitIndexingAdviceTest {

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
	public void saveVisit_indexes() throws Throwable {
		Visit v = visit("v-1", false);
		advice.afterReturning(v, VisitService.class.getMethod("saveVisit", Visit.class),
		        new Object[]{v}, null);
		assertEquals(1, service.indexed.size());
	}

	@Test
	public void voidVisit_deletes() throws Throwable {
		Visit v = visit("v-2", true);
		advice.afterReturning(v,
		        VisitService.class.getMethod("voidVisit", Visit.class, String.class),
		        new Object[]{v, "reason"}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void purgeVisit_deletes() throws Throwable {
		Visit v = visit("v-3", false);
		advice.afterReturning(null, VisitService.class.getMethod("purgeVisit", Visit.class),
		        new Object[]{v}, null);
		assertEquals(1, service.deleted.size());
		assertEquals("non-patient purge must not trigger cross-type bulk-delete",
		        0, service.bulkDeletedPatients.size());
	}

	@Test
	public void saveVisitType_notAdvised() throws Throwable {
		// "saveVisitType" is not in the visit advice's trigger-name set. The trigger-name filter
		// rejects before the type guard runs.
		org.openmrs.VisitType vt = new org.openmrs.VisitType();
		advice.afterReturning(vt,
		        VisitService.class.getMethod("saveVisitType", org.openmrs.VisitType.class),
		        new Object[]{vt}, null);
		assertEquals(0, dispatcher.count);
	}

	private static Visit visit(String uuid, boolean voided) {
		Visit v = new Visit();
		v.setUuid(uuid);
		v.setVoided(voided);
		return v;
	}

	private static class StubSerializer extends VisitRecordSerializer {
		@Override
		protected void populate(Visit record, QueryDocument doc) {
			doc.setText("stub-text");
		}
	}

	private static class TestableAdvice extends VisitIndexingAdvice {
		private final VisitRecordSerializer serializer;
		private final BridgeIndexer indexer;
		private final AfterCommitDispatcher dispatcher;

		TestableAdvice(VisitRecordSerializer s, BridgeIndexer i, AfterCommitDispatcher d) {
			this.serializer = s;
			this.indexer = i;
			this.dispatcher = d;
		}

		@Override protected VisitRecordSerializer serializer() { return serializer; }
		@Override BridgeIndexer indexer() { return indexer; }
		@Override AfterCommitDispatcher dispatcher() { return dispatcher; }
	}
}
