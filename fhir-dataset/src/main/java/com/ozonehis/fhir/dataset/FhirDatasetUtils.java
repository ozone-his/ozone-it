/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.fhir.dataset;

import ca.uhn.fhir.context.FhirContext;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Resource;

public class FhirDatasetUtils {

    /**
     * Load resource from a file path.
     *
     * @param filePath the file path of the resource to load.
     * @param resource resource object
     * @param <T>      The type of the resource to load e.g., Patient, Encounter, etc.
     * @return resource object
     */
    @SuppressWarnings("unchecked")
    protected static <T extends Resource> T loadResource(String filePath, T resource) {
        FhirContext ctx = FhirContext.forR4();
        return (T) ctx.newJsonParser().parseResource(resource.getClass(), readJSON(filePath));
    }

    /**
     * Read JSON file from the classpath.
     *
     * @param filePath the file path of the JSON file to read.
     * @return JSON content as a string
     */
    protected static String readJSON(String filePath) {
        InputStream is = FhirDatasetUtils.class.getClassLoader().getResourceAsStream(filePath);
        if (is == null) {
            throw new IllegalArgumentException("File not found! " + filePath);
        } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
