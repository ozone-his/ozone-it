/*
 * Copyright © 2025, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.ozonehis.eip.odoo.openmrs.it;

import com.ozonehis.eip.odoo.openmrs.client.OdooClient;
import com.ozonehis.eip.odoo.openmrs.client.OdooUtils;
import com.ozonehis.it.commons.BaseOzoneIntegrationTest;
import com.ozonehis.it.commons.OzoneApp;
import java.util.List;
import lombok.Getter;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.env.StandardEnvironment;

@Getter
@ExtendWith(BaseOdooOpenmrsExtension.class)
public abstract class BaseOdooOpenmrsIntegrationTest extends BaseOzoneIntegrationTest {

    protected static OdooUtils odooUtils;

    public static final String odooCustomerDobField = "x_customer_dob";

    public static final List<String> partnerDefaultAttributes =
            List.of("id", "name", "ref", "street", "street2", "city", "zip", "active", "comment");

    protected static void setupOdooUtils() {
        odooUtils = new OdooUtils();
        odooUtils.setEnvironment(new StandardEnvironment());
    }

    protected static OdooClient odooClient() {
        return new OdooClient(
                OzoneApp.ODOO.baseUrl(),
                "odoo",
                OzoneApp.ODOO.credentials().username(),
                OzoneApp.ODOO.credentials().password());
    }
}
