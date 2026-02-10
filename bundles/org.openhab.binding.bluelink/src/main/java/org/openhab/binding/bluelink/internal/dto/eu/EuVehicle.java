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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.bluelink.internal.model.IVehicle;

/**
 * Abstracts from the region-specific vehicle information representations.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public record EuVehicle(
        // generic
        String registrationId, String name, String model, @NonNull String vin, String rawEngineType, boolean electric,
        // US only:
        @Nullable Integer generation, @Nullable Double odometer,
        // EU only:
        @Nullable Boolean ccs2ProtocolSupport) implements IVehicle {

    @Override
    public @Nullable String id() {
        return registrationId;
    }

    @Override
    public @Nullable String nickName() {
        return name;
    }

    @Override
    public EngineType engineType() {
        if ("EV".equalsIgnoreCase(rawEngineType)) {
            return EngineType.EV;
        }
        if ("PHEV".equalsIgnoreCase(rawEngineType)) {
            return EngineType.PHEV;
        }
        if ("ICE".equalsIgnoreCase(rawEngineType) || "FUEL".equalsIgnoreCase(rawEngineType)) {
            return EngineType.ICE;
        }
        return electric ? EngineType.EV : EngineType.ICE;
    }

    @Override
    public int modelYear() {
        return generation != null ? generation.intValue() : 0;
    }
}
