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
 * German {@link LanguageSupport} implementation.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public final class GermanLanguageSupport extends AbstractPatternLanguageSupport {

    private static final Pattern DECIMAL_COMMA_PATTERN = Pattern.compile("(?<=\\d),(?=\\d)");

    private static final Map<String, HSBType> COLORS = Map.ofEntries( //
            Map.entry("rot", HSBType.RED), //
            Map.entry("grün", HSBType.GREEN), //
            Map.entry("gruen", HSBType.GREEN), //
            Map.entry("blau", HSBType.BLUE), //
            Map.entry("weiß", HSBType.WHITE), //
            Map.entry("weiss", HSBType.WHITE), //
            Map.entry("warmweiß", new HSBType("36,35,100")), //
            Map.entry("warmweiss", new HSBType("36,35,100")), //
            Map.entry("kaltweiß", new HSBType("210,15,100")), //
            Map.entry("kaltweiss", new HSBType("210,15,100")), //
            Map.entry("tageslicht", new HSBType("50,5,100")), //
            Map.entry("gelb", HSBType.fromRGB(255, 224, 32)), //
            Map.entry("orange", HSBType.fromRGB(255, 160, 16)), //
            Map.entry("lila", HSBType.fromRGB(128, 0, 128)), //
            Map.entry("violett", HSBType.fromRGB(128, 0, 128)), //
            Map.entry("rosa", HSBType.fromRGB(255, 96, 208)), //
            Map.entry("türkis", HSBType.fromRGB(0, 255, 255)), //
            Map.entry("tuerkis", HSBType.fromRGB(0, 255, 255)));

    public GermanLanguageSupport() {
        super(Locale.GERMAN, Map.ofEntries( //
                entry(Intent.QUERY_STATE, keywords("wie|status|ob|ist\\s+es|welche|welcher|welches")), //
                entry(Intent.SET_VALUE, keywords("stell\\w*|setz\\w*|änder\\w*|aender\\w*|mach\\w*|schalt\\w*")), //
                entry(Intent.INCREASE,
                        keywords("heller|wärmer|waermer|lauter|hoch|hinauf|herauf|höher|hoeher|erhöh\\w*|erhoeh\\w*")), //
                entry(Intent.DECREASE, keywords(
                        "dunkler|kälter|kaelter|kühler|kuehler|leiser|runter|herunter|hinunter|niedriger|verringer\\w*|senk\\w*|dimm\\w*")), //
                entry(Intent.TOGGLE, keywords("umschalt\\w*|wechsel\\w*")), //
                entry(Intent.STOP, keywords("stopp?\\w*|anhalt\\w*|halt")), //
                entry(Intent.PAUSE, keywords("paus\\w*")), //
                entry(Intent.PLAY, keywords("abspiel\\w*|fortsetz\\w*|weiterspiel\\w*|wiedergabe")), //
                entry(Intent.NEXT, keywords("nächst\\w*|naechst\\w*|überspring\\w*|ueberspring\\w*|weiter")), //
                entry(Intent.PREVIOUS, keywords("vorherig\\w*|zurück|zurueck")), //
                entry(Intent.OPEN, keywords("öffn\\w*|oeffn\\w*|aufmach\\w*|auf")), //
                entry(Intent.CLOSE, keywords("schließ\\w*|schliess\\w*|zumach\\w*|zu")), //
                entry(Intent.TURN_OFF, keywords("ausschalt\\w*|ausmach\\w*|deaktivier\\w*|aus")), //
                entry(Intent.TURN_ON, keywords("einschalt\\w*|anmach\\w*|aktivier\\w*|ein|an|start\\w*"))), //
                keywords("alle|allen|alles|sämtliche|saemtliche|beide|beiden|überall|ueberall"), //
                Pattern.compile(
                        "(?iu)^\\s*(ist|sind|war|waren|hat|haben|gibt|läuft|laeuft|was|wer|wo|wann|warum|sag\\s+mir|zeig\\w*)\\b"), //
                keywords("auf"), //
                keywords(
                        "heller|wärmer|waermer|lauter|höher|hoeher|dunkler|kälter|kaelter|kühler|kuehler|leiser|niedriger|um"), //
                COLORS);
    }

    @Override
    public String normalizeValue(String rawValue) {
        String normalized = DECIMAL_COMMA_PATTERN.matcher(rawValue.trim()).replaceAll(".");
        normalized = normalized.replaceAll("(?iu)\\bprozent\\b", "%");
        normalized = normalized.replaceAll("(?iu)\\bgrad\\s+celsius\\b", "°C");
        normalized = normalized.replaceAll("(?iu)\\bgrad\\s+fahrenheit\\b", "°F");
        // A bare "Grad" does not name the scale, so the value is interpreted in the item's unit (°C or °F)
        normalized = normalized.replaceAll("(?iu)\\bgrad\\b", "");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private static final Map<String, Set<String>> SEMANTIC_SYNONYMS = Map.ofEntries( //
            // Locations
            Map.entry("schlafzimmer", Set.of("bedroom")), //
            Map.entry("wohnzimmer", Set.of("living room")), //
            Map.entry("küche", Set.of("kitchen")), //
            Map.entry("kueche", Set.of("kitchen")), //
            Map.entry("waschküche", Set.of("laundry room")), //
            Map.entry("hauswirtschaftsraum", Set.of("utility room")), //
            Map.entry("toilette", Set.of("toilet")), //
            Map.entry("wc", Set.of("toilet")), //
            Map.entry("waschkueche", Set.of("laundry room")), //
            Map.entry("badezimmer", Set.of("bathroom")), //
            Map.entry("bad", Set.of("bathroom")), //
            Map.entry("büro", Set.of("office")), //
            Map.entry("buero", Set.of("office")), //
            Map.entry("arbeitszimmer", Set.of("office")), //
            Map.entry("kinderzimmer", Set.of("child")), //
            Map.entry("gästezimmer", Set.of("guestroom")), //
            Map.entry("gaestezimmer", Set.of("guestroom")), //
            Map.entry("flur", Set.of("corridor")), //
            Map.entry("diele", Set.of("corridor")), //
            Map.entry("keller", Set.of("cellar", "basement")), //
            Map.entry("erdgeschoss", Set.of("ground floor")), //
            Map.entry("obergeschoss", Set.of("first floor")), //
            Map.entry("dachgeschoss", Set.of("second floor", "attic")), //
            // Devices and properties
            Map.entry("licht", Set.of("light", "lightbulb", "downlight")), //
            Map.entry("lichter", Set.of("light", "lights")), //
            Map.entry("lampe", Set.of("light", "lightbulb", "lamp")), //
            Map.entry("lampen", Set.of("light", "lights", "lamp")), //
            Map.entry("deckenlicht", Set.of("ceiling light")), //
            Map.entry("deckenlampe", Set.of("ceiling light", "ceiling lamp")), //
            Map.entry("stehlampe", Set.of("floor lamp")), //
            Map.entry("schreibtischlampe", Set.of("desk light", "desk lamp")), //
            Map.entry("lichtstreifen", Set.of("light stripe")), //
            Map.entry("spiegel", Set.of("mirror")), //
            Map.entry("regal", Set.of("shelf")), //
            Map.entry("schreibtischleuchte", Set.of("desk light", "desk lamp")), //
            Map.entry("heizung", Set.of("heating", "hvac", "temperature")), //
            Map.entry("heizkörper", Set.of("heating")), //
            Map.entry("heizkoerper", Set.of("heating")), //
            Map.entry("temperatur", Set.of("temperature")), //
            Map.entry("thermostat", Set.of("temperature", "setpoint")), //
            Map.entry("klimaanlage", Set.of("ac", "airconditioning")), //
            Map.entry("klimaanlagen", Set.of("ac", "airconditioning")), //
            Map.entry("rollladen", Set.of("shutter", "blinds")), //
            Map.entry("rollläden", Set.of("shutter", "shutters", "blinds")), //
            Map.entry("rolllaeden", Set.of("shutter", "shutters", "blinds")), //
            Map.entry("rollo", Set.of("shutter", "blinds")), //
            Map.entry("rollos", Set.of("shutter", "shutters", "blinds")), //
            Map.entry("jalousie", Set.of("shutter", "blinds")), //
            Map.entry("jalousien", Set.of("shutter", "shutters", "blinds")), //
            Map.entry("fenster", Set.of("window")), //
            Map.entry("tür", Set.of("door")), //
            Map.entry("tuer", Set.of("door")), //
            Map.entry("haustür", Set.of("front door")), //
            Map.entry("garagentür", Set.of("garage door")), //
            Map.entry("garagentuer", Set.of("garage door")), //
            Map.entry("haustuer", Set.of("front door")), //
            Map.entry("schloss", Set.of("lock")), //
            Map.entry("abgeschlossen", Set.of("lock")), //
            // Outdoor values come from weather services rather than a location
            Map.entry("draußen", Set.of("weather")), //
            Map.entry("draussen", Set.of("weather")), //
            Map.entry("außen", Set.of("weather")), //
            Map.entry("aussen", Set.of("weather")), //
            Map.entry("verschlossen", Set.of("lock")), //
            Map.entry("lautsprecher", Set.of("speaker")), //
            Map.entry("lautstärke", Set.of("volume")), //
            Map.entry("lautstaerke", Set.of("volume")), //
            Map.entry("fernseher", Set.of("television", "tv")), //
            Map.entry("musik", Set.of("playback", "player", "speaker", "receiver")), //
            Map.entry("lied", Set.of("song", "playback", "player", "speaker", "receiver")), //
            Map.entry("titel", Set.of("song", "playback", "player", "speaker", "receiver")), //
            Map.entry("interpret", Set.of("artist")), //
            Map.entry("künstler", Set.of("artist")), //
            Map.entry("kuenstler", Set.of("artist")), //
            Map.entry("wiedergabe", Set.of("playback", "player")), //
            Map.entry("eingang", Set.of("input")), //
            Map.entry("quelle", Set.of("input")), //
            Map.entry("feuchtigkeit", Set.of("humidity")), //
            Map.entry("windgeschwindigkeit", Set.of("windspeed")), //
            Map.entry("mondphase", Set.of("moon phase")), //
            Map.entry("batterie", Set.of("battery")), //
            Map.entry("ladestand", Set.of("soc")), //
            Map.entry("leistung", Set.of("power")), //
            Map.entry("strom", Set.of("electricity")), //
            Map.entry("netz", Set.of("grid")), //
            Map.entry("tagesproduktion", Set.of("daily production")), //
            Map.entry("sonnenuntergang", Set.of("sunset")), //
            Map.entry("sonnenaufgang", Set.of("sunrise")), //
            Map.entry("tarif", Set.of("tariff")), //
            // Comparatives name the property they change, e.g. "mach es wärmer" targets a temperature setpoint
            Map.entry("wärmer", Set.of("temperature", "setpoint")), //
            Map.entry("waermer", Set.of("temperature", "setpoint")), //
            Map.entry("kälter", Set.of("temperature", "setpoint")), //
            Map.entry("kaelter", Set.of("temperature", "setpoint")), //
            Map.entry("kühler", Set.of("temperature", "setpoint")), //
            Map.entry("kuehler", Set.of("temperature", "setpoint")), //
            Map.entry("heller", Set.of("light")), //
            Map.entry("dunkler", Set.of("light")), //
            Map.entry("lauter", Set.of("volume")), //
            Map.entry("leiser", Set.of("volume")));

    private static final Map<String, Set<String>> QUERY_PROPERTY_SYNONYMS = Map.ofEntries( //
            Map.entry("warm", Set.of("temperature")), //
            Map.entry("heiß", Set.of("temperature")), //
            Map.entry("heiss", Set.of("temperature")), //
            Map.entry("kalt", Set.of("temperature")), //
            Map.entry("kühl", Set.of("temperature")), //
            Map.entry("kuehl", Set.of("temperature")), //
            Map.entry("feucht", Set.of("humidity")), //
            Map.entry("windig", Set.of("wind", "windspeed")), //
            Map.entry("laut", Set.of("volume")));

    /**
     * Function words, which neither name a target nor make a state query about something else.
     */
    private static final Set<String> STOP_WORDS = Set.of("im", "der", "die", "das", "dem", "den", "des", "ein", "eine",
            "einen", "einer", "am", "auf", "und", "wie", "was", "wer", "wann", "wo", "welche", "welcher", "welches",
            "ist", "sind", "war", "gibt", "haben", "hat", "wir", "uns", "unser", "unsere", "läuft", "laeuft", "gerade",
            "aktuell", "jetzt", "heute", "noch", "mal", "denn", "eigentlich", "hoch", "viel", "sag", "zeig", "ob",
            "offen", "geschlossen", "schalte", "dimme", "stell", "alle", "in", "es", "ich", "mir", "mich", "von",
            "bitte");

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
