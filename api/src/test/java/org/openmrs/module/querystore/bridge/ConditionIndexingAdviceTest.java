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
import org.openmrs.Condition;
import org.openmrs.api.ConditionService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ImmediateDispatcher;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.RecordingService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ZeroEmbedder;
import org.openmrs.module.querystore.model.QueryDocument;
import org.openmrs.module.querystore.serialization.ConditionRecordSerializer;

/**
 * Per-class advice configuration verification. The behavioural contract of
 * {@link AbstractIndexingAdvice} is exercised end-to-end by
 * {@link ObsIndexingAdviceTest} and {@link EncounterIndexingAdviceTest}; what's unique per
 * subclass is the trigger-method set, the type token, and the serializer wiring. These tests
 * therefore use a stub serializer (no entity-fixture coupling) and verify that each named trigger
 * routes the entity through the dispatch pipeline.
 */
public class ConditionIndexingAdviceTest {

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
	public void saveCondition_indexes() throws Throwable {
		Condition c = condition("c-1", false);
		advice.afterReturning(c, method("saveCondition"), new Object[]{c}, null);
		assertEquals(1, service.indexed.size());
		assertEquals("c-1", service.indexed.get(0).getResourceUuid());
	}

	@Test
	public void voidCondition_deletes() throws Throwable {
		Condition c = condition("c-2", true);
		advice.afterReturning(c, method("voidCondition", String.class), new Object[]{c, "reason"}, null);
		assertEquals(1, service.deleted.size());
		assertEquals("c-2", service.deleted.get(0)[1]);
	}

	@Test
	public void purgeCondition_deletes() throws Throwable {
		Condition c = condition("c-3", false);
		advice.afterReturning(null, method("purgeCondition"), new Object[]{c}, null);
		assertEquals(1, service.deleted.size());
		assertEquals("non-patient purge must not trigger cross-type bulk-delete",
		        0, service.bulkDeletedPatients.size());
	}

	@Test
	public void unrelatedMethod_ignored() throws Throwable {
		Condition c = condition("c-4", false);
		advice.afterReturning(c, ConditionService.class.getMethod("getCondition", Integer.class),
		        new Object[]{1}, null);
		assertEquals(0, dispatcher.count);
	}

	private static Method method(String name, Class<?>... extraArgs) throws NoSuchMethodException {
		Class<?>[] params = new Class<?>[1 + extraArgs.length];
		params[0] = Condition.class;
		System.arraycopy(extraArgs, 0, params, 1, extraArgs.length);
		return ConditionService.class.getMethod(name, params);
	}

	private static Condition condition(String uuid, boolean voided) {
		Condition c = new Condition();
		c.setUuid(uuid);
		c.setVoided(voided);
		return c;
	}

	/**
	 * Returns a fixed document keyed by the source UUID — bypasses the real serializer's
	 * dependency on a fully-populated concept/patient graph. The real
	 * {@link ConditionRecordSerializer} behaviour is pinned by its own serializer test.
	 */
	private static class StubSerializer extends ConditionRecordSerializer {
		@Override
		protected void populate(Condition record, QueryDocument doc) {
			doc.setText("stub-text");
		}
	}

	private static class TestableAdvice extends ConditionIndexingAdvice {
		private final ConditionRecordSerializer serializer;
		private final BridgeIndexer indexer;
		private final AfterCommitDispatcher dispatcher;

		TestableAdvice(ConditionRecordSerializer s, BridgeIndexer i, AfterCommitDispatcher d) {
			this.serializer = s;
			this.indexer = i;
			this.dispatcher = d;
		}

		@Override protected ConditionRecordSerializer serializer() { return serializer; }
		@Override BridgeIndexer indexer() { return indexer; }
		@Override AfterCommitDispatcher dispatcher() { return dispatcher; }
	}
}
