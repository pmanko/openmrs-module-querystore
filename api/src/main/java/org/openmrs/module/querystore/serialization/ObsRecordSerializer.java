/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.serialization;

import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_COMMENT;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_INTERPRETATION;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_OBS_GROUP_CONCEPT_NAME;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_OBS_GROUP_UUID;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_STATUS;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_UNITS;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_BOOLEAN;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_CODED_NAME;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_CODED_UUID;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_COMPLEX_HANDLER;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_COMPLEX_URI;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_DATETIME;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_DRUG_NAME;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_DRUG_UUID;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_NUMERIC;
import static org.openmrs.module.querystore.QueryStoreConstants.FIELD_VALUE_TEXT;

import java.time.LocalDate;

import org.openmrs.Concept;
import org.openmrs.ConceptComplex;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptNumeric;
import org.openmrs.Drug;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.model.QueryDocument;
import org.openmrs.module.querystore.util.ConceptNameUtil;
import org.openmrs.module.querystore.util.DateFormatUtil;

/**
 * Serializes an {@link Obs} into a {@link QueryDocument} for the {@code querystore_obs} index.
 * Group obs members are emitted as atomic documents with {@code obs_group_uuid} and
 * {@code obs_group_concept_name} populated; group parents whose own value is empty are skipped
 * (their members are serialized individually) per the ADR decision 6 Synonyms and group obs
 * convention.
 *
 * <p>The serializer processes a single {@link Obs} at a time and does not iterate group members
 * itself — the calling sync pipeline or backfill task is responsible for fanning out across
 * {@link Obs#getGroupMembers()} and invoking {@code serialize} on each.
 */
public class ObsRecordSerializer extends AbstractRecordSerializer<Obs> {

	@Override
	public String getResourceType() {
		return "obs";
	}

	@Override
	public Class<Obs> getSupportedType() {
		return Obs.class;
	}

	@Override
	protected String getPatientUuid(Obs obs) {
		// Patient and Person share the same UUID, so reading from getPerson is correct for any
		// patient-scoped obs. Non-patient Person obs would index under a non-patient UUID; tracked
		// under the Person vs Patient open question (docs/adr.md#person-vs-patient-model).
		return obs.getPerson() != null ? obs.getPerson().getUuid() : null;
	}

	@Override
	protected String getResourceUuid(Obs obs) {
		return obs.getUuid();
	}

	@Override
	protected LocalDate getDate(Obs obs) {
		return DateFormatUtil.toLocalDate(obs.getObsDatetime());
	}

	@Override
	protected void populate(Obs obs, QueryDocument doc) {
		Concept concept = obs.getConcept();
		ConceptDatatype datatype = concept != null ? concept.getDatatype() : null;

		String valueDisplay = extractValueAndPopulate(obs, concept, datatype, doc);
		if (valueDisplay.isEmpty()) {
			return;
		}
		String preferredName = ConceptNameUtil.getPreferredName(concept);
		doc.setText(preferredName.isEmpty() ? valueDisplay : preferredName + ": " + valueDisplay);

		putConceptFields(doc, concept, preferredName);
		putObsMetaFields(doc, obs);
		putGroupFields(doc, obs);
		putEncounterContext(doc, obs.getEncounter());
	}

