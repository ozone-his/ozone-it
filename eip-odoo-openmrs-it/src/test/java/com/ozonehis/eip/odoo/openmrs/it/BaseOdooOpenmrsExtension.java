/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.eip.odoo.openmrs.it;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ozonehis.it.commons.OzoneApp;
import com.ozonehis.it.commons.OzoneAppReadinessChecker;
import com.ozonehis.it.commons.OzoneRunner;
import java.util.List;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

class BaseOdooOpenmrsExtension implements BeforeAllCallback, AfterAllCallback {

    private static boolean isRunning = false;

    private static final UnmodifiableList<OzoneApp> apps =
            new UnmodifiableList<>(List.of(OzoneApp.OPENMRS, OzoneApp.ODOO));

    protected static OzoneRunner runner;

    protected static boolean started;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!isRunning) {
            runner = new OzoneRunner();
            started = runner.startOzone(apps);
            boolean allReady = OzoneAppReadinessChecker.waitForAppsReady(360, apps);

            assertTrue(started);
            assertTrue(allReady);

            isRunning = true;

            // Wait for 30 secs to ensure stability before running tests
            Thread.sleep(30000);

            var ozoneApps = runner.getRunningApps();

            assertTrue(ozoneApps.contains(OzoneApp.ODOO));
            assertTrue(ozoneApps.contains(OzoneApp.OPENMRS));

            context.getRoot()
                    .getStore(ExtensionContext.Namespace.GLOBAL)
                    .put("ozoneRunner", (ExtensionContext.Store.CloseableResource) () -> {
                        runner.destroy();
                        isRunning = false;
                    });
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (isRunning) {
            try {
                context.getRoot()
                        .getStore(ExtensionContext.Namespace.GLOBAL)
                        .get("ozoneRunner", ExtensionContext.Store.CloseableResource.class)
                        .close();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
