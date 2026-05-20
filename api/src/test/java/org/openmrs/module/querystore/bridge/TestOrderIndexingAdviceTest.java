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
import org.openmrs.DrugOrder;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderContext;
import org.openmrs.api.OrderService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ImmediateDispatcher;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.RecordingService;
import org.openmrs.module.querystore.bridge.BridgeAdviceTestSupport.ZeroEmbedder;
import org.openmrs.module.querystore.model.QueryDocument;
import org.openmrs.module.querystore.serialization.TestOrderRecordSerializer;

public class TestOrderIndexingAdviceTest {

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
	public void saveOrder_testOrder_indexes() throws Throwable {
		TestOrder order = testOrder("to-1", false);
		advice.afterReturning(order,
		        OrderService.class.getMethod("saveOrder", Order.class, OrderContext.class),
		        new Object[]{order, null}, null);
		assertEquals(1, service.indexed.size());
	}

	@Test
	public void saveOrder_drugOrder_notAdvised() throws Throwable {
		DrugOrder drugOrder = new DrugOrder();
		drugOrder.setUuid("do-x");
		advice.afterReturning(drugOrder,
		        OrderService.class.getMethod("saveOrder", Order.class, OrderContext.class),
		        new Object[]{drugOrder, null}, null);
		assertEquals(0, dispatcher.count);
	}

	@Test
	public void voidOrder_deletes() throws Throwable {
		TestOrder order = testOrder("to-2", true);
		advice.afterReturning(order,
		        OrderService.class.getMethod("voidOrder", Order.class, String.class),
		        new Object[]{order, "reason"}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void purgeOrder_deletes() throws Throwable {
		TestOrder order = testOrder("to-3", false);
		advice.afterReturning(null,
		        OrderService.class.getMethod("purgeOrder", Order.class),
		        new Object[]{order}, null);
		assertEquals(1, service.deleted.size());
		assertEquals("non-patient purge must not trigger cross-type bulk-delete",
		        0, service.bulkDeletedPatients.size());
	}

	private static TestOrder testOrder(String uuid, boolean voided) {
		TestOrder o = new TestOrder();
		o.setUuid(uuid);
		o.setVoided(voided);
		return o;
	}

	private static class StubSerializer extends TestOrderRecordSerializer {
		@Override
		protected void populate(TestOrder record, QueryDocument doc) {
			doc.setText("stub-text");
		}
	}

	private static class TestableAdvice extends TestOrderIndexingAdvice {
		private final TestOrderRecordSerializer serializer;
		private final BridgeIndexer indexer;
		private final AfterCommitDispatcher dispatcher;

		TestableAdvice(TestOrderRecordSerializer s, BridgeIndexer i, AfterCommitDispatcher d) {
			this.serializer = s;
			this.indexer = i;
			this.dispatcher = d;
		}

		@Override protected TestOrderRecordSerializer serializer() { return serializer; }
		@Override BridgeIndexer indexer() { return indexer; }
		@Override AfterCommitDispatcher dispatcher() { return dispatcher; }
	}
}
