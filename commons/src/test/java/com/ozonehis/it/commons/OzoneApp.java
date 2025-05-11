package com.ozonehis.it.commons;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum OzoneApp {
	
	OPENMRS("http://localhost/openmrs", "/health/started", 3,
			"docker-compose-openmrs.yml", "docker-compose-openmrs-sso.yml"),
	
	ODOO("http://localhost:8069", "/health", 4,
			"docker-compose-odoo.yml", "docker-compose-odoo-sso.yml"),
	
	KEYCLOAK("http://localhost:8084", "/health", 2,
			"docker-compose-keycloak.yml");
	
	private static final String[] COMMON_DOCKER_COMPOSE_FILES = {
			"docker-compose-common.yml"
	};
	
	private final String[] dockerComposeFiles;
	
	private final String baseUrl;
	
	private final String healthCheckEndpoint;
	
	private final int sortOrder;
	
	OzoneApp(String baseUrl, String healthCheckEndpoint, int sortOrder, String... dockerComposeFiles) {
		this.baseUrl = baseUrl;
		this.healthCheckEndpoint = healthCheckEndpoint;
		this.sortOrder = sortOrder;
		this.dockerComposeFiles = dockerComposeFiles;
	}
	
	public String baseUrl() {
		return baseUrl;
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
}
