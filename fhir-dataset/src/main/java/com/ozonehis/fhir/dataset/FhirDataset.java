/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.fhir.dataset;

import jakarta.annotation.Nonnull;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

public enum FhirDataset {
    RICHARD_JONES(getPatient("fhir/dataset/richard.patient.json")),

    JOSHUA_JOHNSON(getPatient("fhir/dataset/joshua.patient.json")),

    JAMES_SMITH(getPatient("fhir/dataset/james.patient.json")),

    MARY_JANE(getPatient("fhir/dataset/mary.patient.json"));

    private final Resource resource;

    FhirDataset(Resource resource) {
        this.resource = resource;
    }

    static Patient getPatient(@Nonnull String filePath) {
        return FhirDatasetUtils.loadResource(filePath, new Patient());
    }

    public Resource getResource() {
        return resource;
    }
}
