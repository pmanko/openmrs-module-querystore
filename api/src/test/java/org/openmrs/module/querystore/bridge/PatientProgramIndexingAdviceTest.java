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
import org.openmrs.PatientProgram;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ImmediateDispatcher;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.RecordingService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ZeroEmbedder;
import org.openmrs.module.querystore.model.QueryDocument;
import org.openmrs.module.querystore.serialization.PatientProgramRecordSerializer;

public class PatientProgramIndexingAdviceTest {

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
	public void savePatientProgram_indexes() throws Throwable {
		PatientProgram pp = program("pp-1", false);
		advice.afterReturning(pp,
		        ProgramWorkflowService.class.getMethod("savePatientProgram", PatientProgram.class),
		        new Object[]{pp}, null);
		assertEquals(1, service.indexed.size());
	}

	@Test
	public void voidPatientProgram_deletes() throws Throwable {
		PatientProgram pp = program("pp-2", true);
		advice.afterReturning(pp,
		        ProgramWorkflowService.class.getMethod("voidPatientProgram", PatientProgram.class,
		                String.class),
		        new Object[]{pp, "reason"}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void purgePatientProgram_deletes() throws Throwable {
		PatientProgram pp = program("pp-3", false);
		advice.afterReturning(null,
		        ProgramWorkflowService.class.getMethod("purgePatientProgram", PatientProgram.class),
		        new Object[]{pp}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void saveProgram_notAdvised() throws Throwable {
		// "saveProgram" is not in the patient-program advice's trigger-name set. The name filter
		// rejects before the type guard runs.
		org.openmrs.Program p = new org.openmrs.Program();
		advice.afterReturning(p,
		        ProgramWorkflowService.class.getMethod("saveProgram", org.openmrs.Program.class),
		        new Object[]{p}, null);
		assertEquals(0, dispatcher.count);
	}

	private static PatientProgram program(String uuid, boolean voided) {
		PatientProgram pp = new PatientProgram();
		pp.setUuid(uuid);
		pp.setVoided(voided);
		return pp;
	}

	private static class StubSerializer extends PatientProgramRecordSerializer {
		@Override
		protected void populate(PatientProgram record, QueryDocument doc) {
			doc.setText("stub-text");
		}
	}

	private static class TestableAdvice extends PatientProgramIndexingAdvice {
		private final PatientProgramRecordSerializer serializer;
		private final BridgeIndexer indexer;
		private final AfterCommitDispatcher dispatcher;

		TestableAdvice(PatientProgramRecordSerializer s, BridgeIndexer i, AfterCommitDispatcher d) {
			this.serializer = s;
			this.indexer = i;
			this.dispatcher = d;
		}

		@Override protected PatientProgramRecordSerializer serializer() { return serializer; }
		@Override BridgeIndexer indexer() { return indexer; }
		@Override AfterCommitDispatcher dispatcher() { return dispatcher; }
	}
}
