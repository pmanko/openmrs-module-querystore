/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.backend.elasticsearch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.openmrs.module.querystore.QueryStoreConstants;
import org.openmrs.module.querystore.backend.Filter;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;

/**
 * Translates structured {@link Filter} predicates into Elasticsearch {@link Query} clauses. Top-level
 * fields ({@code patient_uuid}, {@code resource_uuid}, {@code record_date}) hit their dedicated
 * mapped fields; metadata fields hit {@code meta.<key>} mapped to {@code keyword} by the
 * dynamic_template in {@link ElasticsearchSchemaManager}. RANGE is intentionally restricted to
 * {@code record_date} to match the Lucene tier's v1 capability (see ADR Decision 3).
 */
final class ElasticsearchFilterTranslator {

	private static final Set<String> COLUMN_FIELDS = new HashSet<>(Arrays.asList(
	    QueryStoreConstants.FIELD_PATIENT_UUID,
	    QueryStoreConstants.FIELD_RESOURCE_UUID,
	    QueryStoreConstants.FIELD_RECORD_DATE));

	private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

	private ElasticsearchFilterTranslator() {
	}

	/**
	 * Builds the AND of all {@code filters} as ES bool filter clauses. Returns {@code null} when
	 * {@code filters} is empty so the caller can omit the filter clause entirely.
	 */
	static Query toQuery(List<Filter> filters) {
		if (filters == null || filters.isEmpty()) {
			return null;
		}
		List<Query> clauses = new ArrayList<>(filters.size());
		for (Filter f : filters) {
			clauses.add(translate(f));
		}
		return Query.of(q -> q.bool(b -> b.filter(clauses)));
	}

	private static Query translate(Filter f) {
		switch (f.getKind()) {
			case TERM:
			case PATIENT_SCOPE:
				return termQuery(f.getField(), f.getValue());
			case IN:
				return termsQuery(f.getField(), f.getValues());
			case RANGE:
				return rangeQuery(f.getField(), f.getFrom(), f.getTo());
			default:
				throw new IllegalArgumentException("Unsupported filter kind: " + f.getKind());
		}
	}

	private static Query termQuery(String field, Object value) {
		String resolved = resolveFieldName(field);
		FieldValue fv = toFieldValue(value);
		return Query.of(q -> q.term(t -> t.field(resolved).value(fv)));
	}

	private static Query termsQuery(String field, List<Object> values) {
		String resolved = resolveFieldName(field);
		List<FieldValue> fvs = new ArrayList<>(values.size());
		for (Object v : values) {
			fvs.add(toFieldValue(v));
		}
		return Query.of(q -> q.terms(t -> t.field(resolved).terms(tv -> tv.value(fvs))));
	}

	private static Query rangeQuery(String field, Object from, Object to) {
		validateFieldName(field);
		if (!QueryStoreConstants.FIELD_RECORD_DATE.equals(field)) {
			// Parity with the Lucene tier's v1 restriction. Lifting this to ES alone would create
			// "works on ES, breaks on Lucene" surprises for cross-tier consumers; raise it on all
			// tiers together when a real consumer needs it.
			throw new IllegalArgumentException(
			        "RANGE filter only supported on record_date in v1; got " + field);
		}
		return Query.of(q -> q.range(r -> {
			r.field(field);
			if (from != null) {
				r.gte(JsonData.of(toDateString(from)));
			}
			if (to != null) {
				r.lte(JsonData.of(toDateString(to)));
			}
			return r;
		}));
	}

	private static String resolveFieldName(String field) {
		validateFieldName(field);
		return COLUMN_FIELDS.contains(field) ? field : ElasticsearchFieldNames.META_PREFIX + field;
	}

	private static FieldValue toFieldValue(Object value) {
		if (value == null) {
			// Silent translation to FieldValue.NULL would turn a caller bug into a zero-hit query.
			// Filter.term(field, null) has no useful ES semantics; reject loudly instead.
			throw new IllegalArgumentException("Filter value must not be null");
		}
		if (value instanceof LocalDate) {
			return FieldValue.of(((LocalDate) value).toString());
		}
		if (value instanceof Boolean) {
			return FieldValue.of((Boolean) value);
		}
		if (value instanceof Long || value instanceof Integer) {
			return FieldValue.of(((Number) value).longValue());
		}
		if (value instanceof Number) {
			return FieldValue.of(((Number) value).doubleValue());
		}
		return FieldValue.of(value.toString());
	}

	private static String toDateString(Object value) {
		if (value instanceof LocalDate) {
			return value.toString();
		}
		throw new IllegalArgumentException(
		        "record_date RANGE filter expects LocalDate, got " + value.getClass().getName());
	}

	private static void validateFieldName(String field) {
		if (field == null || !FIELD_NAME_PATTERN.matcher(field).matches()) {
			throw new IllegalArgumentException("Invalid field name: " + field);
		}
	}
}
