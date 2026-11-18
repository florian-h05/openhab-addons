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
package org.openhab.voice.glinerhli.internal.language;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.HSBType;
import org.openhab.voice.glinerhli.internal.Intent;

/**
 * Language-specific NLP rules for Stage 1 fallback intent/quantifier resolution, Stage 2 lexical matching, and Stage 3
 * value/color parsing.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public interface LanguageSupport {

    Locale locale();

    /**
     * Returns {@code true} if the extracted quantifier span denotes an all/every scope.
     */
    boolean isAllQuantifier(@Nullable String quantifier);

    /**
     * Returns {@code true} if the utterance is phrased as a question or explicit status request, as opposed to a
     * declarative statement such as "it is too dark in the kitchen".
     */
    boolean isQuestion(String utterance);

    /**
     * Returns {@code true} if the utterance asks for information, e.g. starts with a question word or a verb-first
     * question such as "is the window open". Polite requests such as "can you turn on the light?" do not.
     */
    boolean asksForState(String utterance);

    /**
     * Returns {@code true} if the single lowercase token is an action, query, or scope keyword rather than a word
     * naming a device or property.
     */
    boolean isActionWord(String token);

    /**
     * Returns {@code true} if the lowercase word is ignored during Stage 2 lexical matching.
     */
    boolean isQueryStopWord(String word);

    /**
     * Returns {@code true} if the {@code action_phrase} or full utterance contains a preposition indicating an
     * absolute target value (e.g. {@code to}).
     */
    boolean hasAbsoluteTargetPreposition(@Nullable String actionPhrase, String utterance);

    /**
     * Extracts the target value following an absolute target preposition in the utterance, or {@code null} if none.
     */
    @Nullable
    String extractAbsoluteTargetValue(String utterance);

    /**
     * Resolves an {@link Intent} deterministically from the extracted {@code action_phrase}, {@code command_value}, and
     * full utterance when GLiNER classification is disabled, below threshold, or not trusted.
     */
    @Nullable
    Intent resolveFallbackIntent(@Nullable String actionPhrase, @Nullable String commandValue, String utterance);

    /**
     * Normalizes localized decimal separators and spoken unit words into a form parseable by openHAB types.
     */
    String normalizeValue(String rawValue);

    /**
     * Parses a localized color name into an {@link HSBType}, or returns {@code null} if unknown.
     */
    @Nullable
    HSBType parseColor(String colorName);

    /**
     * Returns the alternative canonical/English phrases a lowercase word may refer to, each as a list of words (e.g.
     * "Wohnzimmer" refers to "living room", "Licht" to "light" or "lightbulb"), including the parts of compound nouns
     * such as "Kellerlicht".
     *
     * @param stateQuery whether property adjectives (e.g. "warm" for "temperature") apply: in commands or statements
     *            they describe a condition ("it is too warm") rather than name a target
     */
    Set<List<String>> synonyms(String word, boolean stateQuery);

    /**
     * Adds the words of all synonyms of the given words to them.
     */
    Set<String> expandSemanticTokens(Set<String> words);
}
