/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.it.commons;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum OzoneApp {
    OPENMRS(
            "http://localhost/openmrs",
            new OzoneAppCredentials("admin", "Admin123"),
            "/health/started",
            3,
            "docker-compose-openmrs.yml",
            "docker-compose-openmrs-sso.yml"),

    ODOO(
            "http://localhost:8069",
            new OzoneAppCredentials("admin", "admin"),
            "/",
            4,
            "docker-compose-odoo.yml",
            "docker-compose-odoo-sso.yml"),

    KEYCLOAK(
            "http://localhost:8084",
            new OzoneAppCredentials("admin", "password"),
            "/health",
            2,
            "docker-compose-keycloak.yml");

    private static final String[] COMMON_DOCKER_COMPOSE_FILES = {"docker-compose-common.yml"};

    private final String[] dockerComposeFiles;

    private final String baseUrl;

    private final OzoneAppCredentials credentials;

    private final String healthCheckEndpoint;

    private final int sortOrder;

    OzoneApp(
            String baseUrl,
            OzoneAppCredentials credentials,
            String healthCheckEndpoint,
            int sortOrder,
            String... dockerComposeFiles) {
        this.baseUrl = baseUrl;
        this.credentials = credentials;
        this.healthCheckEndpoint = healthCheckEndpoint;
        this.sortOrder = sortOrder;
        this.dockerComposeFiles = dockerComposeFiles;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public OzoneAppCredentials credentials() {
        return credentials;
    }

    public String healthCheckEndpoint() {
        return healthCheckEndpoint;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public String[] dockerComposeFiles() {
        return dockerComposeFiles;
    }

    /**
     * Returns a sorted list of docker compose files for the given apps. The common docker compose files are added at the
     * beginning of the list.
     *
     * @param apps the list of OzoneApp instances
     * @return a sorted list of docker compose files
     */
    public static List<String> sortedDockerComposeFiles(List<OzoneApp> apps) {
        List<String> sortedDockerComposeFiles = apps.stream()
                .sorted(Comparator.comparingInt(app -> app.sortOrder))
                .flatMap(app -> Stream.of(app.dockerComposeFiles))
                .toList();

        // Add common docker compose files at the beginning
        return Stream.concat(Stream.of(COMMON_DOCKER_COMPOSE_FILES), sortedDockerComposeFiles.stream())
                .collect(Collectors.toList());
    }

    /**
     * Checks if the app is running by verifying the health check endpoint.
     *
     * @return true if the app is running, false otherwise
     */
    public boolean isRunning(OzoneApp app) {
        return OzoneAppReadinessChecker.isReady(app);
    }
}
