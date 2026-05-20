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
import org.openmrs.module.querystore.serialization.DrugOrderRecordSerializer;

public class DrugOrderIndexingAdviceTest {

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
	public void saveOrder_drugOrder_indexes() throws Throwable {
		DrugOrder order = drugOrder("do-1", false);
		advice.afterReturning(order,
		        OrderService.class.getMethod("saveOrder", Order.class, OrderContext.class),
		        new Object[]{order, null}, null);
		assertEquals(1, service.indexed.size());
	}

	@Test
	public void saveRetrospectiveOrder_indexes() throws Throwable {
		// saveRetrospectiveOrder is in the trigger set alongside saveOrder; retrospective entry
		// must produce a read-store document just like a normal save.
		DrugOrder order = drugOrder("do-r", false);
		advice.afterReturning(order,
		        OrderService.class.getMethod("saveRetrospectiveOrder", Order.class, OrderContext.class),
		        new Object[]{order, null}, null);
		assertEquals(1, service.indexed.size());
	}

	@Test
	public void saveOrder_testOrder_notAdvised() throws Throwable {
		// Three Order subtypes share OrderService; each advice's type guard must select only its
		// own subtype. A TestOrder must not trigger the DrugOrder advice.
		TestOrder testOrder = new TestOrder();
		testOrder.setUuid("to-x");
		advice.afterReturning(testOrder,
		        OrderService.class.getMethod("saveOrder", Order.class, OrderContext.class),
		        new Object[]{testOrder, null}, null);
		assertEquals(0, dispatcher.count);
	}

	@Test
	public void voidOrder_deletes() throws Throwable {
		DrugOrder order = drugOrder("do-2", true);
		advice.afterReturning(order,
		        OrderService.class.getMethod("voidOrder", Order.class, String.class),
		        new Object[]{order, "reason"}, null);
		assertEquals(1, service.deleted.size());
	}

	@Test
	public void purgeOrder_deletes() throws Throwable {
		DrugOrder order = drugOrder("do-3", false);
		advice.afterReturning(null,
		        OrderService.class.getMethod("purgeOrder", Order.class),
		        new Object[]{order}, null);
		assertEquals(1, service.deleted.size());
		assertEquals("non-patient purge must not trigger cross-type bulk-delete",
		        0, service.bulkDeletedPatients.size());
	}

	private static DrugOrder drugOrder(String uuid, boolean voided) {
		DrugOrder o = new DrugOrder();
		o.setUuid(uuid);
		o.setVoided(voided);
		return o;
	}

	private static class StubSerializer extends DrugOrderRecordSerializer {
		@Override
		protected void populate(DrugOrder record, QueryDocument doc) {
			doc.setText("stub-text");
		}
	}

	private static class TestableAdvice extends DrugOrderIndexingAdvice {
		private final DrugOrderRecordSerializer serializer;
		private final BridgeIndexer indexer;
		private final AfterCommitDispatcher dispatcher;

		TestableAdvice(DrugOrderRecordSerializer s, BridgeIndexer i, AfterCommitDispatcher d) {
			this.serializer = s;
			this.indexer = i;
			this.dispatcher = d;
		}

		@Override protected DrugOrderRecordSerializer serializer() { return serializer; }
		@Override BridgeIndexer indexer() { return indexer; }
		@Override AfterCommitDispatcher dispatcher() { return dispatcher; }
	}
}