	/**
	 * Walks the obs's value fields exactly once, populating value-related metadata and returning
	 * the display string used to compose {@code text}. Returns an empty string when the obs has
	 * no recordable value (e.g., a group parent whose members carry the data).
	 */
	private String extractValueAndPopulate(Obs obs, Concept concept, ConceptDatatype datatype, QueryDocument doc) {
		Concept valueCoded = obs.getValueCoded();
		if (valueCoded != null) {
			String codedName = ConceptNameUtil.getPreferredName(valueCoded);
			if (!codedName.isEmpty()) {
				doc.putMetadata(FIELD_VALUE_CODED_UUID, valueCoded.getUuid());
				doc.putMetadata(FIELD_VALUE_CODED_NAME, codedName);
			}
			return codedName;
		}
		Drug drug = obs.getValueDrug();
		if (drug != null) {
			String drugName = drug.getName();
			if (drugName != null) {
				doc.putMetadata(FIELD_VALUE_DRUG_UUID, drug.getUuid());
				doc.putMetadata(FIELD_VALUE_DRUG_NAME, drugName);
				return drugName;
			}
			return "";
		}
		Double valueNumeric = obs.getValueNumeric();
		if (valueNumeric != null) {
			doc.putMetadata(FIELD_VALUE_NUMERIC, valueNumeric);
			// Single ConceptNumeric resolution per numeric obs — reused for units AND for the
			// reference-range abnormal flag below so we don't pay two service lookups per record.
			ConceptNumeric cn = resolveConceptNumeric(concept, datatype);
			String units = cn != null ? cn.getUnits() : null;
			if (units != null && !units.isEmpty()) {
				doc.putMetadata(FIELD_UNITS, units);
			}
			String modifier = obs.getValueModifier();
			StringBuilder sb = new StringBuilder();
			if (modifier != null) {
				sb.append(modifier);
			}
			sb.append(valueNumeric);
			if (units != null && !units.isEmpty()) {
				sb.append(' ').append(units);
			}
			String abnormalSuffix = abnormalRangeSuffix(cn, valueNumeric);
			if (!abnormalSuffix.isEmpty()) {
				sb.append(' ').append(abnormalSuffix);
			}
			return sb.toString();
		}
		String valueText = obs.getValueText();
		if (valueText != null && !valueText.isEmpty()) {
			doc.putMetadata(FIELD_VALUE_TEXT, valueText);
			return valueText;
		}
		if (obs.getValueDatetime() != null) {
			String formatted = DateFormatUtil.formatDate(obs.getValueDatetime());
			doc.putMetadata(FIELD_VALUE_DATETIME, formatted);
			return formatted;
		}
		if (datatype != null && datatype.isBoolean() && obs.getValueBoolean() != null) {
			Boolean b = obs.getValueBoolean();
			doc.putMetadata(FIELD_VALUE_BOOLEAN, b);
			return b ? "Yes" : "No";
		}
		if (datatype != null && datatype.isComplex() && obs.getValueComplex() != null) {
			doc.putMetadata(FIELD_VALUE_COMPLEX_URI, obs.getValueComplex());
			String handler = resolveComplexHandler(obs.getConcept());
			if (handler != null && !handler.isEmpty()) {
				doc.putMetadata(FIELD_VALUE_COMPLEX_HANDLER, handler);
			}
			return "[complex value]";
		}
		return "";
	}

	/**
	 * Resolves the registered {@link org.openmrs.obs.ComplexObsHandler} name for a complex-typed obs.
	 * When the obs's concept is already a {@link ConceptComplex} instance (the common case under
	 * Hibernate's joined-table inheritance), we read the handler directly. When Hibernate returned a
	 * plain {@code Concept} proxy, we fall back to a typed service lookup — one extra query per
	 * complex obs, which is acceptable because complex obs are sparse (images, PDFs, DICOM) relative
	 * to numeric/coded obs.
	 */
	private String resolveComplexHandler(Concept concept) {
		if (concept instanceof ConceptComplex) {
			return ((ConceptComplex) concept).getHandler();
		}
		if (concept == null || concept.getConceptId() == null) {
			return null;
		}
		ConceptComplex typed = Context.getConceptService().getConceptComplex(concept.getConceptId());
		return typed == null ? null : typed.getHandler();
	}

	private void putObsMetaFields(QueryDocument doc, Obs obs) {
		if (obs.getInterpretation() != null) {
			doc.putMetadata(FIELD_INTERPRETATION, obs.getInterpretation().name());
		}
		if (obs.getStatus() != null) {
			doc.putMetadata(FIELD_STATUS, obs.getStatus().name());
		}
		String comment = obs.getComment();
		if (comment != null && !comment.trim().isEmpty()) {
			doc.putMetadata(FIELD_COMMENT, comment.trim());
		}
	}

