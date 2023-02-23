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
package org.eclipse.leshan.integration.tests.write;

import static org.eclipse.leshan.integration.tests.util.LeshanTestClientBuilder.givenClientUsing;
import static org.eclipse.leshan.integration.tests.util.LeshanTestServerBuilder.givenServerUsing;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.californium.core.coap.Response;
import org.eclipse.leshan.core.ResponseCode;
import org.eclipse.leshan.core.endpoint.Protocol;
import org.eclipse.leshan.core.model.ResourceModel.Type;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.LwM2mResourceInstance;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
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
public class WriteOpaqueValueTest {

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
                ContentFormat.OPAQUE, //
                ContentFormat.TEXT, //
                ContentFormat.CBOR, //
                ContentFormat.TLV, //
                ContentFormat.fromCode(ContentFormat.OLD_TLV_CODE), //
                ContentFormat.fromCode(ContentFormat.OLD_JSON_CODE), //
                ContentFormat.JSON, //
                ContentFormat.SENML_JSON, //
                ContentFormat.SENML_CBOR //
        };

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
    public void start(ContentFormat contentFormat, Protocol givenProtocol, String givenClientEndpointProvider,
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
    public void write_opaque_resource(ContentFormat contentFormat, Protocol givenProtocol,
            String givenClientEndpointProvider, String givenServerEndpointProvider) throws InterruptedException {
        // write resource
        byte[] expectedvalue = new byte[] { 1, 2, 3 };
        WriteResponse response = server.send(currentRegistration,
                new WriteRequest(contentFormat, TestLwM2mId.TEST_OBJECT, 0, TestLwM2mId.OPAQUE_VALUE, expectedvalue));

        // verify result
        assertEquals(ResponseCode.CHANGED, response.getCode());
        assertNotNull(response.getCoapResponse());
        assertThat(response.getCoapResponse(), is(instanceOf(Response.class)));

        // read resource to check the value changed
        ReadResponse readResponse = server.send(currentRegistration,
                new ReadRequest(contentFormat, TestLwM2mId.TEST_OBJECT, 0, TestLwM2mId.OPAQUE_VALUE));
        LwM2mResource resource = (LwM2mResource) readResponse.getContent();
        assertArrayEquals(expectedvalue, (byte[]) resource.getValue());
    }

    @TestAllCases
    public void write_opaque_resource_instance(ContentFormat contentFormat, Protocol givenProtocol,
            String givenClientEndpointProvider, String givenServerEndpointProvider) throws InterruptedException {
        // write 2 resource instances using TLV
        Map<Integer, byte[]> values = new HashMap<>();
        byte[] firstExpectedvalue = new byte[] { 1, 2, 3 };
        byte[] secondExpectedvalue = new byte[] { 4, 5, 6 };
        values.put(2, firstExpectedvalue);
        values.put(3, secondExpectedvalue);

        WriteResponse response = server.send(currentRegistration, new WriteRequest(ContentFormat.TLV,
                TestLwM2mId.TEST_OBJECT, 0, TestLwM2mId.MULTIPLE_OPAQUE_VALUE, values, Type.OPAQUE));

        // verify result
        assertEquals(ResponseCode.CHANGED, response.getCode());
        assertNotNull(response.getCoapResponse());
        assertThat(response.getCoapResponse(), is(instanceOf(Response.class)));

        // read first instance using parameter content format
        ReadResponse readResponse = server.send(currentRegistration,
                new ReadRequest(contentFormat, TestLwM2mId.TEST_OBJECT, 0, TestLwM2mId.MULTIPLE_OPAQUE_VALUE, 2));
        LwM2mResourceInstance resource = (LwM2mResourceInstance) readResponse.getContent();
        assertArrayEquals(firstExpectedvalue, (byte[]) resource.getValue());

        // read second instance using parameter content format
        readResponse = server.send(currentRegistration,
                new ReadRequest(contentFormat, TestLwM2mId.TEST_OBJECT, 0, TestLwM2mId.MULTIPLE_OPAQUE_VALUE, 3));
        resource = (LwM2mResourceInstance) readResponse.getContent();
        assertArrayEquals(secondExpectedvalue, (byte[]) resource.getValue());

        // write second resource instance using parameter content format
        byte[] newExpectedvalue = new byte[] { 7, 8, 9, 10 };
        response = server.send(currentRegistration, new WriteRequest(contentFormat, TestLwM2mId.TEST_OBJECT, 0,
                TestLwM2mId.MULTIPLE_OPAQUE_VALUE, 3, newExpectedvalue, Type.OPAQUE));

        // verify result
        assertEquals(ResponseCode.CHANGED, response.getCode());
        assertNotNull(response.getCoapResponse());
        assertThat(response.getCoapResponse(), is(instanceOf(Response.class)));

        // read second instance using parameter content format
        readResponse = server.send(currentRegistration,
                new ReadRequest(contentFormat, TestLwM2mId.TEST_OBJECT, 0, TestLwM2mId.MULTIPLE_OPAQUE_VALUE, 3));
        resource = (LwM2mResourceInstance) readResponse.getContent();
        assertArrayEquals(newExpectedvalue, (byte[]) resource.getValue());
    }
}
