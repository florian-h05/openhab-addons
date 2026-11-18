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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Registry of supported {@link LanguageSupport} implementations, keyed by ISO language code.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public final class LanguageRegistry {

    private static final List<LanguageSupport> LANGUAGES = List.of(new EnglishLanguageSupport(),
            new GermanLanguageSupport());
    private static final Map<String, LanguageSupport> BY_LANGUAGE = LANGUAGES.stream().collect(
            Collectors.toUnmodifiableMap(lang -> lang.locale().getLanguage().toLowerCase(Locale.ROOT), lang -> lang));
    private static final LanguageSupport DEFAULT_LANGUAGE = BY_LANGUAGE.getOrDefault("en", LANGUAGES.getFirst());
    private static final Set<Locale> SUPPORTED_LOCALES = LANGUAGES.stream().map(LanguageSupport::locale)
            .collect(Collectors.toUnmodifiableSet());

    private LanguageRegistry() {
    }

    public static Set<Locale> supportedLocales() {
        return SUPPORTED_LOCALES;
    }

    public static boolean isSupported(Locale locale) {
        return BY_LANGUAGE.containsKey(locale.getLanguage().toLowerCase(Locale.ROOT));
    }

    public static LanguageSupport forLocale(Locale locale) {
        LanguageSupport match = BY_LANGUAGE.get(locale.getLanguage().toLowerCase(Locale.ROOT));
        return match != null ? match : DEFAULT_LANGUAGE;
    }
}