	private void putGroupFields(QueryDocument doc, Obs obs) {
		Obs parent = obs.getObsGroup();
		if (parent == null) {
			return;
		}
		doc.putMetadata(FIELD_OBS_GROUP_UUID, parent.getUuid());
		String parentName = ConceptNameUtil.getPreferredName(parent.getConcept());
		if (!parentName.isEmpty()) {
			doc.putMetadata(FIELD_OBS_GROUP_CONCEPT_NAME, parentName);
		}
	}

	/**
	 * Resolves a {@link ConceptNumeric} typed view of the obs's concept when the concept is
	 * numeric, falling back through Hibernate's joined-table inheritance via a service lookup
	 * when the concept arrives as a plain {@link Concept} proxy. Returns null for non-numeric
	 * concepts. One lookup per numeric obs — reused by callers that need both units and the
	 * reference ranges.
	 */
	private ConceptNumeric resolveConceptNumeric(Concept concept, ConceptDatatype datatype) {
		if (concept == null) {
			return null;
		}
		if (concept instanceof ConceptNumeric) {
			return (ConceptNumeric) concept;
		}
		if (datatype != null && datatype.isNumeric()) {
			return Context.getConceptService().getConceptNumeric(concept.getConceptId());
		}
		return null;
	}

	/**
	 * Returns the abnormal-range suffix for a numeric value (e.g. {@code "(HIGH; normal 60-115)"}),
	 * or the empty string when no flag applies. Severity precedence: critical bands override the
	 * normal bands, so a value above {@code hiCritical} reads as {@code "CRITICAL HIGH"} rather
	 * than {@code "HIGH"}. When the concept has reference bands on only one side (e.g. an upper
	 * critical but no critical-low), values on the unbanded side fall through unflagged — the
	 * convention is "don't invent a band the dictionary didn't define."
	 *
	 * <p>Why this lives in the indexed text rather than in {@link #FIELD_INTERPRETATION}: a small
	 * LLM (Gemma 4 E2B/E4B) reasoning from the chart benefits from seeing "146.5 umol/L (HIGH;
	 * normal 60-115)" inline at the citation point — answering "anything abnormal?" without
	 * needing to cross-walk a numeric to a separate interpretation field or carry the normal
	 * range in its own pretrained medical knowledge. The metadata {@code interpretation} field
	 * is set elsewhere (per-obs {@link Obs#getInterpretation()}) and is the source-clinician's
	 * judgement; this suffix is the dictionary-driven reference-range computation. Both can
	 * coexist on the same record.
	 */
	private static String abnormalRangeSuffix(ConceptNumeric cn, double value) {
		if (cn == null) {
			return "";
		}
		Double hiCritical = cn.getHiCritical();
		Double lowCritical = cn.getLowCritical();
		Double hiNormal = cn.getHiNormal();
		Double lowNormal = cn.getLowNormal();
		String severity;
		if (hiCritical != null && value > hiCritical) {
			severity = "CRITICAL HIGH";
		} else if (lowCritical != null && value < lowCritical) {
			severity = "CRITICAL LOW";
		} else if (hiNormal != null && value > hiNormal) {
			severity = "HIGH";
		} else if (lowNormal != null && value < lowNormal) {
			severity = "LOW";
		} else {
			return "";
		}
		StringBuilder out = new StringBuilder("(").append(severity);
		if (lowNormal != null && hiNormal != null) {
			out.append("; normal ").append(formatRangeNumber(lowNormal))
					.append("-").append(formatRangeNumber(hiNormal));
		}
		out.append(")");
		return out.toString();
	}

	/**
	 * Formats a reference-range bound as a compact decimal string. Whole-number values render
	 * without the trailing {@code .0} so {@code "normal 60-115"} appears instead of
	 * {@code "normal 60.0-115.0"} — easier for both the LLM and human readers, no information
	 * loss. Non-integer bounds keep their full precision.
	 */
	private static String formatRangeNumber(double v) {
		if (v == Math.floor(v) && !Double.isInfinite(v)) {
			return Long.toString((long) v);
		}
		return Double.toString(v);
	}
}
