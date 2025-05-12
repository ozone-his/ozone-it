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
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
public abstract class BaseOzoneIntegrationTest {

    protected static OzoneRunner runner;

    protected static boolean started;

    protected static IGenericClient openmrsFhirClient() {
        FhirContext fhirContext = FhirContext.forR4();
        String fhirServerUrl = OzoneApp.OPENMRS.baseUrl() + "/ws/fhir2/R4";
        IGenericClient client = fhirContext.newRestfulGenericClient(fhirServerUrl);

        // Create custom interceptor that implements the correct interface
        IClientInterceptor authInterceptor = new IClientInterceptor() {

            @Override
            public void interceptRequest(ca.uhn.fhir.rest.client.api.IHttpRequest theRequest) {
                String credentials = OzoneApp.OPENMRS.credentials().username() + ":"
                        + OzoneApp.OPENMRS.credentials().password();
                String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
                theRequest.addHeader("Authorization", "Basic " + base64Credentials);
            }

            @Override
            public void interceptResponse(IHttpResponse iHttpResponse) throws IOException {}
        };

        log.info("Using FHIR server URL: {}", fhirServerUrl);
        log.info(
                "Using Basic Auth credentials: {}:{}",
                OzoneApp.OPENMRS.credentials().username(),
                OzoneApp.OPENMRS.credentials().password());

        client.registerInterceptor(authInterceptor);
        return client;
    }

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
        // Thread.sleep(2 * 60 * 1000);
        if (runner != null) {
            runner.close();
        }
    }
}
