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
import org.eclipse.jdt.annotation.Nullable;

/**
 * Structured result of Stage 1 intent entity slot extraction and classification.
 *
 * @param intent resolved {@link Intent}, or {@code null} if no intent could be determined
 * @param intentScore confidence score in {@code [0.0, 1.0]}
 * @param device extracted target device or sensor property phrase
 * @param location extracted room, floor, or zone phrase
 * @param value extracted numeric value, unit, option label, or color name
 * @param isAllScope {@code true} if the utterance includes an all/every/both quantifier
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public record ParsedUtterance(@Nullable Intent intent, float intentScore, @Nullable String device,
        @Nullable String location, @Nullable String value, boolean isAllScope) {
}
