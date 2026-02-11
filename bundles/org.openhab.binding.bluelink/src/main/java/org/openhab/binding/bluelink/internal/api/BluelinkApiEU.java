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

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.bluelink.internal.dto.BatteryStatus;
import org.openhab.binding.bluelink.internal.dto.CommonVehicleStatus;
import org.openhab.binding.bluelink.internal.dto.DoorStatus;
import org.openhab.binding.bluelink.internal.dto.DrivingRange;
import org.openhab.binding.bluelink.internal.dto.EvStatus;
import org.openhab.binding.bluelink.internal.dto.SeatHeaterState;
import org.openhab.binding.bluelink.internal.dto.TemperatureValue;
import org.openhab.binding.bluelink.internal.dto.TirePressureWarnings;
import org.openhab.binding.bluelink.internal.dto.eu.BaseResponse;
import org.openhab.binding.bluelink.internal.dto.eu.EuVehicle;
import org.openhab.binding.bluelink.internal.dto.eu.RegistrationRequest;
import org.openhab.binding.bluelink.internal.dto.eu.RegistrationResponse;
import org.openhab.binding.bluelink.internal.dto.eu.TokenResponse;
import org.openhab.binding.bluelink.internal.dto.eu.VehicleInfoEU;
import org.openhab.binding.bluelink.internal.dto.eu.VehicleStatusEU;
import org.openhab.binding.bluelink.internal.dto.eu.VehiclesResponse;
import org.openhab.binding.bluelink.internal.model.Brand;
import org.openhab.binding.bluelink.internal.model.IVehicle;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

