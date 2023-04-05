/*******************************************************************************
 * Copyright (c) 2023 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.integration.tests.util.assertion;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.leshan.core.ResponseCode;
import org.eclipse.leshan.core.response.LwM2mResponse;

public class LwM2mResponseAssert extends AbstractAssert<LwM2mResponseAssert, LwM2mResponse> {

    public LwM2mResponseAssert(LwM2mResponse actual) {
        super(actual, LwM2mResponseAssert.class);
    }

    public static LwM2mResponseAssert assertThat(LwM2mResponse actual) {
        return new LwM2mResponseAssert(actual);
    }

    public LwM2mResponseAssert hasCode(ResponseCode expectedCode) {
        isNotNull();

        if (actual.getCode() == null) {
            failWithMessage("Response MUST NOT have <null> response code");
        }
        if (!actual.getCode().equals(expectedCode)) {
            failWithMessage("Expected <%s> ResponseCode for <%s> response", expectedCode, actual);
        }
        return this;
    }
}
