/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.eip.odoo.openmrs.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.ozonehis.fhir.dataset.FhirDataset;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OpenmrsBillableToOdooSaleOrderIntegrationTest extends BaseOdooOpenmrsIntegrationTest {

    @BeforeAll
    static void setup() {
        // Set up the Odoo and OpenMRS utilities
        setupOdooUtils();
    }

    @Test
    public void shouldCreateSaleOrderFromBillable() {
        // create a Patient in OpenMRS
        var maryPatient = (Patient) FhirDataset.MARY_JANE.getResource();
        MethodOutcome outcome =
                openmrsFhirClient().create().resource(maryPatient).execute();
        assertNotNull(outcome.getId());
        assertEquals(true, outcome.getCreated());

        // verify patient created
        var patientId = outcome.getId().getIdPart();
        var createdPatient = openmrsFhirClient()
                .read()
                .resource(Patient.class)
                .withId(patientId)
                .execute();

        assertEquals("Jane", createdPatient.getNameFirstRep().getGivenAsSingleString());
        assertEquals("Mary", createdPatient.getNameFirstRep().getFamily());

        // start a visit in OpenMRS
        //		var visit = OpenmrsRestClient.createVisit(patientId);
        //		assertNotNull(visit);

        // create a billable in OpenMRS
    }
}
