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
/**
 * Stage 1: Intent classification and entity slot extraction.
 * <p>
 * {@link GlinerIntentExtractor} runs GLiNER 2.5 ONNX Runtime inference ({@code encoder}, {@code boundary}, and optional
 * {@code classifier} sessions) to extract {@link EntityType} slots, detect all-scope quantifiers, and classify the
 * {@link org.openhab.voice.glinerhli.internal.Intent}. When running in NER-only mode or when classifier confidence is
 * below the configured threshold, deterministic keyword and stem rules resolve the intent from the extracted slots and
 * utterance, producing a {@link org.openhab.voice.glinerhli.internal.ParsedUtterance}.
 *
 * @author Florian Hotze - Initial contribution
 */
package org.openhab.voice.glinerhli.internal.extraction;
