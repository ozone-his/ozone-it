/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.it.commons;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Base64;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class OpenmrsRestClient {

    private static String getEncodedBasicAuthCredentials() {
        final String userAndPass = OzoneApp.OPENMRS.credentials().username() + ":"
                + OzoneApp.OPENMRS.credentials().password();
        byte[] auth = Base64.getEncoder().encode(userAndPass.getBytes(UTF_8));
        return "Basic " + new String(auth, UTF_8);
    }

    protected static String post(String url, String payload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", getEncodedBasicAuthCredentials())
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            var client = HttpClient.newHttpClient();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                throw new RuntimeException("Failed : HTTP error code : " + response.statusCode() + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // create Visit
    public static String createVisit(String patientUuid) {
        String visitPayload = "    \"patient\": \"'" + patientUuid + "'\",\n"
                + "    \"visitType\": \"7b0f5697-27e3-40c4-8bae-f4049abfb4ed\",\n"
                + "    \"startDatetime\": \"2025-09-20T04:09:25.000Z\",\n"
                + "    \"location\": \"aff27d58-a15c-49a6-9beb-d30dcfc0c66e\"\n"
                + "}";

        String url = OzoneApp.OPENMRS.baseUrl() + "/ws/rest/v1/visit";
        var body = post(url, visitPayload);
        log.info("Created visit: {}", body);
        return body;
    }
}
