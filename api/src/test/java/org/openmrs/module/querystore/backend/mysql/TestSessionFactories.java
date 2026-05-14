/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.querystore.backend.mysql;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Builds a {@link DbSessionFactory} over a Testcontainers MySQL instance so production classes
 * that take {@code DbSessionFactory} (e.g. {@code MysqlBackendStore}, {@code BootstrapProgressDao})
 * can be exercised in integration tests without standing up an OpenMRS Spring context. No entity
 * mappings are registered — the production classes only use {@code session.doReturningWork(...)}
 * via {@code JdbcSupport} for raw JDBC, so a bare {@link Configuration} suffices.
 *
 * <p>Callers must close the returned factory's underlying {@link org.hibernate.SessionFactory}
 * (e.g. in {@code @AfterClass}) — otherwise the Hibernate-owned connection pool stays open and
 * subsequent test classes can hit connection-cap warnings.
 */
public final class TestSessionFactories {

	private TestSessionFactories() {
	}

	public static DbSessionFactory forContainer(MySQLContainer<?> mysql) {
		SessionFactory sf = new Configuration()
		        .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
		        .setProperty("hibernate.connection.url", mysql.getJdbcUrl())
		        .setProperty("hibernate.connection.username", mysql.getUsername())
		        .setProperty("hibernate.connection.password", mysql.getPassword())
		        .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect")
		        .buildSessionFactory();
		return new DbSessionFactory(sf);
	}
}
