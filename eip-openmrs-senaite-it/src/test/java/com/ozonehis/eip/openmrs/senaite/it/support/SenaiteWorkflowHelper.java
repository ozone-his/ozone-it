/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.eip.openmrs.senaite.it.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ozonehis.eip.openmrs.senaite.config.SenaiteConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Drives SENAITE AnalysisRequest workflow transitions for integration tests.
 */
public class SenaiteWorkflowHelper {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private final SenaiteConfig senaiteConfig;

    private boolean selfVerificationEnabled;

    public SenaiteWorkflowHelper(SenaiteConfig senaiteConfig) {
        this.senaiteConfig = senaiteConfig;
    }

    public void receive(String analysisRequestUid, String clientUid) {
        transitionAnalysisRequest(analysisRequestUid, clientUid, "receive");
    }

    public void submit(String analysisRequestUid, String clientUid) {
        transitionAnalysisRequest(analysisRequestUid, clientUid, "submit");
    }

    public void verify(String analysisRequestUid, String clientUid) {
        transitionAnalysisRequest(analysisRequestUid, clientUid, "verify");
    }

    public void publish(String analysisRequestUid, String clientUid) {
        transitionAnalysisRequest(analysisRequestUid, clientUid, "publish");
    }

    public void setAnalysisResult(String analysisUid, String result) {
        postJson(
                senaiteConfig.getSenaiteBaseUrl() + "/@@API/senaite/v1/update",
                "{\"uid\":\"" + analysisUid + "\",\"Result\":\"" + result + "\"}");
    }

    /**
     * Receive the sample (if still sample_due), enter results on each Analysis (submit/verify at
     * analysis level), then publish the AnalysisRequest.
     */
    public void publishSampleWithResult(String analysisRequestUid, String clientUid, String resultValue) {
        ensureSelfVerificationAllowed();
        String state = getReviewState(analysisRequestUid);
        if ("published".equalsIgnoreCase(state)) {
            return;
        }
        if ("sample_due".equalsIgnoreCase(state) || state == null) {
            receive(analysisRequestUid, clientUid);
            waitForReviewState(analysisRequestUid, "sample_received");
            state = getReviewState(analysisRequestUid);
        }
        if ("sample_received".equalsIgnoreCase(state)) {
            List<String> analysisUids = getAnalysisUids(analysisRequestUid);
            if (analysisUids.isEmpty()) {
                throw new IllegalStateException("No analyses found on AnalysisRequest " + analysisRequestUid);
            }
            for (String analysisUid : analysisUids) {
                // SENAITE transitions submit/verify on Analyses, not on the AnalysisRequest.
                postJson(
                        senaiteConfig.getSenaiteBaseUrl() + "/@@API/senaite/v1/update",
                        "{\"uid\":\"" + analysisUid + "\",\"Result\":\"" + resultValue
                                + "\",\"transition\":\"submit\"}");
            }
            waitForReviewState(analysisRequestUid, "to_be_verified");
            state = getReviewState(analysisRequestUid);
        }
        if ("to_be_verified".equalsIgnoreCase(state)) {
            List<String> analysisUids = getAnalysisUids(analysisRequestUid);
            for (String analysisUid : analysisUids) {
                postJson(
                        senaiteConfig.getSenaiteBaseUrl() + "/@@API/senaite/v1/update",
                        "{\"uid\":\"" + analysisUid + "\",\"transition\":\"verify\"}");
            }
            waitForReviewState(analysisRequestUid, "verified");
            state = getReviewState(analysisRequestUid);
        }
        if ("verified".equalsIgnoreCase(state)) {
            publish(analysisRequestUid, clientUid);
            waitForReviewState(analysisRequestUid, "published");
        }
        waitForReviewState(analysisRequestUid, "published");
    }

    public String getReviewState(String analysisRequestUid) {
        JsonNode item = getAnalysisRequest(analysisRequestUid);
        JsonNode state = item.get("review_state");
        return state == null || state.isNull() ? null : state.asText();
    }

    public String getClientUid(String analysisRequestUid) {
        JsonNode item = getAnalysisRequest(analysisRequestUid);
        if (item.hasNonNull("getClientUID")) {
            return item.get("getClientUID").asText();
        }
        if (item.hasNonNull("ClientUID")) {
            return item.get("ClientUID").asText();
        }
        if (item.hasNonNull("Client") && item.get("Client").isTextual()) {
            return item.get("Client").asText();
        }
        return null;
    }

