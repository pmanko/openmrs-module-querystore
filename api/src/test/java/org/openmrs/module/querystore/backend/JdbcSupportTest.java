/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;

import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.jdbc.ReturningWork;
import org.hibernate.jdbc.Work;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.openmrs.api.db.hibernate.DbSessionFactory;

/**
 * Pins the {@link JdbcSupport#inTransaction} contract: commit on success, rollback on any
 * unchecked throwable, session always closed, and rollback failures captured as suppressed so
 * the original exception is what callers see. The helper is small but load-bearing — every
 * querystore JDBC write goes through it — so the failure paths are worth nailing down explicitly.
 */
public class JdbcSupportTest {

	private DbSessionFactory dbSessionFactory;

	private SessionFactory sessionFactory;

	private StatelessSession session;

	private Transaction tx;

	private Connection connection;

	@Before
	public void setUp() {
		dbSessionFactory = mock(DbSessionFactory.class);
		sessionFactory = mock(SessionFactory.class);
		session = mock(StatelessSession.class);
		tx = mock(Transaction.class);
		connection = mock(Connection.class);

		when(dbSessionFactory.getHibernateSessionFactory()).thenReturn(sessionFactory);
		when(sessionFactory.openStatelessSession()).thenReturn(session);
		when(session.beginTransaction()).thenReturn(tx);
		when(session.doReturningWork(any())).thenAnswer(inv -> {
			ReturningWork<?> work = inv.getArgument(0);
			return work.execute(connection);
		});
	}

	@Test
	public void inTransaction_happyPath_commitsAndClosesSession() {
		String result = JdbcSupport.inTransaction(dbSessionFactory, conn -> {
			assertSame(connection, conn);
			return "ok";
		});

		assertEquals("ok", result);
		verify(tx, never()).rollback();
		// commit MUST happen before close; reversing the order would silently discard writes
		// because the connection returns to the pool with an uncommitted transaction.
		InOrder order = inOrder(tx, session);
		order.verify(tx).commit();
		order.verify(session).close();
	}

	@Test
	public void inTransaction_workThrowsRuntimeException_rollsBackAndPropagates() {
		when(tx.isActive()).thenReturn(true);
		IllegalStateException thrown = new IllegalStateException("boom");

		// Cast resolves the void-vs-returning overload ambiguity for a lambda whose body is only
		// `throw` (both void- and value-compatible per JLS §15.27.2).
		try {
			JdbcSupport.inTransaction(dbSessionFactory, (Work) conn -> {
				throw thrown;
			});
			fail("expected exception to propagate");
		}
		catch (IllegalStateException e) {
			assertSame(thrown, e);
		}

		verify(tx, never()).commit();
		// rollback MUST happen before close; same reasoning as the commit-before-close pin.
		InOrder order = inOrder(tx, session);
		order.verify(tx).rollback();
		order.verify(session).close();
	}

	@Test
	public void inTransaction_workThrowsError_stillRollsBackAndPropagates() {
		// Error is included in the catch so a stale autocommit-off connection isn't released to
		// the pool with an uncommitted transaction. Pin the behaviour: OutOfMemoryError doesn't
		// leak past the helper without a rollback attempt.
		when(tx.isActive()).thenReturn(true);
		OutOfMemoryError thrown = new OutOfMemoryError("simulated");

		try {
			JdbcSupport.inTransaction(dbSessionFactory, (Work) conn -> {
				throw thrown;
			});
			fail("expected Error to propagate");
		}
		catch (OutOfMemoryError e) {
			assertSame(thrown, e);
		}

		verify(tx).rollback();
		verify(session).close();
	}

	@Test
	public void inTransaction_commitThrows_propagatesCommitException() {
		RuntimeException commitFailure = new RuntimeException("commit failed");
		doThrow(commitFailure).when(tx).commit();
		when(tx.isActive()).thenReturn(true);

		try {
			JdbcSupport.inTransaction(dbSessionFactory, conn -> "value");
			fail("expected commit failure to propagate");
		}
		catch (RuntimeException e) {
			assertSame(commitFailure, e);
		}

		verify(tx).rollback();
		verify(session).close();
	}

	@Test
	public void inTransaction_rollbackThrows_originalExceptionWinsWithSuppressedRollback() {
		when(tx.isActive()).thenReturn(true);
		RuntimeException original = new RuntimeException("original");
		RuntimeException rollbackFailure = new RuntimeException("rollback failed");
		doThrow(rollbackFailure).when(tx).rollback();

		try {
			JdbcSupport.inTransaction(dbSessionFactory, (Work) conn -> {
				throw original;
			});
			fail("expected original to propagate");
		}
		catch (RuntimeException e) {
			assertSame("rollback failure must not mask the original", original, e);
			assertEquals(1, e.getSuppressed().length);
			assertSame(rollbackFailure, e.getSuppressed()[0]);
		}

		verify(session).close();
	}

	@Test
	public void inTransaction_rollbackSkippedWhenTransactionInactive() {
		when(tx.isActive()).thenReturn(false);
		RuntimeException thrown = new RuntimeException("after commit-failure that already deactivated");

		try {
			JdbcSupport.inTransaction(dbSessionFactory, (Work) conn -> {
				throw thrown;
			});
			fail();
		}
		catch (RuntimeException e) {
			assertSame(thrown, e);
		}

		verify(tx, never()).rollback();
		verify(session).close();
	}

	@Test
	public void inTransaction_workOverload_executesAndCommits() {
		JdbcSupport.inTransaction(dbSessionFactory, conn -> {
			assertSame(connection, conn);
		});

		verify(tx).commit();
		verify(session, atLeastOnce()).doReturningWork(any());
		verify(session).close();
	}
}
