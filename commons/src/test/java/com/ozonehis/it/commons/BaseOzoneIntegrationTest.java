package com.ozonehis.it.commons;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class BaseOzoneIntegrationTest {
    
    private static OzoneRunner runner;
    
    protected static boolean started;

    @BeforeAll
    static void setUp() throws Exception {
        runner = new OzoneRunner();
        started = runner.startOzone(List.of(OzoneApp.OPENMRS, OzoneApp.ODOO));
    }
    
    @Test
    @DisplayName("It should start Ozone successfully")
    void shouldStartOzoneInstance() {
        // Your test code here
        assertTrue(started, "Ozone should be started successfully");
    }

    @AfterAll
    static void tearDown() throws Exception {
        // sleep 2 minutes
        //Thread.sleep(2 * 60 * 1000);
        if (runner != null) {
            runner.close();
        }
    }
}
