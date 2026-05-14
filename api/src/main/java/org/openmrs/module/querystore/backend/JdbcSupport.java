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

import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.jdbc.ReturningWork;
import org.hibernate.jdbc.Work;
import org.openmrs.api.db.hibernate.DbSessionFactory;

/**
 * Boilerplate for the "open stateless session, run JDBC in a transaction, commit or rollback,
 * close session" pattern that every querystore JDBC consumer (MysqlBackendStore,
 * MysqlSchemaManager, BootstrapProgressDao) repeats. The transaction is required because
 * connections from {@link StatelessSession} come with autocommit disabled, so without an explicit
 * commit the writes are discarded when the session closes.
 *
 * <p>DDL caveat: MySQL implicitly commits DDL ({@code CREATE TABLE}, {@code DROP TABLE}, ...) at
 * the engine level. The surrounding Hibernate {@code Transaction.commit()} runs against an
 * already-closed transaction at the JDBC level and is a no-op. This means DDL failures cannot be
 * rolled back via this helper — that's a MySQL property, not a helper limitation. DML failures
 * roll back normally.
 */
public final class JdbcSupport {

	private JdbcSupport() {
	}

	/**
	 * Runs {@code work} inside a stateless-session-scoped transaction. Contract:
	 * <ul>
	 *   <li>Exceptions thrown by {@code work} (including {@link Error}) propagate to the caller
	 *       unwrapped, after {@code tx.rollback()} runs.</li>
	 *   <li>If {@code commit()} itself throws, the commit exception propagates; rollback is
	 *       attempted (guarded by {@code tx.isActive()}).</li>
	 *   <li>If {@code rollback()} throws during the exception path, the rollback failure attaches
	 *       as a suppressed exception on the original so the root cause isn't masked.</li>
	 *   <li>The session is always closed (try-with-resources); close-time failures attach as
	 *       suppressed if the body already threw.</li>
	 *   <li>The connection is <strong>not</strong> enlisted in any caller transaction — querystore
	 *       writes intentionally happen after commit of the originating operation (ADR Decision
	 *       12), and the {@code last_modified} freshness guard makes upserts idempotent.</li>
	 * </ul>
	 */
	public static <T> T inTransaction(DbSessionFactory sessionFactory, ReturningWork<T> work) {
		// try-with-resources so a close-time exception attaches as suppressed rather than
		// replacing the original thrown by the work or by tx.commit().
		try (StatelessSession session = sessionFactory.getHibernateSessionFactory().openStatelessSession()) {
			Transaction tx = session.beginTransaction();
			try {
				T result = session.doReturningWork(work);
				tx.commit();
				return result;
			}
			// `Error` is included because the helper is meant to be the central JDBC pattern;
			// leaving the transaction open on Error would return a connection to the pool with an
			// uncommitted transaction. Both branches rethrow the original after recording any
			// rollback failure as suppressed.
			catch (RuntimeException | Error e) {
				if (tx.isActive()) {
					try {
						tx.rollback();
					}
					catch (RuntimeException rollbackEx) {
						e.addSuppressed(rollbackEx);
					}
				}
				throw e;
			}
		}
	}

	/**
	 * Void-returning overload of {@link #inTransaction(DbSessionFactory, ReturningWork)}; same
	 * contract. Callers whose lambda body is a single {@code throw} must cast to {@link Work} to
	 * resolve the void-vs-returning overload ambiguity per JLS §15.27.2.
	 */
	public static void inTransaction(DbSessionFactory sessionFactory, Work work) {
		inTransaction(sessionFactory, conn -> {
			work.execute(conn);
			return null;
		});
	}
}
