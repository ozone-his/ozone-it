/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.eip.openmrs.senaite.it.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.function.Function;

/**
 * WireMock stubs for OpenMRS FHIR endpoints used by sync ITs.
 */
public class OpenMrsWireMockSupport {

    public static final String DEFAULT_SERVICE_REQUEST_ID = "5a92fd97-89ed-48df-aea5-907f42810709";

    public static final String TASK_ID = "5e9139c3-1bf5-44d1-8524-c2df9f620570";

    public static final String PATIENT_ID = "487b174a-8f7a-475f-899b-752cf419edba";

    public static final String ORDER_ENCOUNTER_ID = "2a0d7092-4b8b-45bc-8ad5-2d187f7e1843";

    private final WireMockServer wireMockServer;

    private final Function<String, String> jsonLoader;

    private final String serviceRequestId;

    public OpenMrsWireMockSupport(WireMockServer wireMockServer, Function<String, String> jsonLoader) {
        this(wireMockServer, jsonLoader, DEFAULT_SERVICE_REQUEST_ID);
    }

    public OpenMrsWireMockSupport(
            WireMockServer wireMockServer, Function<String, String> jsonLoader, String serviceRequestId) {
        this.wireMockServer = wireMockServer;
        this.jsonLoader = jsonLoader;
        this.serviceRequestId = serviceRequestId;
    }

    public String getServiceRequestId() {
        return serviceRequestId;
    }

    public void start() {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        WireMock.configureFor("localhost", wireMockServer.port());
        wireMockServer.resetAll();
    }

