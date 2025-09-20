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
import static com.ozonehis.it.commons.OzoneConstants.OZONE_TEST_WORKSPACE;

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

    private List<OzoneApp> runningApps = List.of();

    public List<OzoneApp> getRunningApps() {
        return new ArrayList<>(runningApps);
    }

    public OzoneRunner() throws IOException {
        this.projectRoot = findProjectRoot();
        this.testDir = projectRoot.resolve(OZONE_TEST_WORKSPACE);
        this.ozoneDir = testDir.resolve("ozone");

        if (Files.exists(testDir)) {
            deleteDirectory(testDir.toFile());
        }
        Files.createDirectories(projectRoot.resolve("target"));
        Files.createDirectories(testDir);
    }

    private void prepareOzoneEnvironment() throws IOException {
        if (Files.exists(ozoneDir)) {
            deleteDirectory(ozoneDir.toFile());
        }
        copyOzoneToWorkspace();
    }

    public boolean startOzone(List<OzoneApp> apps, int timeoutMinutes) throws IOException, InterruptedException {
        prepareOzoneEnvironment();
        this.runningApps = new ArrayList<>(apps);
        overrideDockerComposeFiles(apps);
        return executeScript("start.sh", timeoutMinutes);
    }

    /**
     * Starts Ozone using the start.sh script with the default configuration.
     *
     * @return true if started successfully
     * @throws IOException          if there's an error starting Ozone
     * @throws InterruptedException if the process is interrupted
     */
    public boolean start() throws IOException, InterruptedException {
        return startOzone(List.of(), DEFAULT_STARTUP_TIMEOUT_MINUTES);
    }

    /**
     * Starts Ozone with SSO configuration using start-with-sso.sh script.
     *
     * @return true if started successfully
     * @throws IOException          if there's an error starting Ozone
     * @throws InterruptedException if the process is interrupted
     */
    public boolean startWithSSO() throws IOException, InterruptedException {
        prepareOzoneEnvironment();
        return executeScript("start-with-sso.sh", DEFAULT_STARTUP_TIMEOUT_MINUTES);
    }

    /**
     * Starts Ozone with demo data using start-demo.sh script.
     *
     * @return true if started successfully
     * @throws IOException          if there's an error starting Ozone
     * @throws InterruptedException if the process is interrupted
     */
    public boolean startWithDemoData() throws IOException, InterruptedException {
        prepareOzoneEnvironment();
        return executeScript("start-demo.sh", DEFAULT_STARTUP_TIMEOUT_MINUTES);
    }

    public boolean startOzone(List<OzoneApp> apps) throws IOException, InterruptedException {
        return startOzone(apps, DEFAULT_STARTUP_TIMEOUT_MINUTES);
    }

    /**
     * Stops the Ozone instance using stop.sh script.
     *
     * @throws IOException          if there's an error stopping Ozone
     * @throws InterruptedException if the process is interrupted
     */
    public void stop() throws IOException, InterruptedException {
        executeScript("stop-demo.sh", 2);
    }

    /**
     * Destroys the Ozone instance using destroy-demo.sh script.
     *
     * @throws IOException          if there's an error destroying Ozone
     * @throws InterruptedException if the process is interrupted
     */
    public void destroy() throws IOException, InterruptedException {
        executeScript("destroy-demo.sh", 2);
    }

    @Override
    public void close() throws Exception {
        try {
            destroy();
        } finally {
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
     * Executes a shell script in the scripts' directory.
     *
     * @param scriptName     the name of the script to execute
     * @param timeoutMinutes timeout in minutes
     * @return true if the script executed successfully
     * @throws IOException          if there's an error executing the script
     * @throws InterruptedException if the process is interrupted
     */
    private boolean executeScript(String scriptName, int timeoutMinutes) throws IOException, InterruptedException {
        Path scriptsDir = ozoneDir.resolve("run/docker/scripts");
        if (!Files.exists(scriptsDir)) {
            throw new IOException("Scripts directory not found at: " + scriptsDir);
        }

        ProcessBuilder processBuilder = new ProcessBuilder("./" + scriptName);
        processBuilder.directory(scriptsDir.toFile());
        processBuilder.inheritIO();
        processBuilder.environment().putAll(xterm);

        log.info("Executing script {} in directory: {}", scriptName, scriptsDir);
        Process process = processBuilder.start();

        boolean completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!completed) {
            log.error("Script {} timed out after {} minutes", scriptName, timeoutMinutes);
            process.destroyForcibly();
            throw new RuntimeException("Script timed out: " + scriptName);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorMessage = new String(process.getErrorStream().readAllBytes());
            log.error("Script {} failed with exit code {}: {}", scriptName, exitCode, errorMessage);
            throw new RuntimeException("Script failed with exit code: " + exitCode + " - " + errorMessage);
        }
        log.info("Script {} executed successfully", scriptName);
        return true;
    }

    public void overrideDockerComposeFiles(List<OzoneApp> apps) throws IOException {
        if (apps == null || apps.isEmpty()) {
            log.warn("No apps specified, using default apps");
        } else {
            log.info("Starting the specified apps: {}", apps);
            List<String> dockerComposeFiles = OzoneApp.sortedDockerComposeFiles(apps);

            Path scriptsDir = ozoneDir.resolve("run/docker/scripts");
            Path dockerComposeFilesPath = scriptsDir.resolve("docker-compose-files.txt");

            String content = String.join("\n", dockerComposeFiles);
            Files.writeString(dockerComposeFilesPath, content);
            log.info("Updated docker-compose-files.txt with: {}", dockerComposeFiles);
        }
    }

    /**
     * Overrides the environment variables in the .env file.
     *
     * @param envVars a map of environment variable names and their values
     * @throws IOException if there's an error reading or writing the .env file
     */
    public void overrideEnvironmentVariables(Map<String, String> envVars) throws IOException {
        Path envFile = ozoneDir.resolve("run/docker/.env");
        if (!Files.exists(envFile)) {
            throw new IOException("Environment file not found at: " + envFile);
        }

        List<String> lines = Files.readAllLines(envFile);
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            String keyValue = entry.getKey() + "=" + entry.getValue();
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(entry.getKey() + "=")) {
                    lines.set(i, keyValue);
                    found = true;
                    break;
                }
            }
            if (!found) {
                lines.add(keyValue);
            }
        }
        Files.write(envFile, lines);
        log.info("Updated environment variables in .env file: {}", envVars);
    }

    private void copyOzoneToWorkspace() throws IOException {
        Path sourceDir = projectRoot.resolve(OZONE_PATH);
        if (!Files.exists(sourceDir)) {
            throw new IOException("Source Ozone directory does not exist: " + sourceDir);
        }

        log.info("Copying fresh Ozone instance from {} to {}", sourceDir, ozoneDir);
        Files.createDirectories(ozoneDir);
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {

            @Override
            @NonNull public FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs)
                    throws IOException {
                Path targetDir = ozoneDir.resolve(sourceDir.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull @SuppressWarnings("ResultOfMethodCallIgnored")
            public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs)
                    throws IOException {
                Path targetFile = ozoneDir.resolve(sourceDir.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                if (file.toString().endsWith(".sh")) {
                    targetFile.toFile().setExecutable(true);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
