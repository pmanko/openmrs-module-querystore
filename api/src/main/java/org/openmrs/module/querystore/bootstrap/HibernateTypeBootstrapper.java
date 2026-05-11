/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.bootstrap;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openmrs.api.db.hibernate.DbSessionFactory;

/**
 * {@link TypeBootstrapper} implementation that fetches pages via HQL against core's session. The
 * 12 core resource types share the same shape — {@link org.openmrs.BaseOpenmrsData} entity, paginate
 * by {@code dateChanged ?? dateCreated} ascending with {@code uuid} as tie-breaker, skip voided —
 * so the HQL is parameterized by entity name and lives here once instead of being copy-pasted into
 * every leaf. Subtypes provide only the serializer; the entity name is read from
 * {@code getSerializer().getSupportedType().getSimpleName()}.
 */
public abstract class HibernateTypeBootstrapper<T> extends TypeBootstrapper<T> {

	private final DbSessionFactory sessionFactory;

	protected HibernateTypeBootstrapper(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * HQL expression for the cursor's effective date, referenced as {@code e.<field>}. The default
	 * {@code COALESCE(e.dateChanged, e.dateCreated)} works for entities whose Hibernate mapping
	 * exposes both audit columns. {@code Obs.hbm.xml} and {@code Order.hbm.xml} (in OpenMRS 2.8+)
	 * do not map {@code dateChanged} — those subclasses must override to return {@code "e.dateCreated"}
	 * (or another monotonic timestamp) or HQL will throw {@code QueryException} at first fetch.
	 */
	protected String cursorDateExpr() {
		return "COALESCE(e.dateChanged, e.dateCreated)";
	}

	@Override
	protected final List<T> fetchPage(Instant afterDateChanged, String afterUuid, int pageSize) {
		Class<T> entityType = getSerializer().getSupportedType();
		String entityName = entityType.getSimpleName();
		String dateExpr = cursorDateExpr();
		// Use the modern parameterized Query API via the underlying SessionFactory; DbSession's
		// createQuery returns the legacy raw type.
		Session session = sessionFactory.getHibernateSessionFactory().getCurrentSession();
		Query<T> q;
		if (afterDateChanged == null) {
			q = session.createQuery(firstPageHql(entityName, dateExpr), entityType);
		} else {
			q = session.createQuery(afterCursorHql(entityName, dateExpr), entityType);
			q.setParameter("cursor", Date.from(afterDateChanged));
			q.setParameter("afterUuid", afterUuid != null ? afterUuid : "");
		}
		q.setMaxResults(pageSize);
		return q.list();
	}

	// Package-private so a unit test can pin the HQL shape without invoking Hibernate.
	static String firstPageHql(String entityName, String dateExpr) {
		return "FROM " + entityName + " e WHERE e.voided = false "
		        + "ORDER BY " + dateExpr + " ASC, e.uuid ASC";
	}

	static String afterCursorHql(String entityName, String dateExpr) {
		// COALESCE in WHERE so a record whose dateChanged is null still progresses past the cursor on
		// dateCreated alone; the uuid tie-breaker handles records sharing the same effective timestamp.
		return "FROM " + entityName + " e WHERE e.voided = false AND ("
		        + dateExpr + " > :cursor "
		        + "OR (" + dateExpr + " = :cursor AND e.uuid > :afterUuid)) "
		        + "ORDER BY " + dateExpr + " ASC, e.uuid ASC";
	}
}
