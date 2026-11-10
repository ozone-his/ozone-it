/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.eip.openmrs.senaite.it.camel;

import static com.ozonehis.eip.openmrs.senaite.it.support.OpenMrsWireMockSupport.DEFAULT_SERVICE_REQUEST_ID;
import static com.ozonehis.eip.openmrs.senaite.it.support.OpenMrsWireMockSupport.PATIENT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openmrs.eip.fhir.Constants.HEADER_FHIR_EVENT_TYPE;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.ozonehis.eip.openmrs.senaite.Constants;
import com.ozonehis.eip.openmrs.senaite.it.support.OpenMrsWireMockSupport;
import com.ozonehis.eip.openmrs.senaite.it.support.SenaiteWorkflowHelper;
import com.ozonehis.eip.openmrs.senaite.model.SenaiteResponseWrapper;
import com.ozonehis.eip.openmrs.senaite.model.analysisRequest.AnalysisRequestDTO;
import com.ozonehis.eip.openmrs.senaite.model.analysisRequest.AnalysisRequestMapper;
import com.ozonehis.eip.openmrs.senaite.model.analysisRequest.response.AnalysisRequestItem;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AnalysisRequestChangeSyncIntegrationTest extends BaseRouteIntegrationTest {

    private static final String GET_ANALYSIS_REQUEST_BY_PATIENT_ID =
            "http://localhost:8080/senaite/@@API/senaite/v1/AnalysisRequest?catalog=senaite_catalog_sample&complete=true&getClientID=%s&getClientSampleID=%s";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

    private static final List<String> SENAITE_GET_URLS = new CopyOnWriteArrayList<>();

    private static final AtomicBoolean INTERCEPT_INSTALLED = new AtomicBoolean(false);

    /** Unique per JVM so this class does not collide with create/cancel ITs. */
    private static final String SERVICE_REQUEST_ID = UUID.randomUUID().toString();

    private final WireMockServer wireMockServer = new WireMockServer(8089);

    private OpenMrsWireMockSupport openMrs;

    private SenaiteWorkflowHelper senaiteWorkflow;

    private Bundle serviceRequestBundle;

    @BeforeEach
    public void setUp() {
        openMrs = new OpenMrsWireMockSupport(wireMockServer, this::readJSON, SERVICE_REQUEST_ID);
        openMrs.start();
        senaiteWorkflow = new SenaiteWorkflowHelper(getSenaiteConfig());
        serviceRequestBundle = loadServiceRequestBundle();
        SENAITE_GET_URLS.clear();
    }

    @AfterEach
    public void tearDown() {
        openMrs.stop();
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        getContextWithRouting(context);
        if (INTERCEPT_INSTALLED.compareAndSet(false, true)) {
            AdviceWith.adviceWith(context, "senaite-get-analysis-request-route", a -> a.weaveAddFirst()
                    .process(exchange -> {
                        Boolean complete = exchange.getIn().getHeader(Constants.HEADER_SENAITE_COMPLETE, Boolean.class);
                        String clientSampleId =
                                exchange.getIn().getHeader(Constants.HEADER_CLIENT_SAMPLE_ID, String.class);
                        SENAITE_GET_URLS.add((Boolean.TRUE.equals(complete) ? "complete=true" : "complete=false")
                                + ":"
                                + clientSampleId);
                    }));
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should verify change-poll and search routes are registered")
    public void shouldVerifySyncRoutes() {
        assertTrue(hasRoute(contextExtension.getContext(), "senaite-search-analysis-requests-route"));
        assertTrue(hasRoute(contextExtension.getContext(), "senaite-get-analysis-request-route"));
        assertTrue(hasRoute(contextExtension.getContext(), "poll-senaite"));
        assertNotNull(getAnalysisRequestChangePoller());
        assertNotNull(getTaskProcessor());
    }

    @Test
    @Order(2)
    @DisplayName("Should accept Task when sample is received without loading results")
    public void shouldAcceptTaskWhenSampleReceivedWithoutLoadingResults() throws Exception {
        AnalysisRequestDTO analysisRequest = createAnalysisRequest();
        senaiteWorkflow.receive(analysisRequest.getUid(), analysisRequest.getClient());
        analysisRequest = refetchAnalysisRequest();
        assertEquals("sample_received", analysisRequest.getReviewState());

        openMrs.stubForSampleReceivedSync();
        SENAITE_GET_URLS.clear();
        openMrs.resetRequests();

        getAnalysisRequestChangePoller().poll(getProducerTemplate());

        openMrs.verifyTaskUpdatedTo("accepted");
        openMrs.verifyNoResultResourceCreates();
        assertFalse(
                SENAITE_GET_URLS.stream().anyMatch(url -> url.startsWith("complete=true")),
                "sample_received sync must not load a full AnalysisRequest: " + SENAITE_GET_URLS);
        assertEquals("sample_received", refetchAnalysisRequest().getReviewState());
    }

    @Test
    @Order(3)
    @DisplayName("Should skip already accepted Task on received poll")
    public void shouldSkipAlreadyAcceptedTaskOnReceivedPoll() throws Exception {
        ensureAnalysisRequestReceived();

        openMrs.stubMetadata();
        openMrs.stubTaskSearch("accepted");
        openMrs.stubServiceRequestRead();
        openMrs.resetRequests();
        SENAITE_GET_URLS.clear();

        getAnalysisRequestChangePoller().poll(getProducerTemplate());

        openMrs.verifyNoTaskUpdate();
        openMrs.verifyNoResultResourceCreates();
    }

    @Test
    @Order(4)
    @DisplayName("Should not use complete=true on full Task scan until sample is published")
    public void shouldNotUseCompleteTrueOnFullScanUntilPublished() throws Exception {
        ensureAnalysisRequestReceived();

        openMrs.stubForFullScanRequestedTask();
        openMrs.resetRequests();
        SENAITE_GET_URLS.clear();

        Bundle taskBundle =
                FhirContext.forR4().newJsonParser().parseResource(Bundle.class, openMrs.taskBundleJson("requested"));
        Exchange exchange = new DefaultExchange(contextExtension.getContext());
        exchange.getMessage().setBody(taskBundle);
        getTaskProcessor().process(exchange);

        openMrs.verifyTaskUpdatedTo("accepted");
        openMrs.verifyNoResultResourceCreates();
        assertFalse(
                SENAITE_GET_URLS.stream().anyMatch(url -> url.startsWith("complete=true")),
                "non-published full scan must omit complete=true: " + SENAITE_GET_URLS);
        assertTrue(
                SENAITE_GET_URLS.stream().anyMatch(url -> url.startsWith("complete=false")),
                "full scan should still fetch catalog AnalysisRequest: " + SENAITE_GET_URLS);
    }

    @Test
    @Order(5)
    @DisplayName("Should sync results and complete Task when sample is published")
    public void shouldSyncResultsAndCompleteTaskWhenSamplePublished() throws Exception {
        AnalysisRequestDTO analysisRequest = ensureAnalysisRequestReceived();
        senaiteWorkflow.publishSampleWithResult(analysisRequest.getUid(), analysisRequest.getClient(), "12.5");
        assertEquals("published", refetchAnalysisRequest().getReviewState());

        openMrs.stubForPublishedSync();
        openMrs.resetRequests();
        SENAITE_GET_URLS.clear();

        getAnalysisRequestChangePoller().poll(getProducerTemplate());

        openMrs.verifyResultResourcesCreated();
        openMrs.verifyTaskUpdatedTo("completed");
        assertTrue(
                SENAITE_GET_URLS.stream().anyMatch(url -> url.startsWith("complete=true:" + SERVICE_REQUEST_ID)),
                "published sync must load full AnalysisRequest: " + SENAITE_GET_URLS);
    }

    @Test
    @Order(6)
    @DisplayName("Should not reload published sample after Task is completed")
    public void shouldNotReloadPublishedSampleAfterTaskCompleted() throws Exception {
        ensureAnalysisRequestPublished();

        openMrs.stubForCompletedTaskSkip();
        openMrs.resetRequests();
        SENAITE_GET_URLS.clear();

        getAnalysisRequestChangePoller().poll(getProducerTemplate());

        openMrs.verifyNoTaskUpdate();
        openMrs.verifyNoResultResourceCreates();
        assertFalse(
                SENAITE_GET_URLS.stream().anyMatch(url -> url.startsWith("complete=true")),
                "completed Task must not trigger another full AnalysisRequest load: " + SENAITE_GET_URLS);
    }

    private Bundle loadServiceRequestBundle() {
        String json = readJSON("fhir.bundle/service-request-bundle.json")
                .replace(DEFAULT_SERVICE_REQUEST_ID, SERVICE_REQUEST_ID);
        return FhirContext.forR4().newJsonParser().parseResource(Bundle.class, json);
    }

    private AnalysisRequestDTO createAnalysisRequest() throws IOException {
        openMrs.stubForLabOrderCreate();
        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_FHIR_EVENT_TYPE, "c");
        sendBodyAndHeaders("direct:service-request-to-analysis-request-processor", serviceRequestBundle, headers);

        AnalysisRequestDTO analysisRequest = fetchAnalysisRequest(PATIENT_ID, SERVICE_REQUEST_ID);
        assertNotNull(analysisRequest);
        assertNotNull(analysisRequest.getUid());
        return analysisRequest;
    }

    private AnalysisRequestDTO ensureAnalysisRequestReceived() throws IOException {
        AnalysisRequestDTO analysisRequest = fetchAnalysisRequest(PATIENT_ID, SERVICE_REQUEST_ID);
        if (analysisRequest == null || analysisRequest.getUid() == null) {
            analysisRequest = createAnalysisRequest();
        }
        String state = analysisRequest.getReviewState();
        if ("published".equalsIgnoreCase(state)
                || "verified".equalsIgnoreCase(state)
                || "to_be_verified".equalsIgnoreCase(state)
                || "sample_received".equalsIgnoreCase(state)) {
            return analysisRequest;
        }
        if ("sample_due".equalsIgnoreCase(state)) {
            senaiteWorkflow.receive(analysisRequest.getUid(), analysisRequest.getClient());
            analysisRequest = refetchAnalysisRequest();
        }
        assertEquals("sample_received", analysisRequest.getReviewState());
        return analysisRequest;
    }

    private AnalysisRequestDTO ensureAnalysisRequestPublished() throws IOException {
        AnalysisRequestDTO analysisRequest = fetchAnalysisRequest(PATIENT_ID, SERVICE_REQUEST_ID);
        if (analysisRequest == null || analysisRequest.getUid() == null) {
            analysisRequest = createAnalysisRequest();
        }
        if (!"published".equalsIgnoreCase(analysisRequest.getReviewState())) {
            senaiteWorkflow.publishSampleWithResult(analysisRequest.getUid(), analysisRequest.getClient(), "12.5");
            analysisRequest = refetchAnalysisRequest();
        }
        assertEquals("published", analysisRequest.getReviewState());
        return analysisRequest;
    }

    private AnalysisRequestDTO refetchAnalysisRequest() throws IOException {
        AnalysisRequestDTO analysisRequest = fetchAnalysisRequest(PATIENT_ID, SERVICE_REQUEST_ID);
        assertNotNull(analysisRequest);
        assertNotNull(analysisRequest.getUid());
        return analysisRequest;
    }

    private AnalysisRequestDTO fetchAnalysisRequest(String patientId, String serviceRequestId) throws IOException {
        String response =
                fetchFromSenaite(String.format(GET_ANALYSIS_REQUEST_BY_PATIENT_ID, patientId, serviceRequestId));
        TypeReference<SenaiteResponseWrapper<AnalysisRequestItem>> typeReference = new TypeReference<>() {};
        SenaiteResponseWrapper<AnalysisRequestItem> wrapper = OBJECT_MAPPER.readValue(response, typeReference);
        if (wrapper.getItems() == null || wrapper.getItems().isEmpty()) {
            return null;
        }
        return AnalysisRequestMapper.map(wrapper);
    }

    private String fetchFromSenaite(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", getSenaiteConfig().authHeader())
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
