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

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Diagnosis;
import org.openmrs.api.DiagnosisService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ImmediateDispatcher;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.RecordingService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ZeroEmbedder;
import org.openmrs.module.querystore.model.QueryDocument;
import org.openmrs.module.querystore.serialization.DiagnosisRecordSerializer;

public class DiagnosisIndexingAdviceTest {

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
	public void save_indexes() throws Throwable {
		// DiagnosisService's save method is named "save" (not "saveDiagnosis"); the type guard
		// keeps saveDiagnosisAttributeType and similar from being advised.
		Diagnosis d = diagnosis("d-1", false);
		advice.afterReturning(d, DiagnosisService.class.getMethod("save", Diagnosis.class),
		        new Object[]{d}, null);
		assertEquals(1, service.indexed.size());
		assertEquals("d-1", service.indexed.get(0).getResourceUuid());
	}

	@Test
	public void voidDiagnosis_deletes() throws Throwable {
		Diagnosis d = diagnosis("d-2", true);
		advice.afterReturning(d,
		        DiagnosisService.class.getMethod("voidDiagnosis", Diagnosis.class, String.class),
		        new Object[]{d, "reason"}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void purgeDiagnosis_deletes() throws Throwable {
		Diagnosis d = diagnosis("d-3", false);
		advice.afterReturning(null,
		        DiagnosisService.class.getMethod("purgeDiagnosis", Diagnosis.class),
		        new Object[]{d}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void saveDiagnosisAttributeType_notAdvised() throws Throwable {
		// "saveDiagnosisAttributeType" is not in the trigger-name set ({"save", "voidDiagnosis",
		// ...}). The trigger-name filter rejects before the type guard ever runs.
		org.openmrs.DiagnosisAttributeType type = new org.openmrs.DiagnosisAttributeType();
		advice.afterReturning(type,
		        DiagnosisService.class.getMethod("saveDiagnosisAttributeType",
		                org.openmrs.DiagnosisAttributeType.class),
		        new Object[]{type}, null);
		assertEquals(0, dispatcher.count);
	}

	private static Diagnosis diagnosis(String uuid, boolean voided) {
		Diagnosis d = new Diagnosis();
		d.setUuid(uuid);
		d.setVoided(voided);
		return d;
	}

	private static class StubSerializer extends DiagnosisRecordSerializer {
		@Override
		protected void populate(Diagnosis record, QueryDocument doc) {
			doc.setText("stub-text");
		}
	}

	private static class TestableAdvice extends DiagnosisIndexingAdvice {
		private final DiagnosisRecordSerializer serializer;
		private final BridgeIndexer indexer;
		private final AfterCommitDispatcher dispatcher;

		TestableAdvice(DiagnosisRecordSerializer s, BridgeIndexer i, AfterCommitDispatcher d) {
			this.serializer = s;
			this.indexer = i;
			this.dispatcher = d;
		}

		@Override protected DiagnosisRecordSerializer serializer() { return serializer; }
		@Override BridgeIndexer indexer() { return indexer; }
		@Override AfterCommitDispatcher dispatcher() { return dispatcher; }
	}
}
