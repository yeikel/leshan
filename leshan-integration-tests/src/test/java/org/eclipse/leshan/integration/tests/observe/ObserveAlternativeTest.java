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
package org.eclipse.leshan.integration.tests.observe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.leshan.integration.tests.util.Assertions.assertArg;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Set;

import org.eclipse.californium.core.coap.Response;
import org.eclipse.leshan.core.ResponseCode;
import org.eclipse.leshan.core.node.LwM2mSingleResource;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.observation.SingleObservation;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.integration.tests.util.IntegrationTestHelper;
import org.eclipse.leshan.server.observation.ObservationListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ObserveAlternativeTest {

    protected IntegrationTestHelper helper = new IntegrationTestHelper();

    @BeforeEach
    public void start() {
        helper.initialize();
        helper.createServer();
        helper.server.start();
        helper.createClient();
        helper.client.start();
        helper.waitForRegistrationAtServerSide(1);
    }

    @AfterEach
    public void stop() {
        helper.client.destroy(false);
        helper.server.destroy();
        helper.dispose();
    }

    @Test
    public void can_observe_resource() throws InterruptedException {
        TestObservationListener listener = new TestObservationListener();
        helper.server.getObservationService().addListener(listener);

        // observe device timezone
        ObserveResponse observeResponse = helper.server.send(helper.getCurrentRegistration(),
                new ObserveRequest(3, 0, 15));
        assertEquals(ResponseCode.CONTENT, observeResponse.getCode());
        assertNotNull(observeResponse.getCoapResponse());
        assertThat(observeResponse.getCoapResponse(), is(instanceOf(Response.class)));

        // an observation response should have been sent
        SingleObservation observation = observeResponse.getObservation();
        assertEquals("/3/0/15", observation.getPath().toString());
        assertEquals(helper.getCurrentRegistration().getId(), observation.getRegistrationId());
        Set<Observation> observations = helper.server.getObservationService()
                .getObservations(helper.getCurrentRegistration());
        assertEquals(1, observations.size(), "We should have only one observation");
        assertTrue(observations.contains(observation), "New observation is not there");

        // write device timezone
        LwM2mResponse writeResponse = helper.server.send(helper.getCurrentRegistration(),
                new WriteRequest(3, 0, 15, "Europe/Paris"));

        // verify result
        listener.waitForNotification(2000);
        assertEquals(ResponseCode.CHANGED, writeResponse.getCode());
        assertTrue(listener.receivedNotify().get());
        assertEquals(LwM2mSingleResource.newStringResource(15, "Europe/Pariss"),
                (listener.getObserveResponse()).getContent());
        assertNotNull(listener.getObserveResponse().getCoapResponse());
        assertThat(listener.getObserveResponse().getCoapResponse(), is(instanceOf(Response.class)));
    }

    @Test
    public void can_observe_resource_using_mockito_lambda() throws InterruptedException {
        // mock creation
        ObservationListener observeListener = mock(ObservationListener.class);
        helper.server.getObservationService().addListener(observeListener);

        // observe device timezone
        ObserveResponse observeResponse = helper.server.send(helper.getCurrentRegistration(),
                new ObserveRequest(3, 0, 15));
        assertEquals(ResponseCode.CONTENT, observeResponse.getCode());
        assertNotNull(observeResponse.getCoapResponse());
        assertThat(observeResponse.getCoapResponse(), is(instanceOf(Response.class)));

        // an observation response should have been sent
        SingleObservation observation = observeResponse.getObservation();
        assertEquals("/3/0/15", observation.getPath().toString());
        assertEquals(helper.getCurrentRegistration().getId(), observation.getRegistrationId());
        Set<Observation> observations = helper.server.getObservationService()
                .getObservations(helper.getCurrentRegistration());
        assertEquals(1, observations.size(), "We should have only one observation");
        assertTrue(observations.contains(observation), "New observation is not there");

        verify(observeListener, times(1)).newObservation(observation, helper.getCurrentRegistration());
        verifyNoMoreInteractions(observeListener);

        // write device timezone
        LwM2mResponse writeResponse = helper.server.send(helper.getCurrentRegistration(),
                new WriteRequest(3, 0, 15, "Europe/Paris"));

        // verify result
        verify(observeListener, timeout(200).times(1)). //
                onResponse(//
                        notNull(SingleObservation.class), //
                        notNull(), //
                        argThat(response -> {
                            return LwM2mSingleResource.newStringResource(15, "Europe/Pariss")
                                    .equals(response.getContent()) && response.getCoapResponse() != null
                                    && response.getCoapResponse() instanceof Response;
                        }));
        verifyNoMoreInteractions(observeListener);
        assertEquals(ResponseCode.CHANGED, writeResponse.getCode());
        assertEquals(ResponseCode.CHANGED, writeResponse.getCode());
    }

    @Test
    public void can_observe_resource_using_mockito_capture() throws InterruptedException {
        // mock creation
        ObservationListener observeListener = mock(ObservationListener.class);
        helper.server.getObservationService().addListener(observeListener);

        // observe device timezone
        ObserveResponse observeResponse = helper.server.send(helper.getCurrentRegistration(),
                new ObserveRequest(3, 0, 15));
        assertEquals(ResponseCode.CONTENT, observeResponse.getCode());
        assertNotNull(observeResponse.getCoapResponse());
        assertThat(observeResponse.getCoapResponse(), is(instanceOf(Response.class)));

        // an observation response should have been sent
        SingleObservation observation = observeResponse.getObservation();
        assertEquals("/3/0/15", observation.getPath().toString());
        assertEquals(helper.getCurrentRegistration().getId(), observation.getRegistrationId());
        Set<Observation> observations = helper.server.getObservationService()
                .getObservations(helper.getCurrentRegistration());
        assertEquals(1, observations.size(), "We should have only one observation");
        assertTrue(observations.contains(observation), "New observation is not there");

        verify(observeListener, times(1)).newObservation(observation, helper.getCurrentRegistration());
        verifyNoMoreInteractions(observeListener);

        // write device timezone
        LwM2mResponse writeResponse = helper.server.send(helper.getCurrentRegistration(),
                new WriteRequest(3, 0, 15, "Europe/Paris"));

        // verify result
        ArgumentCaptor<ObserveResponse> responseCaptor = ArgumentCaptor.forClass(ObserveResponse.class);
        verify(observeListener, timeout(200).times(1)).//
                onResponse(any(SingleObservation.class), any(), responseCaptor.capture());
        verifyNoMoreInteractions(observeListener);
        assertEquals(ResponseCode.CHANGED, writeResponse.getCode());
        assertEquals(LwM2mSingleResource.newStringResource(15, "Europe/Pariss"),
                responseCaptor.getValue().getContent());
        assertNotNull(responseCaptor.getValue().getCoapResponse());
        assertThat(responseCaptor.getValue().getCoapResponse(), is(instanceOf(Response.class)));
    }

    @Test
    public void can_observe_resource_using_mockito_assertThat() throws InterruptedException {
        // mock creation
        ObservationListener observeListener = mock(ObservationListener.class);
        helper.server.getObservationService().addListener(observeListener);

        // observe device timezone
        ObserveResponse observeResponse = helper.server.send(helper.getCurrentRegistration(),
                new ObserveRequest(3, 0, 15));
        assertEquals(ResponseCode.CONTENT, observeResponse.getCode());
        assertNotNull(observeResponse.getCoapResponse());
        assertThat(observeResponse.getCoapResponse(), is(instanceOf(Response.class)));

        // an observation response should have been sent
        SingleObservation observation = observeResponse.getObservation();
        assertEquals("/3/0/15", observation.getPath().toString());
        assertEquals(helper.getCurrentRegistration().getId(), observation.getRegistrationId());
        Set<Observation> observations = helper.server.getObservationService()
                .getObservations(helper.getCurrentRegistration());
        assertEquals(1, observations.size(), "We should have only one observation");
        assertTrue(observations.contains(observation), "New observation is not there");

        verify(observeListener, times(1)).newObservation(observation, helper.getCurrentRegistration());
        verifyNoMoreInteractions(observeListener);

        // write device timezone
        LwM2mResponse writeResponse = helper.server.send(helper.getCurrentRegistration(),
                new WriteRequest(3, 0, 15, "Europe/Paris"));

        // verify result
        verify(observeListener, timeout(2000).times(1)). //
                onResponse(//
                        notNull(SingleObservation.class), //
                        notNull(), //
                        assertArg(response -> {
                            assertThat(response.getContent(),
                                    is(LwM2mSingleResource.newStringResource(15, "Europe/Pariss")));
                            assertThat(response.getCoapResponse(), is(notNullValue()));
                            assertThat(response.getCoapResponse(), is(instanceOf(Response.class)));
                        }));
        assertEquals(ResponseCode.CHANGED, writeResponse.getCode());
    }

    @Test
    public void can_observe_resource_using_mockito_assertj() throws InterruptedException {
        // mock creation
        ObservationListener observeListener = mock(ObservationListener.class);
        helper.server.getObservationService().addListener(observeListener);

        // observe device timezone
        ObserveResponse observeResponse = helper.server.send(helper.getCurrentRegistration(),
                new ObserveRequest(3, 0, 15));
        assertThat(observeResponse.getCode()).isEqualTo(ResponseCode.CONTENT);
        assertThat(observeResponse.getCoapResponse()).isInstanceOf(Response.class);

        // an observation response should have been sent
        SingleObservation observation = observeResponse.getObservation();
        assertThat(observation.getPath().toString()).isEqualTo("/3/0/15");
        assertThat(observation.getRegistrationId()).isEqualTo(helper.getCurrentRegistration().getId());
        assertThat(helper.server.getObservationService().getObservations(helper.getCurrentRegistration()))
                .containsOnly(observation);

        verify(observeListener, times(1)).newObservation(observation, helper.getCurrentRegistration());
        verifyNoMoreInteractions(observeListener);

        // write device timezone
        LwM2mResponse writeResponse = helper.server.send(helper.getCurrentRegistration(),
                new WriteRequest(3, 0, 15, "Europe/Paris"));

        // verify result
        verify(observeListener, timeout(2000).times(1)). //
                onResponse(//
                        notNull(SingleObservation.class), //
                        notNull(), //
                        assertArg(response -> {
                            assertThat(response.getContent())
                                    .isEqualTo(LwM2mSingleResource.newStringResource(15, "Europe/Pariss"));
                            assertThat(response.getCoapResponse()).isInstanceOf(Response.class);
                        }));
        assertThat(writeResponse.getCode()).isEqualTo(ResponseCode.CHANGED);
    }

    @Test
    public void can_observe_resource_using_mockito_assertj_and_mock_annotation(
            @Mock ObservationListener observeListener) throws InterruptedException {

        helper.server.getObservationService().addListener(observeListener);

        // observe device timezone
        ObserveResponse observeResponse = helper.server.send(helper.getCurrentRegistration(),
                new ObserveRequest(3, 0, 15));
        assertThat(observeResponse.getCode()).isEqualTo(ResponseCode.CONTENT);
        assertThat(observeResponse.getCoapResponse()).isInstanceOf(Response.class);

        // an observation response should have been sent
        SingleObservation observation = observeResponse.getObservation();
        assertThat(observation.getPath().toString()).isEqualTo("/3/0/15");
        assertThat(observation.getRegistrationId()).isEqualTo(helper.getCurrentRegistration().getId());
        assertThat(helper.server.getObservationService().getObservations(helper.getCurrentRegistration()))
                .containsOnly(observation);

        verify(observeListener, times(1)).newObservation(observation, helper.getCurrentRegistration());
        verifyNoMoreInteractions(observeListener);

        // write device timezone
        LwM2mResponse writeResponse = helper.server.send(helper.getCurrentRegistration(),
                new WriteRequest(3, 0, 15, "Europe/Paris"));

        // verify result
        verify(observeListener, timeout(2000).times(1)). //
                onResponse(//
                        notNull(SingleObservation.class), //
                        notNull(), //
                        assertArg(response -> {
                            assertThat(response.getContent())
                                    .isEqualTo(LwM2mSingleResource.newStringResource(15, "Europe/Pariss"));
                            assertThat(response.getCoapResponse()).isInstanceOf(Response.class);
                        }));
        assertThat(writeResponse.getCode()).isEqualTo(ResponseCode.CHANGED);
    }
}
