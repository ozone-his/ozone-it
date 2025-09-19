/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.eip.odoo.openmrs.it;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.ozonehis.eip.odoo.openmrs.Constants;
import com.ozonehis.eip.odoo.openmrs.model.Partner;
import com.ozonehis.fhir.dataset.FhirDataset;
import com.ozonehis.it.commons.OzoneApp;
import com.ozonehis.it.commons.OzoneAppReadinessChecker;
import com.ozonehis.it.commons.OzoneRunner;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Slf4j
@Order(1)
@SuppressWarnings("unchecked")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OpenmrsPatientToOdooPartnerIntegrationTest extends BaseOdooOpenmrsIntegrationTest {

    private static final UnmodifiableList<OzoneApp> apps =
            new UnmodifiableList<>(List.of(OzoneApp.OPENMRS, OzoneApp.ODOO));

    private static final Patient patientJamesSmith = (Patient) FhirDataset.JAMES_SMITH.getResource();

    private static String patientIdForJamesSmith;

    @BeforeAll
    static void setup() throws IOException, InterruptedException {
        runner = new OzoneRunner();
        started = runner.startOzone(apps);

        boolean allReady = OzoneAppReadinessChecker.waitForAppsReady(360, apps);

        assertTrue(started, "Failed to start Ozone apps: " + apps);
        assertTrue(allReady, "Not all apps are ready within the timeout period");

        wait(10); // Wait for the apps to stabilize after startup

        // Set up the Odoo and OpenMRS utilities
        setupOdooUtils();

        // Create a patient to be used in tests
        MethodOutcome outcome = openmrsFhirClient()
                .create()
                .resource(patientJamesSmith)
                .prettyPrint()
                .encodedJson()
                .execute();

        assertNotNull(outcome);
        assertTrue(outcome.getCreated());

        patientIdForJamesSmith = outcome.getId().getIdPart();
    }

    @Test
    @Order(1)
    @DisplayName("Should start Odoo and OpenMRS apps")
    void shouldStartOdooAndOpenmrsApps() {
        List<OzoneApp> runningApps = runner.getRunningApps();

        assertTrue(runningApps.contains(OzoneApp.OPENMRS));
        assertTrue(runningApps.contains(OzoneApp.ODOO));
    }

    @Test
    @Order(2)
    @DisplayName("should create patient in OpenMRS and synchronized as partner in Odoo")
    void shouldCreateRichardJonePatientInOpenMRSandSynchronizedAsPartnerInOdoo() {
        Patient patientRichardJones = (Patient) FhirDataset.RICHARD_JONES.getResource();
        MethodOutcome outcome = openmrsFhirClient()
                .create()
                .resource(patientRichardJones)
                .prettyPrint()
                .encodedJson()
                .execute();

        assertNotNull(outcome);
        assertTrue(outcome.getCreated());

        // Search for the patient in OpenMRS
        Patient createdPatient = openmrsFhirClient()
                .read()
                .resource(Patient.class)
                .withId(outcome.getId().getIdPart())
                .prettyPrint()
                .encodedJson()
                .execute();

        assertNotNull(createdPatient);
        assertEquals(
                patientRichardJones.getNameFirstRep().getFamily(),
                createdPatient.getNameFirstRep().getFamily());
        assertEquals(
                patientRichardJones.getNameFirstRep().getGivenAsSingleString(),
                createdPatient.getNameFirstRep().getGivenAsSingleString());
        assertEquals(
                patientRichardJones.getAddressFirstRep().getCity(),
                createdPatient.getAddressFirstRep().getCity());

        // Verify the patient was synchronized to Odoo
        // wait for 30 seconds to allow the synchronization to complete
        wait(20);
        String ref = createdPatient.getIdPart();
        log.info("Verifying patient(s) in Odoo: {}", ref);

        Object[] result = odooClient()
                .searchAndRead(Constants.PARTNER_MODEL, List.of(asList("ref", "=", ref)), partnerDefaultAttributes);

        assertNotNull(result);
        assertTrue(stream(result).findAny().isPresent());
        log.info("Found partner(s) in Odoo: {}", result);

        Partner createdPartner = odooUtils.convertToObject((Map<String, Object>) result[0], Partner.class);

        assertNotNull(createdPartner);
        assertEquals(createdPartner.getPartnerRef(), createdPatient.getIdPart());
        assertEquals(
                createdPartner.getPartnerCity(),
                createdPatient.getAddress().get(0).getCity());
        assertTrue(createdPartner
                .getPartnerName()
                .contains(createdPatient.getNameFirstRep().getFamily()));
        assertTrue(createdPartner
                .getPartnerName()
                .contains(createdPatient.getNameFirstRep().getGivenAsSingleString()));
    }

    @Test
    @Order(3)
    @DisplayName("should update patient in OpenMRS and verify changes are synchronized to Odoo partner")
    void shouldUpdatePatientInOpenMRSAndVerifySynchronizationToOdoo() {
        // Verify the patient was synchronized to Odoo
        log.info("Verifying patient(s) in Odoo with ref: {}", patientIdForJamesSmith);
        Object[] result = odooClient()
                .searchAndRead(
                        Constants.PARTNER_MODEL,
                        List.of(asList("ref", "=", patientIdForJamesSmith)),
                        partnerDefaultAttributes);
        assertNotNull(result);
        assertTrue(result.length > 0);

        // Update the patient
        Patient existingPatient = openmrsFhirClient()
                .read()
                .resource(Patient.class)
                .withId(patientIdForJamesSmith)
                .execute();

        assertNotNull(existingPatient);
        assertEquals(
                patientJamesSmith.getNameFirstRep().getFamily(),
                existingPatient.getNameFirstRep().getFamily());
        assertEquals(
                patientJamesSmith.getNameFirstRep().getGivenAsSingleString(),
                existingPatient.getNameFirstRep().getGivenAsSingleString());
        assertEquals(
                patientJamesSmith.getAddressFirstRep().getCity(),
                existingPatient.getAddressFirstRep().getCity());

        log.info("Updating patient in OpenMRS with UUID: {}", existingPatient.getIdPart());

        // Update the patient details
        existingPatient.getNameFirstRep().setFamily("Smith");
        existingPatient.getAddressFirstRep().setCity("NewCity");

        openmrsFhirClient().update().resource(existingPatient).execute();

        // Wait for sync to complete
        wait(20);

        // Verify the partner was updated in Odoo
        Object[] synchronizedPartnerInfo = odooClient()
                .searchAndRead(
                        Constants.PARTNER_MODEL,
                        List.of(asList("ref", "=", existingPatient.getIdPart())),
                        partnerDefaultAttributes);

        assertNotNull(synchronizedPartnerInfo);
        assertTrue(synchronizedPartnerInfo.length > 0);

        Partner updatedPartner =
                odooUtils.convertToObject((Map<String, Object>) synchronizedPartnerInfo[0], Partner.class);

        assertNotNull(updatedPartner);
        assertEquals(existingPatient.getIdPart(), updatedPartner.getPartnerRef());
        // assertEquals("NewCity", updatedPartner.getPartnerCity());
        assertTrue(updatedPartner.getPartnerName().contains("Smith"));
    }

    @Test
    @Order(4)
    @DisplayName("should update multiple fields of patient and verify all changes sync to Odoo partner")
    void shouldUpdateMultipleFieldsAndVerifySynchronization() {

        // Update multiple fields
        Patient existingPatient = openmrsFhirClient()
                .read()
                .resource(Patient.class)
                .withId(patientIdForJamesSmith)
                .execute();

        existingPatient.getNameFirstRep().setFamily("Anderson");
        existingPatient.getNameFirstRep().setGiven(List.of(new StringType("Thomas")));
        existingPatient.getAddressFirstRep().setCity("SpringField");
        existingPatient.getAddressFirstRep().setPostalCode("12345");

        openmrsFhirClient().update().resource(existingPatient).execute();

        // Wait for sync
        wait(20);

        // Verify all changes in Odoo
        Object[] result = odooClient()
                .searchAndRead(
                        Constants.PARTNER_MODEL,
                        List.of(asList("ref", "=", patientIdForJamesSmith)),
                        partnerDefaultAttributes);

        Partner updatedPartner = odooUtils.convertToObject((Map<String, Object>) result[0], Partner.class);

        assertNotNull(updatedPartner);
        assertEquals(patientIdForJamesSmith, updatedPartner.getPartnerRef());
        // assertEquals("SpringField", updatedPartner.getPartnerCity());
        assertTrue(updatedPartner.getPartnerName().contains("Anderson"));
        assertTrue(updatedPartner.getPartnerName().contains("Thomas"));
    }

    @Test
    @Order(5)
    @DisplayName("should handle concurrent updates to patient and verify final state in Odoo")
    void shouldHandleConcurrentUpdatesAndVerifyFinalState() {
        // Perform multiple rapid updates
        Patient existingPatient = openmrsFhirClient()
                .read()
                .resource(Patient.class)
                .withId(patientIdForJamesSmith)
                .execute();

        // First update
        existingPatient.getNameFirstRep().setFamily("Johnson");
        openmrsFhirClient().update().resource(existingPatient).execute();

        // The second update immediately after
        existingPatient.getNameFirstRep().setFamily("Williams");
        // existingPatient.getAddressFirstRep().setCity("Boston");
        openmrsFhirClient().update().resource(existingPatient).execute();

        // Wait for sync to complete
        wait(20);

        // Verify the final state in Odoo
        Object[] result = odooClient()
                .searchAndRead(
                        Constants.PARTNER_MODEL,
                        List.of(asList("ref", "=", patientIdForJamesSmith)),
                        partnerDefaultAttributes);

        Partner finalPartner = odooUtils.convertToObject((Map<String, Object>) result[0], Partner.class);

        assertNotNull(finalPartner);
        assertEquals(patientIdForJamesSmith, finalPartner.getPartnerRef());
        // assertEquals("Boston", finalPartner.getPartnerCity());
        assertTrue(finalPartner.getPartnerName().contains("Williams"));
    }
}
