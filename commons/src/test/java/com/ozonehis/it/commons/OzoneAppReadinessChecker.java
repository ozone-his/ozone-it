package com.ozonehis.it.commons;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestTemplate;

/**
 * This class is responsible for checking the readiness of Ozone applications. It can be extended to implement specific
 * readiness checks for different applications.
 */
@Slf4j
public class OzoneAppReadinessChecker {
	
	/**
	 * Checks if a specific Ozone application is ready by making a GET request to its health check endpoint.
	 *
	 * @param app the Ozone application to check
	 * @return true if the application is ready, false otherwise
	 */
	public static boolean isReady(OzoneApp app) {
		RestTemplate restTemplate = new RestTemplate();
		String readinessUri = app.baseUrl() + app.healthCheckEndpoint();
		try {
			HttpStatusCode status = restTemplate.getForEntity(readinessUri, String.class).getStatusCode();
			if (status.is2xxSuccessful()) {
				log.info("{} application is ready at {}", app.name(), readinessUri);
				return true;
			} else {
				log.warn("{} application is not available. Status code: {}", app.name(), status);
				return false;
			}
		}
		catch (Exception e) {
			log.warn("{} application not ready: {}", app.name(), e.getMessage());
			return false;
		}
	}
	
	/**
	 * Checks if all specified Ozone applications are ready.
	 *
	 * @param apps the array of Ozone applications to check
	 * @return true if all applications are ready, false otherwise
	 */
	public static boolean areAllAppsReady(UnmodifiableList<OzoneApp> apps) {
		for (OzoneApp app : apps) {
			if (!isReady(app)) {
				log.error("{} application is not ready", app.name());
				return false;
			}
		}
		log.info("All specified Ozone applications are ready.");
		return true;
	}
	
	/**
	 * Waits until all specified Ozone applications are ready.
	 *
	 * @param apps the array of Ozone applications to check
	 * @param timeoutSeconds the maximum time to wait in seconds
	 * @return true if all applications are ready within the timeout,false otherwise
	 */
	public static boolean waitForAppsReady(int timeoutSeconds, UnmodifiableList<OzoneApp> apps) {
		int elapsedTime = 0;
		while (elapsedTime < timeoutSeconds) {
			if (areAllAppsReady(apps)) {
				log.info("All Ozone applications are ready after {} seconds.", elapsedTime);
				return true;
			}
			try {
				Thread.sleep(1000); // Wait for 1 second before checking again
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.error("Thread interrupted while waiting for apps to be ready", e);
			}
			elapsedTime++;
		}
		log.error("Timeout reached. Not all Ozone applications are ready after {} seconds.", timeoutSeconds);
		return false;
	}
}
