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

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.voice.glinerhli.internal.ParsedUtterance;

/**
 * Stage 1 intent and entity slot extraction contract, implemented by {@link GlinerIntentExtractor}
 * and stubbed in deterministic end-to-end tests.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public interface IntentExtractor extends AutoCloseable {

    /**
     * Parses a user utterance into intent and slot entities.
     *
     * @param locale active user locale
     * @param text raw user utterance
     * @param confidenceThreshold minimum classifier score before falling back to deterministic keyword matching
     * @return extracted {@link ParsedUtterance}
     */
    ParsedUtterance parse(Locale locale, String text, float confidenceThreshold);

    @Override
    void close();
}