    public void stop() {
        if (wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    public void stubMetadata() {
        stubGet("/openmrs/ws/fhir2/R4/metadata", "metadata.json");
    }

    public void stubTaskSearch(String status) {
        wireMockServer.stubFor(
                get(urlMatching("/openmrs/ws/fhir2/R4/Task.*")).willReturn(jsonResponse(taskBundle(status))));
    }

    public void stubEmptyTaskSearch() {
        wireMockServer.stubFor(get(urlMatching("/openmrs/ws/fhir2/R4/Task.*"))
                .willReturn(jsonResponse(jsonLoader.apply("fhir.bundle/task-empty-bundle.json"))));
    }

    public void stubTaskCreate() {
        wireMockServer.stubFor(post(urlEqualTo("/openmrs/ws/fhir2/R4/Task"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(201)
                        .withBody(taskResource("accepted"))));
    }

    public void stubTaskUpdate(String status) {
        wireMockServer.stubFor(
                put(urlMatching("/openmrs/ws/fhir2/R4/Task/.*")).willReturn(jsonResponse(taskResource(status))));
    }

    public void stubServiceRequestRead() {
        wireMockServer.stubFor(get(urlEqualTo("/openmrs/ws/fhir2/R4/ServiceRequest/" + serviceRequestId))
                .willReturn(jsonResponse(serviceRequestResource())));
    }

    public void stubOrderEncounterRead() {
        stubGet("/openmrs/ws/fhir2/R4/Encounter/" + ORDER_ENCOUNTER_ID, "fhir.bundle/order-encounter.json");
    }

    public void stubEmptyEncounterSearch() {
        wireMockServer.stubFor(get(urlMatching("/openmrs/ws/fhir2/R4/Encounter\\?.*"))
                .willReturn(jsonResponse(jsonLoader.apply("fhir.bundle/empty-search-bundle.json"))));
    }

    public void stubEmptyObservationSearch() {
        wireMockServer.stubFor(get(urlMatching("/openmrs/ws/fhir2/R4/Observation\\?.*"))
                .willReturn(jsonResponse(jsonLoader.apply("fhir.bundle/empty-search-bundle.json"))));
    }

    public void stubCreateEncounter() {
        wireMockServer.stubFor(post(urlEqualTo("/openmrs/ws/fhir2/R4/Encounter"))
                .willReturn(jsonResponse(jsonLoader.apply("fhir.bundle/created-encounter.json"))
                        .withStatus(201)));
    }

    public void stubCreateObservation() {
        wireMockServer.stubFor(post(urlEqualTo("/openmrs/ws/fhir2/R4/Observation"))
                .willReturn(jsonResponse(jsonLoader.apply("fhir.bundle/created-observation.json"))
                        .withStatus(201)));
    }

    public void stubCreateDiagnosticReport() {
        wireMockServer.stubFor(post(urlEqualTo("/openmrs/ws/fhir2/R4/DiagnosticReport"))
                .willReturn(jsonResponse(jsonLoader.apply("fhir.bundle/created-diagnostic-report.json"))
                        .withStatus(201)));
    }

    public void stubForLabOrderCreate() {
        stubMetadata();
        stubEmptyTaskSearch();
        stubTaskCreate();
    }

    public void stubForSampleReceivedSync() {
        stubMetadata();
        stubTaskSearch("requested");
        stubServiceRequestRead();
        stubTaskUpdate("accepted");
    }

    public void stubForPublishedSync() {
        stubMetadata();
        stubTaskSearch("accepted");
        stubServiceRequestRead();
        stubOrderEncounterRead();
        stubEmptyEncounterSearch();
        stubEmptyObservationSearch();
        stubCreateEncounter();
        stubCreateObservation();
        stubCreateDiagnosticReport();
        stubTaskUpdate("completed");
    }

    public void stubForCompletedTaskSkip() {
        stubMetadata();
        stubTaskSearch("completed");
        stubServiceRequestRead();
    }

    public void stubForFullScanRequestedTask() {
        stubMetadata();
        stubTaskSearch("requested");
        stubServiceRequestRead();
        stubTaskUpdate("accepted");
    }

    public void verifyTaskUpdatedTo(String status) {
        verify(putRequestedFor(urlMatching("/openmrs/ws/fhir2/R4/Task/.*"))
                .withRequestBody(containing("\"status\":\"" + status + "\"")));
    }

    public void verifyNoTaskUpdate() {
        verify(0, putRequestedFor(urlMatching("/openmrs/ws/fhir2/R4/Task/.*")));
    }

    public void verifyNoResultResourceCreates() {
        verify(0, postRequestedFor(urlEqualTo("/openmrs/ws/fhir2/R4/Encounter")));
        verify(0, postRequestedFor(urlEqualTo("/openmrs/ws/fhir2/R4/Observation")));
        verify(0, postRequestedFor(urlEqualTo("/openmrs/ws/fhir2/R4/DiagnosticReport")));
    }

    public void verifyResultResourcesCreated() {
        verify(postRequestedFor(urlEqualTo("/openmrs/ws/fhir2/R4/Encounter")));
        verify(postRequestedFor(urlEqualTo("/openmrs/ws/fhir2/R4/Observation")));
        verify(postRequestedFor(urlEqualTo("/openmrs/ws/fhir2/R4/DiagnosticReport")));
    }

    public void resetRequests() {
        wireMockServer.resetRequests();
    }

    public String taskBundleJson(String status) {
        return taskBundle(status);
    }

    private void stubGet(String url, String classpathJson) {
        wireMockServer.stubFor(get(urlEqualTo(url)).willReturn(jsonResponse(jsonLoader.apply(classpathJson))));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonResponse(String body) {
        return aResponse().withHeader("Content-Type", "application/json").withBody(body);
    }

    private String serviceRequestResource() {
        return jsonLoader
                .apply("fhir.bundle/service-request.json")
                .replace(DEFAULT_SERVICE_REQUEST_ID, serviceRequestId);
    }

    private String taskResource(String status) {
        return "{"
                + "\"resourceType\":\"Task\","
                + "\"id\":\"" + TASK_ID + "\","
                + "\"basedOn\":[{\"reference\":\"ServiceRequest/" + serviceRequestId
                + "\",\"type\":\"ServiceRequest\"}],"
                + "\"status\":\"" + status + "\","
                + "\"intent\":\"order\""
                + "}";
    }

    private String taskBundle(String status) {
        return "{"
                + "\"resourceType\":\"Bundle\","
                + "\"type\":\"searchset\","
                + "\"total\":1,"
                + "\"entry\":[{"
                + "\"fullUrl\":\"http://localhost/openmrs/ws/fhir2/R4/Task/" + TASK_ID + "\","
                + "\"resource\":" + taskResource(status)
                + "}]"
                + "}";
    }
}
