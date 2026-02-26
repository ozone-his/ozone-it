/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.eip.odoo.openmrs.it.camel;

import com.ozonehis.camel.test.infra.odoo.services.OdooLocalContainerService;
import com.ozonehis.camel.test.infra.odoo.services.OdooService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@Slf4j
public class BaseOdooExtension implements BeforeAllCallback, AfterAllCallback {

    private static boolean isOdooRunning = false;

    @Getter
    private static OdooService odooService;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!isOdooRunning) {
            odooService = new OdooLocalContainerService();
            odooService.initialize();

            // Wait for Odoo to be fully initialized and ready to accept connections
            try {
                log.info("Waiting for Odoo to be fully initialized...");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            isOdooRunning = true;

            context.getRoot()
                    .getStore(ExtensionContext.Namespace.GLOBAL)
                    .put("odooService", (ExtensionContext.Store.CloseableResource) odooService::shutdown);
        } else {
            log.info("Odoo service is already running. Skipping initialization.");
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (isOdooRunning) {
            try {
                context.getRoot()
                        .getStore(ExtensionContext.Namespace.GLOBAL)
                        .get("odooService", ExtensionContext.Store.CloseableResource.class)
                        .close();
                log.info("Odoo service shutdown completed.");
            } catch (Throwable e) {
                throw new RuntimeException(e);
            } finally {
                isOdooRunning = false;
            }
        }
    }
}
