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
package org.eclipse.leshan.integration.tests.observe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.leshan.integration.tests.util.LeshanTestClientBuilder.givenClientUsing;
import static org.eclipse.leshan.integration.tests.util.LeshanTestServerBuilder.givenServerUsing;
import static org.eclipse.leshan.integration.tests.util.TestUtil.assertContentFormat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.californium.core.coap.Response;
import org.eclipse.leshan.core.ResponseCode;
import org.eclipse.leshan.core.endpoint.Protocol;
import org.eclipse.leshan.core.node.LwM2mObject;
import org.eclipse.leshan.core.node.LwM2mObjectInstance;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.LwM2mSingleResource;
import org.eclipse.leshan.core.node.TimestampedLwM2mNode;
import org.eclipse.leshan.core.node.codec.DefaultLwM2mEncoder;
import org.eclipse.leshan.core.node.codec.LwM2mEncoder;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.observation.SingleObservation;
import org.eclipse.leshan.core.request.ContentFormat;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.response.ObserveResponse;
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
public class ObserveTimeStampTest {

    /*---------------------------------/
     *  Parameterized Tests
     * -------------------------------*/
    @ParameterizedTest(name = "{0} over COAP - Client using Californium - Server using {3}")
    @MethodSource("transports")
    @Retention(RetentionPolicy.RUNTIME)
    private @interface TestAllCases {
    }