    public List<String> getAnalysisUids(String analysisRequestUid) {
        JsonNode item = getAnalysisRequest(analysisRequestUid);
        List<String> uids = new ArrayList<>();
        JsonNode analyses = item.get("Analyses");
        if (analyses == null || !analyses.isArray()) {
            analyses = item.get("getAnalyses");
        }
        if (analyses != null && analyses.isArray()) {
            for (JsonNode analysis : analyses) {
                if (analysis.hasNonNull("uid")) {
                    uids.add(analysis.get("uid").asText());
                } else if (analysis.isTextual()) {
                    uids.add(analysis.asText());
                }
            }
        }
        if (uids.isEmpty()) {
            JsonNode analysesUids = item.get("AnalysesUIDs");
            if (analysesUids != null && analysesUids.isArray()) {
                for (JsonNode uid : analysesUids) {
                    uids.add(uid.asText());
                }
            }
        }
        return uids;
    }

    public JsonNode getAnalysisRequest(String analysisRequestUid) {
        String url = senaiteConfig.getSenaiteBaseUrl()
                + "/@@API/senaite/v1/AnalysisRequest?catalog=senaite_catalog_sample&complete=true&UID="
                + analysisRequestUid;
        String body = get(url);
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode items = root.get("items");
            if (items != null && items.isArray() && !items.isEmpty()) {
                return items.get(0);
            }
            // Fallback: some SENAITE builds match on lowercase uid
            url = senaiteConfig.getSenaiteBaseUrl()
                    + "/@@API/senaite/v1/AnalysisRequest?catalog=senaite_catalog_sample&complete=true&uid="
                    + analysisRequestUid;
            body = get(url);
            root = OBJECT_MAPPER.readTree(body);
            items = root.get("items");
            if (items != null && items.isArray() && !items.isEmpty()) {
                return items.get(0);
            }
            throw new IllegalStateException("AnalysisRequest not found for uid " + analysisRequestUid + ": " + body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse AnalysisRequest " + analysisRequestUid, e);
        }
    }

    public void ensureSelfVerificationAllowed() {
        if (selfVerificationEnabled) {
            return;
        }
        try {
            postJson(
                    senaiteConfig.getSenaiteBaseUrl() + "/@@API/senaite/v1/update",
                    "{\"portal_type\":\"BikaSetup\",\"path\":\"/senaite/bika_setup\",\"SelfVerificationEnabled\":true}");
        } catch (RuntimeException ignored) {
            // Some images already allow self-verification or use a different setup path.
        }
        selfVerificationEnabled = true;
    }

    private void transitionAnalysisRequest(String analysisRequestUid, String clientUid, String transition) {
        // Receive/cancel require Client; later transitions (e.g. publish) reject updates to Client.
        String body;
        if ("receive".equals(transition) || "cancel".equals(transition)) {
            body = "{\"transition\":\"" + transition + "\",\"Client\":\"" + clientUid + "\"}";
        } else {
            body = "{\"transition\":\"" + transition + "\"}";
        }
        postJson(
                senaiteConfig.getSenaiteBaseUrl() + "/@@API/senaite/v1/AnalysisRequest/update/" + analysisRequestUid,
                body);
    }

    private void waitForReviewState(String analysisRequestUid, String expectedState) {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        String lastState = null;
        while (System.currentTimeMillis() < deadline) {
            lastState = getReviewState(analysisRequestUid);
            if (expectedState.equalsIgnoreCase(lastState)) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException("Timed out waiting for AnalysisRequest " + analysisRequestUid + " to reach "
                + expectedState + " (last=" + lastState + ")");
    }

    private String get(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", senaiteConfig.authHeader())
                .get()
                .build();
        return execute(request);
    }

    private String postJson(String url, String jsonBody) {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", senaiteConfig.authHeader())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        return execute(request);
    }

    private String execute(Request request) {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException(
                        "HTTP " + response.code() + " for " + request.method() + " " + request.url() + ": " + body);
            }
            if (body.contains("\"success\": false") || body.contains("\"success\":false")) {
                throw new IOException(
                        "SENAITE request failed for " + request.method() + " " + request.url() + ": " + body);
            }
            return body;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
