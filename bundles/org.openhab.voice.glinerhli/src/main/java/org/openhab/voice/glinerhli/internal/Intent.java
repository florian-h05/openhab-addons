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
package org.openhab.voice.glinerhli.internal;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Supported smart-home intents for Stage 1 classification and Stage 3 command mapping.
 * <p>
 * Constants are ordered by fallback resolution priority (lowest {@link #ordinal()} = highest priority).
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public enum Intent {
    QUERY_STATE("query_state"),
    SET_VALUE("set_value"),
    INCREASE("increase"),
    DECREASE("decrease"),
    TOGGLE("toggle"),
    STOP("stop"),
    PAUSE("pause"),
    PLAY("play"),
    NEXT("next"),
    PREVIOUS("previous"),
    OPEN("open"),
    CLOSE("close"),
    TURN_OFF("turn_off"),
    TURN_ON("turn_on");

    private final String label;

    Intent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static @Nullable Intent fromLabel(@Nullable String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        for (Intent intent : values()) {
            if (intent.label.equals(normalized)) {
                return intent;
            }
        }
        return null;
    }
}
