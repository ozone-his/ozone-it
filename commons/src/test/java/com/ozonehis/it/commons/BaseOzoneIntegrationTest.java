/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.it.commons;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
public abstract class BaseOzoneIntegrationTest {

    protected static OzoneRunner runner;

    protected static boolean started;

    protected static IGenericClient openmrsFhirClient() {
        FhirContext ctx = FhirContext.forR4();
        String fhirBaseServerUrl = OzoneApp.OPENMRS.baseUrl() + "/ws/fhir2/R4";

        // Set the credentials for the FHIR client
        // TODO: Add support for SSO
        OzoneAppCredentials credentials = OzoneApp.OPENMRS.credentials();
        BasicAuthInterceptor interceptor = new BasicAuthInterceptor(credentials.username(), credentials.password());
        IGenericClient client = ctx.newRestfulGenericClient(fhirBaseServerUrl);
        client.registerInterceptor(interceptor);

        return client;
    }

    /**
     * Wait for a specified number of seconds.
     *
     * @param seconds the number of seconds to wait
     */
    protected static void wait(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while waiting", e);
        }
    }

    @Order(0)
    @Test
    @DisplayName("It should start Ozone successfully")
    void shouldStartOzoneInstance() {
        // Your test code here
        assertTrue(started, "Ozone should be started successfully");
    }

    @AfterAll
    static void tearDown() throws Exception {
        log.info("Stopping Ozone...");
        // sleep 5 minutes
        // Thread.sleep(5 * 60 * 1000);
        if (runner != null) {
            runner.close();
        }
    }
}