/**
 * EU implementation of the Bluelink API.
 * <p>
 * Implementation based on
 * <a href="https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api">hyundai_kia_connect_api</a> and
 * <a href="https://github.com/Hacksore/bluelinky">bluelinky</a>.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class BluelinkApiEU extends AbstractBluelinkApi<EuVehicle> {
    private static final String HTTP_USER_AGENT = "okhttp/3.12.0";
    private static final int HTTP_TIMEOUT_SECONDS = 20;

    private final Logger logger = LoggerFactory.getLogger(BluelinkApiEU.class);
    private final BrandConfig brandConfig;
    private final String refreshToken;

    private @Nullable TokenResponse token;
    private @Nullable Instant tokenExpiry;
    private @Nullable UUID deviceId;

    public enum Brand {
        HYUNDAI,
        KIA,
        GENESIS
    }

    public BluelinkApiEU(final HttpClient httpClient, final Map<String, String> properties, final Brand brand,
            final String refreshToken) {
        // AbstractBluelinkApi requires username/password. We pass empty username and refreshToken as password.
        // We pass default TimeZoneProvider which we don't use much here.
        super(httpClient, () -> ZoneId.systemDefault(), "", refreshToken, null);
        String deviceId = properties.get("deviceId");
        if (deviceId != null && !deviceId.isBlank()) {
            this.deviceId = UUID.fromString(deviceId);
        }
        this.brandConfig = BrandConfig.forBrand(brand);
        this.refreshToken = refreshToken;
    }

    // For tests
    public BluelinkApiEU(final HttpClient httpClient, final Brand brand, final String baseUrl,
            final String refreshToken) {
        super(httpClient, () -> ZoneId.systemDefault(), "", refreshToken, null);
        final BrandConfig hyundaiBrandConfig = BrandConfig.forBrand(brand);
        this.brandConfig = new BrandConfig(baseUrl, baseUrl, hyundaiBrandConfig.ccspServiceId, hyundaiBrandConfig.appId,
                hyundaiBrandConfig.clientSecret, hyundaiBrandConfig.cfb, hyundaiBrandConfig.pushType);
        this.refreshToken = refreshToken;
    }

    @Override
    public Map<String, String> getProperties() {
        UUID deviceId = this.deviceId;
        if (deviceId != null) {
            return Map.of("deviceId", deviceId.toString());
        }
        return Collections.emptyMap();
    }

    @Override
    public boolean login() throws BluelinkApiException {
        authenticate();
        UUID deviceId = this.deviceId;
        if (deviceId == null) {
            registerDevice();
        } else {
            logger.debug("Device already registered, deviceId: {}", deviceId);
        }
        return true;
    }

    private void authenticate() throws BluelinkApiException {
        String url = brandConfig.loginBaseUrl + "/auth/api/v2/user/oauth2/token";
        String formBody = "grant_type=refresh_token" + "&refresh_token=" + this.refreshToken + "&client_id="
                + brandConfig.ccspServiceId + "&client_secret=" + brandConfig.clientSecret;

        Request request = httpClient.newRequest(url).method(HttpMethod.POST)
                .header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded").agent(HTTP_USER_AGENT)
                .content(new StringContentProvider(formBody));

        try {
            ContentResponse response = request.send();

            if (response.getStatus() != HttpStatus.OK_200) {
                logger.debug("Login failed with status {}: {}", response.getStatus(), response.getContentAsString());
                if (response.getStatus() == HttpStatus.BAD_REQUEST_400) {
                    throw new BluelinkApiException("Login failed: Invalid refresh token");
                }
                throw new BluelinkApiException("Login failed with status " + response.getStatus());
            }

            TokenResponse tokenResponse = gson.fromJson(response.getContentAsString(), TokenResponse.class);
            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                throw new BluelinkApiException("Login failed: Invalid response");
            }

            this.token = tokenResponse;
            String expires = tokenResponse.expiresIn();
            this.tokenExpiry = Instant.now().plusSeconds(Long.parseLong(expires != null ? expires : "3600") - 60);
            logger.debug("Login successful, token valid until {}", tokenExpiry);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BluelinkApiException("Login interrupted", e);
        } catch (TimeoutException | ExecutionException e) {
            throw new BluelinkApiException("Login failed", e);
        }
    }

    @Override
    protected boolean isAuthenticated() {
        final TokenResponse token = this.token;
        final Instant expiry = tokenExpiry;
        return token != null && expiry != null && Instant.now().isBefore(expiry);
    }

    private void registerDevice() throws BluelinkApiException {
        ensureAuthenticated();
        String url = brandConfig.apiBaseUrl + "/api/v1/spa/notifications/register";

        RegistrationRequest payload = new RegistrationRequest(
                String.format("%064x", ThreadLocalRandom.current().nextLong()), brandConfig.pushType,
                UUID.randomUUID());

        Request request = httpClient.newRequest(url).method(HttpMethod.POST)
                .timeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .content(new StringContentProvider(gson.toJson(payload)), "application/json;charset=UTF-8");
        addStandardHeaders(request);
        addAuthHeaders(request);

        ContentResponse response;
        try {
            response = request.send();
            if (response.getStatus() != HttpStatus.OK_200) {
                logger.debug("Device registration failed with status {}: {}", response.getStatus(),
                        response.getContentAsString());
                throw new BluelinkApiException("Device registration failed with status " + response.getStatus());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BluelinkApiException("Device registration interrupted", e);
        } catch (final TimeoutException | ExecutionException e) {
            throw new BluelinkApiException("Device registration failed", e);
        }

        Type type = new TypeToken<BaseResponse<RegistrationResponse>>() {
        }.getType();
        BaseResponse<RegistrationResponse> registration = gson.fromJson(response.getContentAsString(), type);
        if (registration != null) {
            this.deviceId = registration.resultMessage().deviceId();
        }
        logger.debug("Device registration successful, deviceId '{}'", deviceId);
    }

    @Override
    public List<EuVehicle> getVehicles() throws BluelinkApiException {
        ensureAuthenticated();
        String url = brandConfig.apiBaseUrl + "/api/v1/spa/vehicles";

        Request request = httpClient.newRequest(url).method(HttpMethod.GET).timeout(HTTP_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
        addStandardHeaders(request);
        addAuthHeaders(request);

        ContentResponse response = doRequest(request);

        Type type = new TypeToken<BaseResponse<VehiclesResponse>>() {
        }.getType();
        BaseResponse<VehiclesResponse> vehicles = gson.fromJson(response.getContentAsString(), type);
        if (vehicles == null) {
            return Collections.emptyList();
        }

        return vehicles.resultMessage().vehicles().stream().map(VehicleInfoEU::mapToVehicle).toList();
    }

    @Override
    public boolean getVehicleStatus(IVehicle vehicle, boolean forceRefresh, VehicleStatusCallback cb)
            throws BluelinkApiException {
        if (!(vehicle instanceof EuVehicle)) {
            throw new IllegalArgumentException("Expected EuVehicle");
        }
        EuVehicle euVehicle = (EuVehicle) vehicle;

        ensureAuthenticated();

        String url = brandConfig.apiBaseUrl + "/api/v1/spa/vehicles/" + euVehicle.registrationId();

        if (forceRefresh) {
            url += "/status";
        } else {
            url += Boolean.TRUE.equals(euVehicle.ccs2ProtocolSupport()) ? "/ccs2/carstatus/latest" : "/status/latest";
        }

        Request request = httpClient.newRequest(url).method(HttpMethod.GET).timeout(HTTP_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
        addStandardHeaders(request);
        addAuthHeaders(request);

        ContentResponse response = doRequest(request);

        logger.debug("Vehicle status request successful for vehicle {}: {}", euVehicle.getDisplayName(),
                response.getContentAsString());

        if (Boolean.TRUE.equals(euVehicle.ccs2ProtocolSupport())) {
            throw new BluelinkApiException(
                    "CCU/CCS2 protocol support hasn't been implemented yet. Report this on GitHub and provide debug logs.");
        }

        VehicleStatusEU statusEU = null;
        if (forceRefresh) {
            Type type = new TypeToken<BaseResponse<VehicleStatusEU.VehicleStatusData>>() {
            }.getType();
            BaseResponse<VehicleStatusEU.VehicleStatusData> forceRefreshResponse = gson
                    .fromJson(response.getContentAsString(), type);
            if (forceRefreshResponse != null) {
                statusEU = new VehicleStatusEU(
                        new VehicleStatusEU.VehicleStatusInfo(null, forceRefreshResponse.resultMessage(), null));
            }
        } else {
            Type type = new TypeToken<BaseResponse<VehicleStatusEU>>() {
            }.getType();
            BaseResponse<VehicleStatusEU> refreshResponse = gson.fromJson(response.getContentAsString(), type);
            if (refreshResponse != null) {
                statusEU = refreshResponse.resultMessage();
            }
        }

        if (statusEU == null) {
            return false;
        }

        cb.acceptStatus(new EuVehicleStatusAdapter(statusEU));

        // Other callbacks
        if (statusEU.info().status().dateTime() != null) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(statusEU.info().status().dateTime(),
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                cb.acceptLastUpdateTimestamp(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException e) {
                logger.warn("unexpected lastUpdate format: {}", statusEU.info().status().dateTime());
            }
        }
        cb.acceptSmartKeyBatteryWarning(statusEU.info().status().smartKeyBatteryWarning());
        if (statusEU.info().location() != null && statusEU.info().location().coord() != null) {
            var coord = statusEU.info().location().coord();
            cb.acceptLocation(new PointType(new org.openhab.core.library.types.DecimalType(coord.lat()),
                    new org.openhab.core.library.types.DecimalType(coord.lon()),
                    new org.openhab.core.library.types.DecimalType(coord.alt())));
        }
        // odometer is in VehicleStatusInfo directly
        if (statusEU.info().odometer() != null) {
            cb.acceptOdometer(new QuantityType<>(statusEU.info().odometer().value(), SIUnits.METRE.multiply(1000)));
        }

        return true;
    }

    // Control methods (Not Implemented)
    @Override
    public boolean lockVehicle(IVehicle vehicle) throws BluelinkApiException {
        throw new BluelinkApiException("Not implemented for EU");
    }

    @Override
    public boolean unlockVehicle(IVehicle vehicle) throws BluelinkApiException {
        throw new BluelinkApiException("Not implemented for EU");
    }

    @Override
    public boolean climateStart(IVehicle vehicle, QuantityType<Temperature> temperature, boolean heat, boolean defrost,
            @Nullable Integer igniOnDuration) throws BluelinkApiException {
        throw new BluelinkApiException("Not implemented for EU");
    }

    @Override
    public boolean climateStop(IVehicle vehicle) throws BluelinkApiException {
        throw new BluelinkApiException("Not implemented for EU");
    }

    @Override
    public boolean startCharging(IVehicle vehicle) throws BluelinkApiException {
        throw new BluelinkApiException("Not implemented for EU");
    }

    @Override
    public boolean stopCharging(IVehicle vehicle) throws BluelinkApiException {
        throw new BluelinkApiException("Not implemented for EU");
    }

    @Override
    public boolean setChargeLimitDC(IVehicle vehicle, int limit) throws BluelinkApiException {
        throw new BluelinkApiException("Not implemented for EU");
    }

    @Override
    public boolean setChargeLimitAC(IVehicle vehicle, int limit) throws BluelinkApiException {
        throw new BluelinkApiException("Not implemented for EU");
    }

    @Override
    public void addStandardHeaders(Request request) {
        String stamp = generateStamp();

        request.header("ccsp-service-id", brandConfig.ccspServiceId).header("ccsp-application-id", brandConfig.appId)
                .header("Stamp", stamp).header(HttpHeader.USER_AGENT, HTTP_USER_AGENT);
    }

    private void addAuthHeaders(final Request request) {
        TokenResponse token = this.token;
        if (token != null) {
            request.header(HttpHeader.AUTHORIZATION, token.tokenType() + " " + token.accessToken());
        }
        UUID deviceId = this.deviceId;
        if (deviceId != null) {
            request.header("ccsp-device-id", deviceId.toString());
        }
    }

    private ContentResponse doRequest(final Request request) throws BluelinkApiException {
        try {
            final ContentResponse response = request.send();
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                return response;
            } else {
                final String msg = "API request failed with status " + response.getStatus();
                if (response.getStatus() == 400) {
                    BaseResponse<?> response1 = gson.fromJson(response.getContentAsString(), BaseResponse.class);
                    if (response1 != null && "4004".equals(response1.responseCode())) {
                        throw new RetryableRequestException(msg);
                    }
                }
                logger.debug("API request failed with status {}: {}", response.getStatus(),
                        response.getContentAsString());
                throw new BluelinkApiException(msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BluelinkApiException("API request interrupted", e);
        } catch (final TimeoutException | ExecutionException e) {
            throw new BluelinkApiException("API request failed", e);
        }
    }

    private String generateStamp() {
        long timestamp = Instant.now().getEpochSecond();
        String rawData = brandConfig.appId + ":" + timestamp;
        byte[] rawBytes = rawData.getBytes(StandardCharsets.UTF_8);
        byte[] cfbBytes = Base64.getDecoder().decode(brandConfig.cfb);

        int length = Math.min(rawBytes.length, cfbBytes.length);
        byte[] result = new byte[length];

        for (int i = 0; i < length; i++) {
            result[i] = (byte) (cfbBytes[i] ^ rawBytes[i]);
        }

        return Base64.getEncoder().encodeToString(result);
    }

    public static class BrandConfig {
        final String apiBaseUrl;
        final String loginBaseUrl;
        final String ccspServiceId;
        final String appId;
        final String clientSecret;
        final String cfb;
        final String pushType;

        public BrandConfig(String apiBaseUrl, String loginBaseUrl, String ccspServiceId, String appId,
                String clientSecret, String cfb, String pushType) {
            this.apiBaseUrl = apiBaseUrl;
            this.loginBaseUrl = loginBaseUrl;
            this.ccspServiceId = ccspServiceId;
            this.appId = appId;
            this.clientSecret = clientSecret;
            this.cfb = cfb;
            this.pushType = pushType;
        }

        static BrandConfig forBrand(Brand brand) {
            return switch (brand) {
                case HYUNDAI -> new BrandConfig("https://prd.eu-ccapi.hyundai.com:8080",
                        "https://idpconnect-eu.hyundai.com", "6d477c38-3ca4-4cf3-9557-2a1929a94654",
                        "014d2225-8495-4735-812d-2616334fd15d", "KUy49XxPzLpLuoK0xhBC77W6VXhmtQR9iQhmIFjjoY4IpxsV",
                        "RFtoRq/vDXJmRndoZaZQyfOot7OrIqGVFj96iY2WL3yyH5Z/pUvlUhqmCxD2t+D65SQ=", "GCM");
                case KIA -> new BrandConfig("https://prd.eu-ccapi.kia.com:8080", "https://idpconnect-eu.kia.com",
                        "fdc85c00-0a2f-4c64-bcb4-2cfb1500730a", "a2b8469b-30a3-4361-8e13-6fceea8fbe74", "secret",
                        "wLTVxwidmH8CfJYBWSnHD6E0huk0ozdiuygB4hLkM5XCgzAL1Dk5sE36d/bx5PFMbZs=", "APNS");
                case GENESIS ->
                    new BrandConfig("https://prd-eu-ccapi.genesis.com:8080", "https://idpconnect-eu.genesis.com",
                            "3020afa2-30ff-412a-aa51-d28fbe901e10", "f11f2b86-e0e7-4851-90df-5600b01d8b70", "secret",
                            "RFtoRq/vDXJmRndoZaZQyYo3/qFLtVReW8P7utRPcc0ZxOzOELm9mexvviBk/qqIp4A=", "GCM");
            };
        }
    }

    // Adapter class for CommonVehicleStatus
    private static class EuVehicleStatusAdapter implements CommonVehicleStatus {
        private final VehicleStatusEU.VehicleStatusData data;

        public EuVehicleStatusAdapter(VehicleStatusEU eu) {
            this.data = eu.info().status();
        }

        @Override
        public boolean engine() {
            return data.engine();
        }

        @Override
        public boolean doorLock() {
            return data.doorLock();
        }

        @Override
        public DoorStatus doorOpen() {
            var d = data.doorOpen();
            return d != null ? new DoorStatus(d.frontLeft(), d.frontRight(), d.backLeft(), d.backRight())
                    : new DoorStatus(0, 0, 0, 0);
        }

        @Override
        public DoorStatus windowOpen() {
            var w = data.windowOpen();
            return w != null ? new DoorStatus(w.frontLeft(), w.frontRight(), w.backLeft(), w.backRight())
                    : new DoorStatus(0, 0, 0, 0);
        }

        @Override
        public boolean trunkOpen() {
            return data.trunkOpen();
        }

        @Override
        public boolean hoodOpen() {
            return data.hoodOpen();
        }

        @Override
        public boolean airCtrlOn() {
            return data.airCtrlOn();
        }

        @Override
        public TemperatureValue airTemp() {
            var at = data.airTemp();
            return new TemperatureValue() {
                @Override
                public String value() {
                    return at != null ? at.value() : "UnDef";
                }

                @Override
                public int unit() {
                    return at != null ? at.unit() : 0;
                }

                @Override
                public State getTemperature(@NonNull IVehicle vehicle) {
                    return at != null ? at.getTemperature() : UnDefType.UNDEF;
                }
            };
        }

        @Override
        public boolean defrost() {
            return data.defrost();
        }

        @Override
        public int steerWheelHeat() {
            return data.steerWheelHeat();
        }

        @Override
        public int sideBackWindowHeat() {
            return data.sideBackWindowHeat();
        }

        @Override
        public int sideMirrorHeat() {
            return 0;
        } // Missing in EU DTO

        @Override
        public SeatHeaterState seatHeaterVentState() {
            var s = data.seatHeaterVentState();
            return s == null ? new SeatHeaterState(0, 0, 0, 0)
                    : new SeatHeaterState(s.frontLeft(), s.frontRight(), s.rearLeft(), s.rearRight());
        }

        @Override
        public BatteryStatus battery() {
            var b = data.battery12V();
            return b == null ? new BatteryStatus(0) : new BatteryStatus(b.stateOfCharge());
        }

        @Override
        public EvStatus evStatus() {
            var e = data.evStatus();
            if (e == null)
                return new EvStatus(false, 0, 0, null, null, null);

            // Map ReserveChargeInfos
            EvStatus.ReserveChargeInfo reserve = null;
            if (e.reserveChargeInfos() != null && e.reserveChargeInfos().targetSocList() != null) {
                List<EvStatus.ReserveChargeInfo.TargetSOC> targets = e.reserveChargeInfos().targetSocList().stream()
                        .map(t -> new EvStatus.ReserveChargeInfo.TargetSOC(t.plugType(), t.targetSocLevel())).toList();
                reserve = new EvStatus.ReserveChargeInfo(targets);
            }

            // Map DrivingDistance
            List<EvStatus.DrivingDistance> distance = null;
            if (e.drivingDistance() != null) {
                distance = e.drivingDistance().stream().map(d -> {
                    var rbf = d.rangeByFuel();
                    return new EvStatus.DrivingDistance(
                            new EvStatus.DrivingDistance.RangeByFuel(mapRange(rbf.totalAvailableRange()),
                                    mapRange(rbf.evModeRange()), mapRange(rbf.gasModeRange())));
                }).toList();
            }

            // Map ChargeRemainingTime
            EvStatus.ChargeRemainingTime time = null;
            if (e.remainTime() != null) {
                var rt = e.remainTime();
                time = new EvStatus.ChargeRemainingTime(mapTime(rt.current()), mapTime(rt.fast()),
                        mapTime(rt.portable()), mapTime(rt.station()));
            }

            return new EvStatus(e.isCharging(), e.batteryPercentage(), e.plugStatus(), reserve, distance, time);
        }

        private DrivingRange mapRange(VehicleStatusEU.RangeValue rv) {
            if (rv == null)
                return null;
            return new DrivingRange((int) rv.value(), rv.unit());
        }

        private EvStatus.ChargeRemainingTime.TimeValue mapTime(VehicleStatusEU.ValueUnit vu) {
            if (vu == null)
                return null;
            return new EvStatus.ChargeRemainingTime.TimeValue((int) vu.value(), vu.unit());
        }

        @Override
        public DrivingRange dte() {
            return mapRange(data.dte());
        }

        @Override
        public int fuelLevel() {
            return data.fuelLevel() != null ? data.fuelLevel() : 0;
        }

        @Override
        public boolean lowFuelLight() {
            return Boolean.TRUE.equals(data.lowFuelLevel());
        }

        @Override
        public boolean washerFluidStatus() {
            return data.washerFluidStatus();
        }

        @Override
        public boolean brakeOilStatus() {
            return data.brakeOilStatus();
        }

        @Override
        public TirePressureWarnings tirePressureWarning() {
            return data.tirePressure();
        }
    }
}
