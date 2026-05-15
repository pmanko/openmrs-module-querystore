/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.DaemonTokenAware;
import org.openmrs.module.querystore.api.impl.QueryStoreServiceImpl;
import org.openmrs.module.querystore.backend.BackendStore;
import org.openmrs.module.querystore.backend.BackendStoreSelector;
import org.openmrs.module.querystore.bootstrap.BootstrapService;

public class QueryStoreActivator extends BaseModuleActivator implements DaemonTokenAware {

	private static final Log log = LogFactory.getLog(QueryStoreActivator.class);

	private DaemonToken daemonToken;

	@Override
	public void setDaemonToken(DaemonToken token) {
		this.daemonToken = token;
	}

	@Override
	public void started() {
		log.info("Query Store module started");
		wireBackend(
		    Context.getRegisteredComponent("querystore.backend.selector", BackendStoreSelector.class),
		    Context.getRegisteredComponent("queryStoreService", QueryStoreServiceImpl.class));
		if (isAutostartEnabled(Context.getAdministrationService())) {
			triggerBootstrap();
		}
	}

	/**
	 * Reads {@code querystore.backend} via {@link BackendStoreSelector} and injects the chosen
	 * {@link BackendStore} into the service. Done here rather than at Spring wiring time because
	 * reading a GP during bean construction self-deadlocks against {@code ServiceContext}'s
	 * in-progress refresh (issue #10). Any failure (missing selector bean, unknown GP value with no
	 * default candidate wired) propagates out and fails module startup — preferable to silently
	 * leaving {@code backend == null}, which the service's null-checks would mask as
	 * empty-result-forever. The service is looked up via {@code getRegisteredComponent} (raw Spring
	 * bean) rather than {@code Context.getService} (AOP proxy implementing only the interface) so
	 * the {@code setBackend} internal seam stays reachable without a {@code ClassCastException}.
	 */
	void wireBackend(BackendStoreSelector selector, QueryStoreServiceImpl service) {
		service.setBackend(selector.getStore());
	}

	@Override
	public void stopped() {
		// A daemon-thread bootstrap started on `started()` may still be running here; we don't
		// interrupt it. The version-by-lastModified invariant (ADR Decision 3) protects the
		// indexed documents from cross-restart races, but a fresh start that re-runs autostart
		// could overlap the previous run's progress-table writes. Bounded risk on the progress
		// table only; proper shutdown coordination is a follow-up if real deployments hit it.
		log.info("Query Store module stopped");
	}

	/** True when the autostart property is set to {@code "true"} (case-insensitive); the GP is
	 *  registered declaratively in {@code omod/src/main/resources/config.xml}. */
	boolean isAutostartEnabled(AdministrationService admin) {
		return Boolean.TRUE.equals(admin.getGlobalPropertyValue(
		        QueryStoreConstants.GP_BOOTSTRAP_AUTOSTART, Boolean.FALSE));
	}

	/**
	 * Kicks off {@link BootstrapService#bootstrap()} in an OpenMRS daemon thread so module startup
	 * isn't blocked by what is often a multi-hour scan on a real corpus.
	 * {@code BootstrapServiceImpl.bootstrap()} catches per-type failures internally and never
	 * rethrows, so the runnable doesn't wrap it in a try/catch — uncaught throws would surface via
	 * the framework's thread-exception handler.
	 */
	private void triggerBootstrap() {
		if (daemonToken == null) {
			log.warn("Daemon token unavailable; skipping bootstrap autostart "
			        + "(BootstrapService.bootstrap() can still be called programmatically)");
			return;
		}
		BootstrapService bootstrap = Context.getService(BootstrapService.class);
		Daemon.runInDaemonThread(() -> {
			log.info("Auto-bootstrap starting");
			bootstrap.bootstrap();
			log.info("Auto-bootstrap completed");
		}, daemonToken);
	}
}
