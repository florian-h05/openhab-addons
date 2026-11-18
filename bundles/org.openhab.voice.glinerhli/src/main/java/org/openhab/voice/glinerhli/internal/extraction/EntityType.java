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
package org.openhab.voice.glinerhli.internal.extraction;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Entity slot types extracted by GLiNER NER during Stage 1.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public enum EntityType {
    LOCATION("location"),
    DEVICE_OR_PROPERTY("device"),
    ACTION_PHRASE("action"),
    COMMAND_VALUE("value"),
    QUANTIFIER("quantifier");

    private final String label;

    EntityType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
