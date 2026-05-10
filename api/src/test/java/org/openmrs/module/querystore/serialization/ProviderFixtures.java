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

import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;

/**
 * Shared Provider builders for serializer unit tests. A Provider always needs a linked Person
 * with at least one name — core's {@code Provider.getName()} routes through the linked Person and
 * logs a warning if no name layout is set. Static-import from test classes.
 */
final class ProviderFixtures {

	private ProviderFixtures() {
	}

	static Provider providerNamed(String uuid, String givenName, String familyName) {
		PersonName personName = new PersonName();
		personName.setGivenName(givenName);
		personName.setFamilyName(familyName);
		Person person = new Person();
		person.addName(personName);
		Provider p = new Provider();
		p.setUuid(uuid);
		p.setPerson(person);
		return p;
	}
}
