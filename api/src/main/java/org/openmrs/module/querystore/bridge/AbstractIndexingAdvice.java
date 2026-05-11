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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.api.context.Context;
import org.openmrs.module.querystore.model.QueryDocument;
import org.openmrs.module.querystore.serialization.ClinicalRecordSerializer;
import org.springframework.aop.AfterReturningAdvice;

/**
 * Shared template for the AOP migration bridge advice (ADR Decision 12 "Migration bridge"). Each
 * concrete subclass adapts the template to one core service's save / void / unvoid / purge
 * methods.
 *
 * <p>The contract a subclass must declare:
 * <ul>
 *   <li>{@link #getSupportedType()} — the entity class used to filter {@code returnValue} and
 *       {@code args[0]} via {@code instanceof}, so an aspect on a service that handles multiple
 *       entity types (e.g., {@code OrderService} for drug / test / referral orders) processes
 *       only its own subtype.</li>
 *   <li>{@link #serializer()} — the {@link ClinicalRecordSerializer} for the type. Subclasses
 *       typically resolve a Spring bean via {@link Context#getRegisteredComponent}, so the advice
 *       instance can be no-arg constructed by OpenMRS at module load.</li>
 *   <li>{@link #triggerMethods()} — the set of advised method names. Core's naming is uneven
 *       ({@code save}/{@code saveX}/{@code removeAllergy}) so this is per-subclass rather than a
 *       hard-coded convention.</li>
 *   <li>{@link #purgeMethods()} — the subset of triggers whose semantics are "remove from core
 *       unconditionally," which the advice routes straight to delete regardless of the entity's
 *       voided flag.</li>
 * </ul>
 *
 * <p>Optional hook:
 * <ul>
 *   <li>{@link #collectTree(BaseOpenmrsData)} — defaults to {@code singletonList(root)}. Override
 *       for entity types that recursively reference siblings of the same type (obs group members)
 *       so each node is independently dispatched under the per-node voided policy.</li>
 * </ul>
 *
 * <p><b>Per-node voided policy.</b> The advised method is treated as a trigger, not as the
 * authority on index-vs-delete. Each node in {@code collectTree(root)} is partitioned by its own
 * voided flag (per ADR Decision 10): voided → delete, non-voided → serialize + index. Purge is the
 * only override — every node in the tree is unconditionally deleted because the core row is being
 * removed.
 *
 * <p><b>Synchronous serialization, asynchronous index.</b> The advice runs inside the originating
 * transaction so lazy Hibernate navigations resolve against an open session. Serialization happens
 * here; embedding and the actual upsert / delete run after commit on the
 * {@link AfterCommitDispatcher}'s executor. Failures inside the dispatched task are caught
 * per-entity so a single poison record can't skip its siblings.
 *
 * <p><b>Removal marker.</b> Each subclass is time-bound and carries its own removal marker per
 * ADR Decision 12. This abstract base is deleted when the last subclass is removed.
 */
public abstract class AbstractIndexingAdvice<T extends BaseOpenmrsData> implements AfterReturningAdvice {

	private final Log log = LogFactory.getLog(getClass());

	@Override
	public final void afterReturning(Object returnValue, Method method, Object[] args, Object target) {
		String name = method.getName();
		if (!triggerMethods().contains(name)) {
			return;
		}

		T entity = entityFrom(returnValue, args);
		if (entity == null) {
			return;
		}

		try {
			dispatch(entity, purgeMethods().contains(name));
		}
		catch (RuntimeException e) {
			// Best-effort per ADR Decision 12. Failures during serialization or dispatch must not
			// propagate back to the clinical-thread caller (the originating save already succeeded).
			log.warn(getClass().getSimpleName() + " failed for " + name + "; swallowing per ADR Decision 12", e);
		}
	}

	private void dispatch(T root, boolean purge) {
		ClinicalRecordSerializer<T> ser = serializer();
		BridgeIndexer indexer = indexer();
		AfterCommitDispatcher dispatcher = dispatcher();

		List<T> tree = collectTree(root);
		List<QueryDocument> toIndex = new ArrayList<>(tree.size());
		List<String> toDelete = new ArrayList<>(purge ? tree.size() : 0);
		for (T node : tree) {
			if (purge || node.getVoided()) {
				toDelete.add(node.getUuid());
			} else {
				QueryDocument doc = ser.serialize(node);
				if (doc != null) {
					toIndex.add(doc);
				}
			}
		}

		if (toIndex.isEmpty() && toDelete.isEmpty()) {
			return;
		}
		String resourceType = ser.getResourceType();
		dispatcher.dispatch(() -> {
			// Per-entity failure isolation: a single poison document (e.g., embedder throws on a
			// pathological text) must not skip its sibling members. The dispatcher's outer guard
			// remains as a last-resort catch for anything that escapes here.
			for (QueryDocument doc : toIndex) {
				try {
					indexer.index(doc);
				}
				catch (RuntimeException e) {
					log.warn("Bridge skipping index for " + resourceType + "/"
					        + doc.getResourceUuid() + " due to failure", e);
				}
			}
			for (String uuid : toDelete) {
				try {
					indexer.delete(resourceType, uuid);
				}
				catch (RuntimeException e) {
					log.warn("Bridge skipping delete for " + resourceType + "/" + uuid
					        + " due to failure", e);
				}
			}
		});
	}

	private T entityFrom(Object returnValue, Object[] args) {
		Class<T> type = getSupportedType();
		if (type.isInstance(returnValue)) {
			return type.cast(returnValue);
		}
		if (args != null && args.length > 0 && type.isInstance(args[0])) {
			return type.cast(args[0]);
		}
		return null;
	}

	/**
	 * Subclass hook: which Java class to match against {@code returnValue} and {@code args[0]} when
	 * extracting the entity. For services that handle multiple subtypes ({@code OrderService}) this
	 * is the specific subtype the advice cares about.
	 */
	protected abstract Class<T> getSupportedType();

	protected abstract ClinicalRecordSerializer<T> serializer();

	protected abstract Set<String> triggerMethods();

	/**
	 * Subset of {@link #triggerMethods()} whose semantics are "remove from core unconditionally."
	 * The advice routes these straight to delete regardless of the entity's voided flag. Must be
	 * non-empty for every advised core service in OpenMRS 2.x — every type has at least one purge
	 * method.
	 */
	protected abstract Set<String> purgeMethods();

	/**
	 * Default: a single-element list containing only the root. Override for entity types that
	 * recursively reference same-type siblings (currently {@code Obs} group members) so each node
	 * is dispatched independently under the per-node voided policy.
	 */
	protected List<T> collectTree(T root) {
		return Collections.singletonList(root);
	}

	BridgeIndexer indexer() {
		return Context.getRegisteredComponent("querystore.bridge.indexer", BridgeIndexer.class);
	}

	AfterCommitDispatcher dispatcher() {
		return Context.getRegisteredComponent("querystore.bridge.dispatcher", AfterCommitDispatcher.class);
	}
}