    static Stream<Arguments> transports() {

        Object[][] transports = new Object[][]
        // Server Endpoint Provider
        { { "Californium" } };

        Object[] contentFormats = new Object[] { //
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
    LwM2mEncoder encoder = new DefaultLwM2mEncoder();

    @BeforeEach
    public void start(ContentFormat format, String givenServerEndpointProvider) {
        server = givenServerUsing(Protocol.COAP).with(givenServerEndpointProvider).build();
        server.start();
        client = givenClientUsing(Protocol.COAP).with("Californium").connectingTo(server).build();
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
    public void can_observe_timestamped_resource(ContentFormat contentFormat, String givenServerEndpointProvider)
            throws InterruptedException {
        // observe device timezone
        ObserveResponse observeResponse = server.send(currentRegistration, new ObserveRequest(3, 0, 15));
        assertEquals(ResponseCode.CONTENT, observeResponse.getCode());
        assertNotNull(observeResponse.getCoapResponse());
        assertThat(observeResponse.getCoapResponse(), is(instanceOf(Response.class)));

        // an observation response should have been sent
        SingleObservation observation = observeResponse.getObservation();
        assertEquals("/3/0/15", observation.getPath().toString());
        assertEquals(currentRegistration.getId(), observation.getRegistrationId());
        Set<Observation> observations = server.getObservationService().getObservations(currentRegistration);
        assertTrue(observations.size() == 1, "We should have only one observation");
        assertTrue(observations.contains(observation), "New observation is not there");

        // *** HACK send time-stamped notification as Leshan client does not support it *** //
        // create time-stamped nodes
        TimestampedLwM2mNode mostRecentNode = new TimestampedLwM2mNode(Instant.ofEpochMilli(System.currentTimeMillis()),
                LwM2mSingleResource.newStringResource(15, "Paris"));
        List<TimestampedLwM2mNode> timestampedNodes = new ArrayList<>();
        timestampedNodes.add(mostRecentNode);
        timestampedNodes.add(new TimestampedLwM2mNode(mostRecentNode.getTimestamp().minusMillis(2),
                LwM2mSingleResource.newStringResource(15, "Londres")));
        byte[] payload = encoder.encodeTimestampedData(timestampedNodes, contentFormat, new LwM2mPath("/3/0/15"),
                client.getObjectTree().getModel());
        Response firstCoapResponse = (Response) observeResponse.getCoapResponse();
        TestObserveUtil.sendNotification(
                client.getClientConnector(client.getServerIdForRegistrationId("/rd/" + currentRegistration.getId())),
                server.getEndpoint(Protocol.COAP).getURI(), payload, firstCoapResponse, contentFormat);
        // *** Hack End *** //

        // verify result
        server.waitForNewObservation(observation);
        ObserveResponse response = server.waitForNotificationOf(observation);
        assertThat(response.getContent()).isEqualTo(mostRecentNode.getNode());
        assertThat(response.getCoapResponse()).isInstanceOf(Response.class);
        assertEquals(timestampedNodes, response.getTimestampedLwM2mNode());
        assertContentFormat(contentFormat, response);
    }

    @TestAllCases
    public void can_observe_timestamped_instance(ContentFormat contentFormat, String givenServerEndpointProvider)
            throws InterruptedException {
        // observe device timezone
        ObserveResponse observeResponse = server.send(currentRegistration, new ObserveRequest(3, 0));
        assertEquals(ResponseCode.CONTENT, observeResponse.getCode());
        assertNotNull(observeResponse.getCoapResponse());
        assertThat(observeResponse.getCoapResponse(), is(instanceOf(Response.class)));

        // an observation response should have been sent
        SingleObservation observation = observeResponse.getObservation();
        assertEquals("/3/0", observation.getPath().toString());
        assertEquals(currentRegistration.getId(), observation.getRegistrationId());
        Set<Observation> observations = server.getObservationService().getObservations(currentRegistration);
        assertTrue(observations.size() == 1, "We should have only one observation");
        assertTrue(observations.contains(observation), "New observation is not there");

        // *** HACK send time-stamped notification as Leshan client does not support it *** //
        // create time-stamped nodes
        TimestampedLwM2mNode mostRecentNode = new TimestampedLwM2mNode(Instant.ofEpochMilli(System.currentTimeMillis()),
                new LwM2mObjectInstance(0, LwM2mSingleResource.newStringResource(15, "Paris")));
        List<TimestampedLwM2mNode> timestampedNodes = new ArrayList<>();
        timestampedNodes.add(mostRecentNode);
        timestampedNodes.add(new TimestampedLwM2mNode(mostRecentNode.getTimestamp().minusMillis(2),
                new LwM2mObjectInstance(0, LwM2mSingleResource.newStringResource(15, "Londres"))));
        byte[] payload = encoder.encodeTimestampedData(timestampedNodes, contentFormat, new LwM2mPath("/3/0"),
                client.getObjectTree().getModel());
        Response firstCoapResponse = (Response) observeResponse.getCoapResponse();
        TestObserveUtil.sendNotification(
                client.getClientConnector(client.getServerIdForRegistrationId("/rd/" + currentRegistration.getId())),
                server.getEndpoint(Protocol.COAP).getURI(), payload, firstCoapResponse, contentFormat);
        // *** Hack End *** //

        // verify result
        server.waitForNewObservation(observation);
        ObserveResponse response = server.waitForNotificationOf(observation);
        assertThat(response.getContent()).isEqualTo(mostRecentNode.getNode());
        assertThat(response.getCoapResponse()).isInstanceOf(Response.class);
        assertEquals(timestampedNodes, response.getTimestampedLwM2mNode());
        assertContentFormat(contentFormat, response);
    }

    @TestAllCases
    public void can_observe_timestamped_object(ContentFormat contentFormat, String givenServerEndpointProvider)
            throws InterruptedException {
        // observe device timezone
        ObserveResponse observeResponse = server.send(currentRegistration, new ObserveRequest(3));
        assertEquals(ResponseCode.CONTENT, observeResponse.getCode());
        assertNotNull(observeResponse.getCoapResponse());
        assertThat(observeResponse.getCoapResponse(), is(instanceOf(Response.class)));

        // an observation response should have been sent
        SingleObservation observation = observeResponse.getObservation();
        assertEquals("/3", observation.getPath().toString());
        assertEquals(currentRegistration.getId(), observation.getRegistrationId());
        Set<Observation> observations = server.getObservationService().getObservations(currentRegistration);
        assertTrue(observations.size() == 1, "We should have only one observation");
        assertTrue(observations.contains(observation), "New observation is not there");

        // *** HACK send time-stamped notification as Leshan client does not support it *** //
        // create time-stamped nodes
        TimestampedLwM2mNode mostRecentNode = new TimestampedLwM2mNode(Instant.ofEpochMilli(System.currentTimeMillis()),
                new LwM2mObject(3, new LwM2mObjectInstance(0, LwM2mSingleResource.newStringResource(15, "Paris"))));
        List<TimestampedLwM2mNode> timestampedNodes = new ArrayList<>();
        timestampedNodes.add(mostRecentNode);
        timestampedNodes.add(new TimestampedLwM2mNode(mostRecentNode.getTimestamp().minusMillis(2),
                new LwM2mObject(3, new LwM2mObjectInstance(0, LwM2mSingleResource.newStringResource(15, "Londres")))));
        byte[] payload = encoder.encodeTimestampedData(timestampedNodes, contentFormat, new LwM2mPath("/3"),
                client.getObjectTree().getModel());

        Response firstCoapResponse = (Response) observeResponse.getCoapResponse();
        TestObserveUtil.sendNotification(
                client.getClientConnector(client.getServerIdForRegistrationId("/rd/" + currentRegistration.getId())),
                server.getEndpoint(Protocol.COAP).getURI(), payload, firstCoapResponse, contentFormat);
        // *** Hack End *** //

        // verify result
        server.waitForNewObservation(observation);
        ObserveResponse response = server.waitForNotificationOf(observation);
        assertThat(response.getContent()).isEqualTo(mostRecentNode.getNode());
        assertThat(response.getCoapResponse()).isInstanceOf(Response.class);
        assertEquals(timestampedNodes, response.getTimestampedLwM2mNode());
        assertContentFormat(contentFormat, response);
    }
}
