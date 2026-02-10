/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.bluelink.internal.api;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.openhab.binding.bluelink.internal.MockApiData.*;
import static org.openhab.core.library.unit.MetricPrefix.KILO;
import static org.openhab.core.library.unit.SIUnits.METRE;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import javax.measure.quantity.Length;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openhab.binding.bluelink.internal.dto.CommonVehicleStatus;
import org.openhab.binding.bluelink.internal.dto.eu.EuVehicle;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.QuantityType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class BluelinkApiEUTest {

    private static final WireMockServer WIREMOCK_SERVER = new WireMockServer(
            WireMockConfiguration.options().dynamicPort());
    private static final HttpClient HTTP_CLIENT = new HttpClient();

    @BeforeAll
    static void setUp() throws Exception {
        WIREMOCK_SERVER.start();
        WireMock.configureFor("localhost", WIREMOCK_SERVER.port());
        stubFor(post(urlEqualTo("/auth/api/v2/user/oauth2/token")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(TOKEN_RESPONSE)));
        stubFor(post(urlEqualTo("/api/v1/spa/notifications/register")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(EU_DEVICE_REGISTRATION_RESPONSE)));
        stubFor(get(urlEqualTo("/api/v1/spa/vehicles/1234/status/latest")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(EU_VEHICLE_STATUS_RESPONSE)));

        HTTP_CLIENT.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        WIREMOCK_SERVER.stop();
        HTTP_CLIENT.stop();
    }

    @Test
    void testGetVehicleStatus() throws Exception {
        final String baseUrl = "http://localhost:" + WIREMOCK_SERVER.port();

        final BluelinkApiEU api = new BluelinkApiEU(HTTP_CLIENT, BluelinkApiEU.Brand.HYUNDAI, baseUrl,
                TEST_REFRESH_TOKEN);
        assertTrue(api.login());

        final EuVehicle vehicle = new EuVehicle("1234", "IONIQ 5", "IONIQ 5", "VIN1234", "EV", true, null, null, false);

        final AtomicReference<@Nullable CommonVehicleStatus> statusRef = new AtomicReference<>();
        final AtomicReference<@Nullable QuantityType<Length>> odometerRef = new AtomicReference<>();
        final AtomicReference<@Nullable PointType> locationRef = new AtomicReference<>();

        boolean result = api.getVehicleStatus(vehicle, false, new VehicleStatusCallback() {
            @Override
            public void acceptStatus(CommonVehicleStatus data) {
                statusRef.set(data);
            }

            @Override
            public void acceptLastUpdateTimestamp(Instant lastUpdated) {
            }

            @Override
            public void acceptSmartKeyBatteryWarning(boolean smartKeyBattery) {
            }

            @Override
            public void acceptLocation(PointType location) {
                locationRef.set(location);
            }

            @Override
            public void acceptOdometer(QuantityType<Length> odometer) {
                odometerRef.set(odometer);
            }
        });

        assertTrue(result);
        final CommonVehicleStatus status = statusRef.get();
        assertNotNull(status);
        assertFalse(status.doorLock());
        assertFalse(status.engine());
        assertFalse(status.trunkOpen());
        assertTrue(status.hoodOpen());
        assertEquals(82, status.battery().stateOfCharge());

        final var evStatus = status.evStatus();
        assertNotNull(evStatus);
        assertEquals(35, evStatus.batteryStatus());
        assertFalse(evStatus.batteryCharge());
        assertEquals(0, evStatus.batteryPlugin()); // 0 is unplugged? or > 0 is plugged? In handler it's > 0. EU DTO has
                                                   // plugStatus.
        // In EU DTO EvStatus: plugStatus is int.
        // In Mock JSON: plugStatus: 0.
        // In handler: updateState(GROUP_CHARGING, CHANNEL_EV_PLUGGED_IN, OnOffType.from(evStatus.batteryPlugin() > 0));

        assertNotNull(evStatus.reservChargeInfos());
        assertEquals(90, evStatus.reservChargeInfos().targetSocList().stream().filter(t -> t.plugType() == 0) // DC
                .map(t -> t.targetSocLevel()).findFirst().orElseThrow());
        assertEquals(80, evStatus.reservChargeInfos().targetSocList().stream().filter(t -> t.plugType() == 1) // AC
                .map(t -> t.targetSocLevel()).findFirst().orElseThrow());

        // Range check?
        // In Mock JSON: drivingDistance -> rangeByFuel -> totalAvailableRange: 129.0 unit: 1. evModeRange: 129.0 unit:
        // 1.
        // DTO maps this.

        final var doorOpen = status.doorOpen();
        assertNotNull(doorOpen);
        assertEquals(1, doorOpen.frontLeft()); // 1 is Open?
        assertEquals(0, doorOpen.frontRight());
        assertEquals(0, doorOpen.backLeft());
        assertEquals(0, doorOpen.backRight());

        final var location = locationRef.get();
        assertNotNull(location);
        // PointType lat,lon,alt
        assertEquals(34.0, location.getAltitude().doubleValue());
        assertEquals(49.01395, location.getLatitude().doubleValue());
        assertEquals(8.40448, location.getLongitude().doubleValue());

        assertFalse(status.airCtrlOn());
        assertFalse(status.defrost());

        assertEquals(new QuantityType<>(39505.5, KILO(METRE)), odometerRef.get());
    }
}
