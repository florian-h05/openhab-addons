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

import static java.util.Map.entry;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.HSBType;
import org.openhab.voice.glinerhli.internal.Intent;

/**
 * English {@link LanguageSupport} implementation.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public final class EnglishLanguageSupport extends AbstractPatternLanguageSupport {

    /**
     * Function words, which neither name a target nor make a state query about something else.
     */
    private static final Set<String> STOP_WORDS = Set.of("the", "an", "in", "on", "of", "at", "to", "for", "from", "is",
            "are", "was", "were", "be", "been", "do", "does", "did", "has", "have", "can", "could", "would", "will",
            "what", "which", "who", "when", "where", "how", "much", "many", "high", "level", "value", "there", "here",
            "this", "that", "these", "those", "turn", "switch", "set", "dim", "stop", "toggle", "all", "every", "both",
            "state", "status", "playing", "current", "currently", "right", "now", "still", "today", "it", "its", "my",
            "our", "me", "you", "your", "please", "tell", "show", "give", "get");

    private static final Map<String, HSBType> COLORS = Map.ofEntries( //
            Map.entry("red", HSBType.RED), //
            Map.entry("green", HSBType.GREEN), //
            Map.entry("blue", HSBType.BLUE), //
            Map.entry("white", HSBType.WHITE), //
            Map.entry("warm white", new HSBType("36,35,100")), //
            Map.entry("cool white", new HSBType("210,15,100")), //
            Map.entry("daylight", new HSBType("50,5,100")), //
            Map.entry("yellow", HSBType.fromRGB(255, 224, 32)), //
            Map.entry("orange", HSBType.fromRGB(255, 160, 16)), //
            Map.entry("purple", HSBType.fromRGB(128, 0, 128)), //
            Map.entry("pink", HSBType.fromRGB(255, 96, 208)), //
            Map.entry("cyan", HSBType.fromRGB(0, 255, 255)));

    public EnglishLanguageSupport() {
        super(Locale.ENGLISH, Map.ofEntries( //
                entry(Intent.QUERY_STATE, keywords("status|what|how|check|how\\s+much")), //
                entry(Intent.SET_VALUE, keywords("set|change")), //
                entry(Intent.INCREASE,
                        keywords("increase|brighter|brighten|warmer|louder|turn\\s+up|raise\\s+temp\\w*")), //
                entry(Intent.DECREASE, keywords("dim\\w*|lower|cooler|darker|quieter|decrease|turn\\s+down")), //
                entry(Intent.TOGGLE, keywords("toggl\\w*")), //
                entry(Intent.STOP, keywords("stopp?\\w*|halt")), //
                entry(Intent.PAUSE, keywords("paus\\w*")), //
                entry(Intent.PLAY, keywords("play|resum\\w*")), //
                entry(Intent.NEXT, keywords("next|skip\\w*")), //
                entry(Intent.PREVIOUS, keywords("prev\\w*")), //
                entry(Intent.OPEN, keywords("open\\w*|raise")), //
                entry(Intent.CLOSE, keywords("clos\\w*|shut")), //
                entry(Intent.TURN_OFF, keywords("off|turn\\s+off|switch\\s+off")), //
                entry(Intent.TURN_ON, keywords("on|start\\w*|turn\\s+on|switch\\s+on"))), //
                keywords("all|every|both"), //
                Pattern.compile(
                        "(?iu)^\\s*(is|are|was|were|does|do|did|has|have|which|who|where|when|why|tell\\s+me|show\\s+me)\\b"), //
                keywords("to"), //
                keywords("brighter|warmer|louder|cooler|darker|quieter|by"), //
                COLORS);
    }

    @Override
    public String normalizeValue(String rawValue) {
        String normalized = rawValue.trim();
        normalized = normalized.replaceAll("(?iu)\\bpercent\\b", "%");
        normalized = normalized.replaceAll("(?iu)\\bdegrees?\\s+celsius\\b", "°C");
        normalized = normalized.replaceAll("(?iu)\\bdegrees?\\s+fahrenheit\\b", "°F");
        // A bare "degrees" does not name the scale, so the value is interpreted in the item's unit (°C or °F)
        normalized = normalized.replaceAll("(?iu)\\bdegrees?\\b", "");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private static final Map<String, Set<String>> SEMANTIC_SYNONYMS = Map.ofEntries( //
            Map.entry("lamp", Set.of("light", "lamp")), //
            Map.entry("lamps", Set.of("light", "lamp")), //
            Map.entry("bulb", Set.of("light", "lightbulb")), //
            Map.entry("blind", Set.of("shutter", "blinds")), //
            Map.entry("blinds", Set.of("shutter", "blinds")), //
            Map.entry("shade", Set.of("shutter", "blinds")), //
            Map.entry("shades", Set.of("shutter", "blinds")), //
            Map.entry("heater", Set.of("heating")), //
            Map.entry("thermostat", Set.of("temperature", "setpoint")), //
            Map.entry("conditioning", Set.of("ac", "airconditioning")), //
            Map.entry("conditioner", Set.of("ac", "airconditioning")), //
            Map.entry("conditioners", Set.of("ac", "airconditioning")), //
            Map.entry("aircon", Set.of("ac", "airconditioning")), //
            Map.entry("music", Set.of("playback", "player", "speaker", "receiver")), //
            Map.entry("song", Set.of("playback", "player", "speaker", "receiver")), //
            Map.entry("track", Set.of("playback", "player", "speaker", "receiver")), //
            Map.entry("sound", Set.of("volume")), //
            Map.entry("television", Set.of("television", "tv")), //
            Map.entry("locked", Set.of("lock")), //
            // Outdoor values come from weather services rather than a location
            Map.entry("outside", Set.of("weather")), //
            Map.entry("outdoor", Set.of("weather")), //
            Map.entry("outdoors", Set.of("weather")), //
            Map.entry("unlocked", Set.of("lock")), //
            // Comparatives name the property they change, e.g. "make it warmer" targets a temperature setpoint
            Map.entry("warmer", Set.of("temperature", "setpoint")), //
            Map.entry("hotter", Set.of("temperature", "setpoint")), //
            Map.entry("cooler", Set.of("temperature", "setpoint")), //
            Map.entry("colder", Set.of("temperature", "setpoint")), //
            Map.entry("brighter", Set.of("light")), //
            Map.entry("brighten", Set.of("light")), //
            Map.entry("darker", Set.of("light")), //
            Map.entry("louder", Set.of("volume")), //
            Map.entry("quieter", Set.of("volume")));

    private static final Map<String, Set<String>> QUERY_PROPERTY_SYNONYMS = Map.ofEntries( //
            Map.entry("warm", Set.of("temperature")), //
            Map.entry("hot", Set.of("temperature")), //
            Map.entry("cold", Set.of("temperature")), //
            Map.entry("cool", Set.of("temperature")), //
            Map.entry("humid", Set.of("humidity")), //
            Map.entry("windy", Set.of("wind", "windspeed")), //
            Map.entry("loud", Set.of("volume")));

    @Override
    protected Map<String, Set<String>> semanticTokenSynonyms() {
        return SEMANTIC_SYNONYMS;
    }

    @Override
    protected Map<String, Set<String>> queryPropertySynonyms() {
        return QUERY_PROPERTY_SYNONYMS;
    }

    @Override
    protected Set<String> queryStopWords() {
        return STOP_WORDS;
    }
}
