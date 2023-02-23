/*******************************************************************************
 * Copyright (c) 2020 Sierra Wireless and others.
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
package org.eclipse.leshan.integration.tests.read;

import static org.eclipse.leshan.core.ResponseCode.BAD_REQUEST;
import static org.eclipse.leshan.core.ResponseCode.CONTENT;
import static org.eclipse.leshan.integration.tests.util.LeshanTestClientBuilder.givenClientUsing;
import static org.eclipse.leshan.integration.tests.util.LeshanTestServerBuilder.givenServerUsing;
import static org.eclipse.leshan.integration.tests.util.TestUtil.assertContentFormat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.stream.Stream;

import org.eclipse.leshan.client.object.LwM2mTestObject;
import org.eclipse.leshan.core.endpoint.Protocol;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.LwM2mResourceInstance;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.util.TestLwM2mId;
import org.eclipse.leshan.integration.tests.util.LeshanTestClient;
import org.eclipse.leshan.integration.tests.util.LeshanTestServer;
import org.eclipse.leshan.integration.tests.util.junit5.extensions.ArgumentsUtil;
import org.eclipse.leshan.integration.tests.util.junit5.extensions.BeforeEachParameterizedResolver;
import org.eclipse.leshan.server.registration.Registration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(BeforeEachParameterizedResolver.class)
public class ReadSingleValueTest {

    /*---------------------------------/
     *  Parameterized Tests
     * -------------------------------*/
    @ParameterizedTest(name = "{0} over {1} - Client using {2} - Server using {3}")
    @MethodSource("transports")
    @Retention(RetentionPolicy.RUNTIME)
    private @interface TestAllCases {
    }

    static Stream<Arguments> transports() {

        Object[][] transports = new Object[][]
        // ProtocolUsed - Client Endpoint Provider - Server Endpoint Provider
        { { Protocol.COAP, "Californium", "Californium" } };

        Object[] contentFormats = new Object[] { //
                ContentFormat.TEXT, //
                ContentFormat.TLV, //
                ContentFormat.CBOR, //
                ContentFormat.JSON, //
                ContentFormat.SENML_JSON, //
                ContentFormat.SENML_CBOR };

        // for each transport, create 1 test by format.
        return Stream.of(ArgumentsUtil.combine(contentFormats, transports));
    }

    /*---------------------------------/
     *  Set-up and Tear-down Tests
     * -------------------------------*/

    LeshanTestServer server;
    LeshanTestClient client;
    Registration currentRegistration;

    @BeforeEach
    public void start(ContentFormat format, Protocol givenProtocol, String givenClientEndpointProvider,
            String givenServerEndpointProvider) {
        server = givenServerUsing(givenProtocol).with(givenServerEndpointProvider).build();
        server.start();
        client = givenClientUsing(givenProtocol).with(givenClientEndpointProvider).connectingTo(server).build();
        client.start();
        server.waitForNewRegistrationOf(client);
        client.waitForRegistrationTo(server);

        currentRegistration = server.getRegistrationFor(client);

    }

    @AfterEach
    public void stop() throws InterruptedException {
        if (client != null)
            client.destroy(false);
        if (server != null)
            server.destroy();
    }

    /*---------------------------------/
     *  Tests
     * -------------------------------*/
    @TestAllCases
    public void can_read_resource(ContentFormat contentFormat, Protocol givenProtocol,
            String givenClientEndpointProvider, String givenServerEndpointProvider) throws InterruptedException {
        // read device model number
        ReadResponse response = server.send(currentRegistration, new ReadRequest(contentFormat, 3, 0, 1));

        // verify result
        assertEquals(CONTENT, response.getCode());
        assertContentFormat(contentFormat, response);

        LwM2mResource resource = (LwM2mResource) response.getContent();
        assertEquals(1, resource.getId());
        assertEquals("IT-TEST-123", resource.getValue());

    }

    @TestAllCases
    public void can_read_resource_instance(ContentFormat contentFormat, Protocol givenProtocol,
            String givenClientEndpointProvider, String givenServerEndpointProvider) throws InterruptedException {
        // read resource instance
        ReadResponse response = server.send(currentRegistration,
                new ReadRequest(contentFormat, TestLwM2mId.TEST_OBJECT, 0, TestLwM2mId.MULTIPLE_STRING_VALUE, 0));

        // verify result
        assertEquals(CONTENT, response.getCode());
        assertContentFormat(contentFormat, response);

        LwM2mResourceInstance resourceInstance = (LwM2mResourceInstance) response.getContent();
        assertEquals(0, resourceInstance.getId());
        assertEquals(LwM2mTestObject.INITIAL_STRING_VALUE, resourceInstance.getValue());
    }

    @TestAllCases
    public void cannot_read_non_multiple_resource_instance(ContentFormat contentFormat, Protocol givenProtocol,
            String givenClientEndpointProvider, String givenServerEndpointProvider) throws InterruptedException {
        // read single instance resource
        ReadResponse response = server.send(currentRegistration,
                new ReadRequest(contentFormat, TestLwM2mId.TEST_OBJECT, 0, TestLwM2mId.INTEGER_VALUE, 0));

        // verify result
        assertEquals(BAD_REQUEST, response.getCode());
        assertEquals("invalid path : resource is not multiple", response.getErrorMessage());
    }
}
