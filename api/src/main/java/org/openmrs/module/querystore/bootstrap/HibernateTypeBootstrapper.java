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

	/**
	 * HQL expression resolving the entity's patient UUID, referenced as {@code e.<path>.uuid}. Used
	 * by the per-patient fetch path on lazy projection (ADR Open Question: Initial backfill /
	 * bootstrap). Default {@code e.patient.uuid} fits every type whose Hibernate mapping exposes a
	 * {@code patient} association ({@link org.openmrs.Encounter}, {@link org.openmrs.Visit}, all
	 * {@link org.openmrs.Order} subtypes, {@link org.openmrs.Condition}, {@link org.openmrs.Diagnosis},
	 * {@link org.openmrs.Allergy}, {@link org.openmrs.PatientProgram},
	 * {@link org.openmrs.MedicationDispense}). {@link org.openmrs.Obs} has no {@code patient}
	 * association — only {@code person} — and overrides to {@code e.person.uuid}; the Person/Patient
	 * UUID coincidence (Patient extends Person) is what makes the filter resolve correctly.
	 * {@link org.openmrs.Patient} is itself the entity and overrides to {@code e.uuid}.
	 */
	protected String patientAssociationExpr() {
		return "e.patient.uuid";
	}

	@Override
	protected final List<T> fetchPage(Instant afterDateChanged, String afterUuid, int pageSize) {
		Class<T> entityType = getSerializer().getSupportedType();
		String entityName = entityType.getSimpleName();
		String dateExpr = cursorDateExpr();
		// Use the modern parameterized Query API via the underlying SessionFactory; DbSession's
		// createQuery returns the legacy raw type.
		String patientExpr = patientAssociationExpr();
		Session session = sessionFactory.getHibernateSessionFactory().getCurrentSession();
		Query<T> q;
		if (afterDateChanged == null) {
			q = session.createQuery(firstPageHql(entityName, dateExpr, patientExpr), entityType);
		} else {
			q = session.createQuery(afterCursorHql(entityName, dateExpr, patientExpr), entityType);
			q.setParameter("cursor", Date.from(afterDateChanged));
			q.setParameter("afterUuid", afterUuid != null ? afterUuid : "");
		}
		q.setMaxResults(pageSize);
		return q.list();
	}

	@Override
	protected final List<T> fetchPageForPatient(String patientUuid, Instant afterDateChanged, String afterUuid,
	                                            int pageSize) {
		Class<T> entityType = getSerializer().getSupportedType();
		String entityName = entityType.getSimpleName();
		String dateExpr = cursorDateExpr();
		String patientExpr = patientAssociationExpr();
		Session session = sessionFactory.getHibernateSessionFactory().getCurrentSession();
		Query<T> q;
		if (afterDateChanged == null) {
			q = session.createQuery(firstPagePerPatientHql(entityName, dateExpr, patientExpr), entityType);
		} else {
			q = session.createQuery(afterCursorPerPatientHql(entityName, dateExpr, patientExpr), entityType);
			q.setParameter("cursor", Date.from(afterDateChanged));
			q.setParameter("afterUuid", afterUuid != null ? afterUuid : "");
		}
		q.setParameter("patientUuid", patientUuid);
		q.setMaxResults(pageSize);
		return q.list();
	}

	// Package-private so a unit test can pin the HQL shape without invoking Hibernate.
	//
	// The "AND <patientExpr> IS NOT NULL" clause excludes rows whose patient/person FK is dangling —
	// an orphan a SQL-dump load can leave behind (a diagnosis/visit pointing at a patient_id with no
	// patient row). Navigating the association forces an INNER JOIN that drops such rows at the SQL
	// level. Without it, q.list() eager-materializes the missing association and throws
	// FetchNotFoundException, failing the ENTIRE type (per-record skip in projectOne can't help — the
	// failure is at page fetch, before projection). The per-patient scan is already orphan-safe for
	// the same reason (it navigates the same expr via "= :patientUuid"); this brings the global scan
	// to parity. Orphan rows are unindexable anyway (no patient to scope them to), so excluding them
	// is the correct "skip the bad record" behavior.
	static String firstPageHql(String entityName, String dateExpr, String patientExpr) {
		return "FROM " + entityName + " e WHERE e.voided = false "
		        + "AND " + patientExpr + " IS NOT NULL "
		        + "ORDER BY " + dateExpr + " ASC, e.uuid ASC";
	}

	static String afterCursorHql(String entityName, String dateExpr, String patientExpr) {
		// COALESCE in WHERE so a record whose dateChanged is null still progresses past the cursor on
		// dateCreated alone; the uuid tie-breaker handles records sharing the same effective timestamp.
		return "FROM " + entityName + " e WHERE e.voided = false "
		        + "AND " + patientExpr + " IS NOT NULL AND ("
		        + dateExpr + " > :cursor "
		        + "OR (" + dateExpr + " = :cursor AND e.uuid > :afterUuid)) "
		        + "ORDER BY " + dateExpr + " ASC, e.uuid ASC";
	}

	static String firstPagePerPatientHql(String entityName, String dateExpr, String patientExpr) {
		return "FROM " + entityName + " e WHERE e.voided = false "
		        + "AND " + patientExpr + " = :patientUuid "
		        + "ORDER BY " + dateExpr + " ASC, e.uuid ASC";
	}

	static String afterCursorPerPatientHql(String entityName, String dateExpr, String patientExpr) {
		return "FROM " + entityName + " e WHERE e.voided = false "
		        + "AND " + patientExpr + " = :patientUuid AND ("
		        + dateExpr + " > :cursor "
		        + "OR (" + dateExpr + " = :cursor AND e.uuid > :afterUuid)) "
		        + "ORDER BY " + dateExpr + " ASC, e.uuid ASC";
	}
}
