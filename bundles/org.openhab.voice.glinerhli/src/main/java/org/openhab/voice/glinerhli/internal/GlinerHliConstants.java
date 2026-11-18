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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Constants used across the GLiNER Human Language Interpreter voice add-on.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public final class GlinerHliConstants {

    public static final String SERVICE_ID = "glinerhli";
    public static final String SERVICE_PID = "org.openhab.voice.glinerhli";
    public static final String SERVICE_CATEGORY = "voice";
    public static final String CONFIG_URI = SERVICE_CATEGORY + ":" + SERVICE_ID;
    public static final String SERVICE_LABEL = "GLiNER Semantic Interpreter";

    public static final String GLINER_SUBDIR = "gliner";
    public static final String EMBEDDING_SUBDIR = "embedding";

    private GlinerHliConstants() {
    }
}
