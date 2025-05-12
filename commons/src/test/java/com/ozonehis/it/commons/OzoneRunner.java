/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.it.commons;

import static com.ozonehis.it.commons.OzoneConstants.DEFAULT_STARTUP_TIMEOUT_MINUTES;
import static com.ozonehis.it.commons.OzoneConstants.OZONE_PATH;
import static com.ozonehis.it.commons.OzoneConstants.TEST_DIR_NAME;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OzoneRunner implements AutoCloseable {

    private final Path projectRoot;

    private final Path testDir;

    private final Path ozoneDir;

    Map<String, String> xterm = Map.of("TERM", "xterm-256color");

    /**
     * Returns the list of OzoneApps that are currently running in this OzoneRunner instance.
     * Note: This method returns the apps that were configured to run, not necessarily the
     * apps that are actually running and healthy.
     *
     * @return List of OzoneApp that were configured to run, empty list if using default configuration
     */
    private List<OzoneApp> runningApps = List.of();

    public List<OzoneApp> getRunningApps() {
        return new ArrayList<>(runningApps);
    }

    public OzoneRunner() throws IOException {
        this.projectRoot = findProjectRoot();
        this.testDir = projectRoot.resolve(TEST_DIR_NAME);
        this.ozoneDir = testDir.resolve("ozone");

        // Clean up any existing test directory
        if (Files.exists(testDir)) {
            deleteDirectory(testDir.toFile());
        }
        // Ensure the test directory and target directory exist
        Files.createDirectories(projectRoot.resolve("target"));
        Files.createDirectories(testDir);
    }

    public boolean startOzone(List<OzoneApp> apps, int timeoutMinutes) throws IOException, InterruptedException {
        // Clean up the previous Ozone directory if it exists
        if (Files.exists(ozoneDir)) {
            deleteDirectory(ozoneDir.toFile());
        }

        this.runningApps = new ArrayList<>(apps);

        // Copy fresh Ozone files to the workspace
        copyOzoneToWorkspace();

        overrideDockerComposeFiles(apps);

        // Start Ozone
        Path scriptsDir = ozoneDir.resolve("run/docker/scripts");
        log.info("Scripts directory: {}", scriptsDir);

        if (!Files.exists(scriptsDir)) {
            throw new IOException("Scripts directory not found at: " + scriptsDir);
        }

        ProcessBuilder processBuilder = new ProcessBuilder("./start.sh");
        processBuilder.directory(scriptsDir.toFile());
        processBuilder.inheritIO();
        processBuilder.environment().putAll(xterm);

        log.info("Starting Ozone in directory: {}", scriptsDir);
        Process ozoneProcess = processBuilder.start();

        // Wait for the start.sh script to complete
        boolean completed = ozoneProcess.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!completed) {
            log.error("Start script timed out after {} minutes", timeoutMinutes);
            ozoneProcess.destroyForcibly();
            throw new RuntimeException("Start script timed out");
        }

        int exitCode = ozoneProcess.exitValue();
        if (exitCode != 0) {
            log.error("Start script failed with exit code: {}", exitCode);
            throw new RuntimeException("Start script failed with exit code: " + exitCode);
        }
        log.info("Ozone started successfully");

        // Todo: check for apps availability using healthcheck

        return true;
    }

    /**
     * Copies the Ozone package from the project root to the test directory.
     *
     * @throws IOException if there's an error copying files
     */
    private void copyOzoneToWorkspace() throws IOException {
        Path sourceDir = projectRoot.resolve(OZONE_PATH);
        if (!Files.exists(sourceDir)) {
            throw new IOException("Source Ozone directory does not exist: " + sourceDir);
        }

        log.info("Copying fresh Ozone instance from {} to {}", sourceDir, ozoneDir);
        Files.createDirectories(ozoneDir);
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {

            @Override
            @NonNull public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = ozoneDir.resolve(sourceDir.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = ozoneDir.resolve(sourceDir.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);

                // Make shell scripts executable
                if (file.toString().endsWith(".sh")) {
                    targetFile.toFile().setExecutable(true);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Starts Ozone with default apps & default timeout of 10 minutes.
     *
     * @return true if Ozone started successfully, false otherwise
     * @throws IOException          if there's an error starting Ozone
     * @throws InterruptedException if the process is interrupted
     */
    public boolean startOzone() throws IOException, InterruptedException {
        return startOzone(List.of(), DEFAULT_STARTUP_TIMEOUT_MINUTES);
    }

    /**
     * Starts Ozone with the specified apps and a default timeout of 10 minutes.
     *
     * @param apps List of OzoneApp to start
     * @return true if Ozone started successfully, false otherwise
     * @throws IOException          if there's an error starting Ozone
     * @throws InterruptedException if the process is interrupted
     */
    public boolean startOzone(List<OzoneApp> apps) throws IOException, InterruptedException {
        return startOzone(apps, DEFAULT_STARTUP_TIMEOUT_MINUTES);
    }

    /**
     * Stops the Ozone instance gracefully.
     *
     * @throws IOException          if there's an error stopping Ozone
     * @throws InterruptedException if the process is interrupted
     */
    public void stop() throws IOException, InterruptedException {
        Path scriptsDir = ozoneDir.resolve("run/docker/scripts");
        ProcessBuilder stopProcessBuilder = new ProcessBuilder();
        stopProcessBuilder.command("./destroy-demo.sh");
        stopProcessBuilder.environment().putAll(xterm);
        stopProcessBuilder.directory(scriptsDir.toFile());
        stopProcessBuilder.inheritIO();

        Process process = stopProcessBuilder.start();
        boolean stopped = process.waitFor(2, TimeUnit.MINUTES);

        if (!stopped) {
            log.warn("Failed to stop gracefully, forcing shutdown");
            process.destroyForcibly();
        }
    }

    @Override
    public void close() throws Exception {
        try {
            stop();
        } finally {
            // Clean up temporary directory
            if (testDir != null && Files.exists(testDir)) {
                deleteDirectory(testDir.toFile());
            }
        }
    }

    private void deleteDirectory(File directory) {
        if (!directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    if (!file.delete()) {
                        log.warn("Failed to delete file: {}", file);
                    }
                }
            }
        }
        if (!directory.delete()) {
            log.warn("Failed to delete directory: {}", directory);
        }
    }

    private Path findProjectRoot() {
        Path current = Paths.get(".").toAbsolutePath().normalize();
        Path parentPomPath = null;

        while (current != null) {
            Path pomFile = current.resolve("pom.xml");
            if (Files.exists(pomFile)) {
                parentPomPath = current;
            } else {
                if (parentPomPath != null) {
                    return parentPomPath;
                }
            }
            current = current.getParent();
        }

        if (parentPomPath != null) {
            log.info("Found project root directory with pom.xml: {}", parentPomPath);
            return parentPomPath;
        }

        throw new RuntimeException("Could not find project root directory with pom.xml");
    }

    /**
     * Overrides the docker-compose-files.txt file with the docker compose files for the specified apps.
     *
     * @param apps List of OzoneApp to override the docker compose files
     * @throws IOException if there's an error writing to the file
     */
    public void overrideDockerComposeFiles(List<OzoneApp> apps) throws IOException {
        if (apps == null || apps.isEmpty()) {
            log.warn("No apps specified, using default apps");
        } else {
            log.info("Using specified apps: {}", apps);
            List<String> dockerComposeFiles = OzoneApp.sortedDockerComposeFiles(apps);

            Path scriptsDir = ozoneDir.resolve("run/docker/scripts");
            Path dockerComposeFilesPath = scriptsDir.resolve("docker-compose-files.txt");

            // Create the content with each file on a new line
            String content = String.join("\n", dockerComposeFiles);

            // Write the content to the file
            Files.writeString(dockerComposeFilesPath, content);
            log.info("Updated docker-compose-files.txt with: {}", dockerComposeFiles);
        }
    }
}
