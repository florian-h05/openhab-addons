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
package org.openhab.binding.bluelink.internal.dto.eu;

import static org.openhab.core.library.unit.SIUnits.CELSIUS;

import java.util.ArrayList;
import java.util.List;

import org.openhab.binding.bluelink.internal.dto.BatteryStatus;
import org.openhab.binding.bluelink.internal.dto.DoorStatus;
import org.openhab.binding.bluelink.internal.dto.DrivingRange;
import org.openhab.binding.bluelink.internal.dto.SeatHeaterState;
import org.openhab.binding.bluelink.internal.dto.TirePressureWarnings;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

import com.google.gson.annotations.SerializedName;

/**
 * Vehicle status response from the EU API.
 *
 * @author Florian Hotze - Initial contribution
 */
public record VehicleStatusEU(@SerializedName("vehicleStatusInfo") VehicleStatusInfo info) {
    public record VehicleStatusInfo(@SerializedName("vehicleLocation") VehicleLocation location,
            @SerializedName("vehicleStatus") VehicleStatusData status,
            @SerializedName("odometer") DrivingRange odometer) {
    }

    public record VehicleStatusData(@SerializedName("time") String dateTime,
            @SerializedName("airControlOn") boolean airCtrlOn, @SerializedName("engine") boolean engine,
            @SerializedName("doorLock") boolean doorLock, @SerializedName("doorOpen") DoorStatus doorOpen,
            @SerializedName("windowOpen") DoorStatus windowOpen, @SerializedName("trunkOpen") boolean trunkOpen,
            @SerializedName("hoodOpen") boolean hoodOpen, @SerializedName("airTemp") AirTemperature airTemp,
            @SerializedName("defrost") boolean defrost, @SerializedName("evStatus") EvStatus evStatus,
            @SerializedName("dte") DrivingRange dte, @SerializedName("steerWheelHeat") int steerWheelHeat,
            @SerializedName("sideBackWindowHeat") int sideBackWindowHeat,
            @SerializedName("seatHeaterVentState") SeatHeaterState seatHeaterVentState,
            @SerializedName("tirePressureLamp") TirePressureWarning tirePressure,
            @SerializedName("battery") BatteryStatus battery12V,
            @SerializedName("washerFluidStatus") boolean washerFluidStatus,
            @SerializedName("fuelLevel") Integer fuelLevel, @SerializedName("lowFuelLevel") Boolean lowFuelLevel,
            @SerializedName("breakOilStatus") boolean brakeOilStatus,
            @SerializedName("smartKeyBatteryWarning") boolean smartKeyBatteryWarning) {
    }

    public record VehicleLocation(@SerializedName("coord") Coordinates coord, @SerializedName("head") int heading,
            @SerializedName("speed") ValueUnit speed, @SerializedName("time") String time) {
    }

    public record Coordinates(double lat, double lon, double alt) {
    }

    public record AirTemperature(String value, int unit, int hvacTempType) {

        private static final double[] TEMPERATURE_RANGE;

        static {
            List<Double> range = new ArrayList<>();
            for (double i = 14; i <= 30; i += 0.5) {
                range.add(i);
            }
            TEMPERATURE_RANGE = range.stream().mapToDouble(Double::doubleValue).toArray();
        }

        public State getTemperature() {
            if (value == null || value.isEmpty()) {
                return UnDefType.UNDEF;
            }

            String cleanHex = value.replace("H", "").trim();

            try {
                int tempIndex = Integer.parseInt(cleanHex, 16);

                if (tempIndex >= 0 && tempIndex < TEMPERATURE_RANGE.length) {
                    return new QuantityType<>(TEMPERATURE_RANGE[tempIndex], CELSIUS);
                } else {
                    return UnDefType.UNDEF;
                }
            } catch (NumberFormatException e) {
                return UnDefType.UNDEF;
            }
        }
    }

    /**
     * High Voltage EV Battery and Charging
     */
    public record EvStatus(@SerializedName("batteryCharge") boolean isCharging,
            @SerializedName("batteryStatus") int batteryPercentage, @SerializedName("batteryPlugin") int plugStatus,
            @SerializedName("remainTime2") ChargeRemainingTime remainTime,
            @SerializedName("drvDistance") List<DrivingDistance> drivingDistance,
            @SerializedName("reservChargeInfos") ReserveChargeInfos reserveChargeInfos) {
    }

    public record ReserveChargeInfos(@SerializedName("targetSOClist") List<TargetSOC> targetSocList) {
    }

    public record TargetSOC(@SerializedName("targetSOClevel") int targetSocLevel,
            @SerializedName("plugType") int plugType, @SerializedName("dte") DrivingRange dte) {
    }

    public record TirePressureWarning(@SerializedName("tirePressureLampAll") int all,
            @SerializedName("tirePressureLampFL") int frontLeft, @SerializedName("tirePressureLampFR") int frontRight,
            @SerializedName("tirePressureLampRL") int rearLeft,
            @SerializedName("tirePressureLampRR") int rearRight) implements TirePressureWarnings {
    }

    public record ValueUnit(double value, int unit) {
    }

    public record ChargeRemainingTime(@SerializedName("atc") ValueUnit current, @SerializedName("etc1") ValueUnit fast,
            @SerializedName("etc2") ValueUnit portable, @SerializedName("etc3") ValueUnit station) {
    }

    public record DrivingDistance(@SerializedName("rangeByFuel") RangeByFuel rangeByFuel) {
    }

    public record RangeByFuel(DrivingRange totalAvailableRange, DrivingRange evModeRange, DrivingRange gasModeRange) {
    }
}
