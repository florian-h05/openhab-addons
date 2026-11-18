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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.HSBType;
import org.openhab.voice.glinerhli.internal.Intent;

/**
 * Keyword-pattern based {@link LanguageSupport} implementation; subclasses only supply their keyword patterns and
 * vocabularies.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public abstract class AbstractPatternLanguageSupport implements LanguageSupport {

    /** Intents resolved from action keywords, in {@link Intent} ordinal (i.e. fallback priority) order. */
    private static final Set<Intent> ACTION_INTENTS = EnumSet
            .complementOf(EnumSet.of(Intent.QUERY_STATE, Intent.SET_VALUE));
    private static final Pattern NUMERIC_VALUE_PATTERN = Pattern.compile(".*\\d+.*");
    private static final Pattern PREPOSITION_TARGET_TAIL = Pattern.compile("^([^?!.,;:]+?)[?!.,;:]*$");
    private static final int MIN_COMPOUND_PART_LENGTH = 4;
    /** Linking elements ("Fugenlaute") joining German compound parts, e.g. "Küche" + "n" + "fenster". */
    private static final List<String> LINKING_ELEMENTS = List.of("", "s", "n", "en", "es");

    private final Locale locale;
    private final Map<Intent, Pattern> intentKeywords;
    private final Pattern allScopeKeywords;
    private final Pattern questionStart;
    private final Pattern absoluteTargetPreposition;
    private final Pattern relativeComparative;
    private final Map<String, HSBType> colors;
    private final List<Pattern> actionWordPatterns;

    /**
     * @param intentKeywords keywords per {@link Intent}; {@link Intent#QUERY_STATE} holds query words and
     *            {@link Intent#SET_VALUE} holds set verbs
     * @param allScopeKeywords quantifiers denoting an all/every scope
     * @param questionStart utterance prefix marking a question, e.g. a leading auxiliary verb
     * @param absoluteTargetPreposition preposition introducing an absolute target value (e.g. "to")
     * @param relativeComparative cues for a relative change (e.g. "brighter", "by")
     * @param colors lowercase color names
     */
    protected AbstractPatternLanguageSupport(Locale locale, Map<Intent, Pattern> intentKeywords,
            Pattern allScopeKeywords, Pattern questionStart, Pattern absoluteTargetPreposition,
            Pattern relativeComparative, Map<String, HSBType> colors) {
        if (!intentKeywords.keySet().containsAll(EnumSet.allOf(Intent.class))) {
            throw new IllegalArgumentException("Keywords missing for intents of " + locale);
        }
        this.locale = locale;
        this.intentKeywords = new EnumMap<>(intentKeywords);
        this.allScopeKeywords = allScopeKeywords;
        this.questionStart = questionStart;
        this.absoluteTargetPreposition = absoluteTargetPreposition;
        this.relativeComparative = relativeComparative;
        this.colors = colors;
        this.actionWordPatterns = Stream.concat(intentKeywords.values().stream(),
                Stream.of(allScopeKeywords, absoluteTargetPreposition, relativeComparative)).toList();
    }

    /**
     * Compiles case-insensitive whole-word alternatives, e.g. {@code keywords("on|turn\\s+on")}.
     */
    protected static Pattern keywords(String alternatives) {
        return Pattern.compile("(?iu)(^|\\b)(" + alternatives + ")(\\b|$)");
    }

    @Override
    public Locale locale() {
        return locale;
    }

    /**
     * Optional mapping from lowercase words in this language to alternative canonical/English phrases used for
     * cross-lingual metadata matching during Stage 2 retrieval. Each alternative may consist of several space-separated
     * words that are all required, e.g. "living room".
     */
    protected Map<String, Set<String>> semanticTokenSynonyms() {
        return Map.of();
    }

    /**
     * Optional mapping from lowercase property adjectives (e.g. {@code warm}, {@code humid}) to the phrases of the
     * property they ask about. Only applied to state queries: in commands or statements such adjectives describe a
     * condition ("it is too warm") rather than name a target, and grounding on them could pick the wrong direction.
     */
    protected Map<String, Set<String>> queryPropertySynonyms() {
        return Map.of();
    }

    /**
     * Optional lowercase stop words filtered from query tokens during Stage 2 lexical matching.
     */
    protected Set<String> queryStopWords() {
        return Set.of();
    }

    @Override
    public boolean isAllQuantifier(@Nullable String quantifier) {
        return quantifier != null && allScopeKeywords.matcher(quantifier.trim()).find();
    }

    @Override
    public boolean isQuestion(String utterance) {
        String trimmed = utterance.trim();
        return trimmed.endsWith("?") || asksForState(trimmed);
    }

    @Override
    public boolean asksForState(String utterance) {
        String trimmed = utterance.trim();
        return matches(Intent.QUERY_STATE, trimmed) || questionStart.matcher(trimmed).find();
    }

    @Override
    public boolean isActionWord(String token) {
        return actionWordPatterns.stream().anyMatch(pattern -> pattern.matcher(token).matches());
    }

    @Override
    public boolean isQueryStopWord(String word) {
        return queryStopWords().contains(word);
    }

    @Override
    public boolean hasAbsoluteTargetPreposition(@Nullable String actionPhrase, String utterance) {
        return (actionPhrase != null && absoluteTargetPreposition.matcher(actionPhrase).find())
                || absoluteTargetPreposition.matcher(utterance).find();
    }

    @Override
    public @Nullable String extractAbsoluteTargetValue(String utterance) {
        String trimmed = utterance.trim();
        Matcher matcher = absoluteTargetPreposition.matcher(trimmed);
        while (matcher.find()) {
            int end = matcher.end();
            if (end < trimmed.length() && Character.isWhitespace(trimmed.charAt(end))) {
                Matcher candidateMatcher = PREPOSITION_TARGET_TAIL.matcher(trimmed.substring(end).trim());
                if (candidateMatcher.matches() && !candidateMatcher.group(1).isBlank()) {
                    return candidateMatcher.group(1).trim();
                }
            }
        }
        return null;
    }

    @Override
    public @Nullable Intent resolveFallbackIntent(@Nullable String actionPhrase, @Nullable String commandValue,
            String utterance) {
        String fullText = utterance.trim();
        String primary = actionPhrase != null && !actionPhrase.isBlank() ? actionPhrase.trim() : fullText;
        boolean hasValue = commandValue != null && !commandValue.isBlank();
        boolean hasNumericValue = commandValue != null && NUMERIC_VALUE_PATTERN.matcher(commandValue).matches();

        if (matches(Intent.QUERY_STATE, primary) || matches(Intent.QUERY_STATE, fullText)) {
            return Intent.QUERY_STATE;
        }
        boolean hasAbsolutePreposition = absoluteTargetPreposition.matcher(primary).find()
                || absoluteTargetPreposition.matcher(fullText).find();
        if (hasNumericValue && (hasAbsolutePreposition
                || !(relativeComparative.matcher(primary).find() || relativeComparative.matcher(fullText).find()))) {
            return Intent.SET_VALUE;
        }
        // "Stell die Lampe auf blau": an extracted value after a set verb and target preposition is set, although the
        // preposition "auf" is also an open keyword
        if (hasValue && hasAbsolutePreposition
                && (matches(Intent.SET_VALUE, primary) || matches(Intent.SET_VALUE, fullText))) {
            return Intent.SET_VALUE;
        }
        Intent action = matchActionKeywords(primary);
        if (action != null) {
            return action;
        }
        if ((hasValue || absoluteTargetPreposition.matcher(fullText).find())
                && (matches(Intent.SET_VALUE, primary) || matches(Intent.SET_VALUE, fullText))) {
            return Intent.SET_VALUE;
        }
        if (!primary.equals(fullText)) {
            action = matchActionKeywords(fullText);
            if (action != null) {
                return action;
            }
        }
        return hasValue ? Intent.SET_VALUE : null;
    }

    @Override
    public @Nullable HSBType parseColor(String colorName) {
        return colors.get(colorName.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public Set<List<String>> synonyms(String word, boolean stateQuery) {
        Set<List<String>> phrases = new LinkedHashSet<>();
        addSynonyms(word, semanticTokenSynonyms(), phrases);
        if (stateQuery) {
            addSynonyms(word, queryPropertySynonyms(), phrases);
        }
        return phrases;
    }

    @Override
    public Set<String> expandSemanticTokens(Set<String> words) {
        Set<String> expanded = new HashSet<>(words);
        for (String word : words) {
            synonyms(word, false).forEach(expanded::addAll);
        }
        return Set.copyOf(expanded);
    }

    private static void addSynonyms(String word, Map<String, Set<String>> synonyms, Set<List<String>> target) {
        Set<String> direct = synonyms.get(word);
        if (direct != null) {
            direct.forEach(phrase -> target.add(List.of(phrase.split(" "))));
            return;
        }
        // Compound nouns name their head last and may name a qualifier first (e.g. German "Kellerlicht" is a
        // cellar light), so match synonym keys as suffix and the remainder as prefix; short keys are excluded to avoid
        // accidental matches
        synonyms.forEach((key, phrases) -> {
            if (key.length() >= MIN_COMPOUND_PART_LENGTH && word.length() > key.length() && word.endsWith(key)) {
                phrases.forEach(phrase -> target.add(List.of(phrase.split(" "))));
                String prefix = word.substring(0, word.length() - key.length());
                for (String linkingElement : LINKING_ELEMENTS) {
                    Set<String> prefixPhrases = prefix.length() - linkingElement.length() >= MIN_COMPOUND_PART_LENGTH
                            && prefix.endsWith(linkingElement)
                                    ? synonyms.get(prefix.substring(0, prefix.length() - linkingElement.length()))
                                    : null;
                    if (prefixPhrases != null) {
                        prefixPhrases.forEach(phrase -> target.add(List.of(phrase.split(" "))));
                        break;
                    }
                }
            }
        });
    }

    private boolean matches(Intent intent, String text) {
        Pattern pattern = intentKeywords.get(intent);
        return pattern != null && pattern.matcher(text).find();
    }

    private @Nullable Intent matchActionKeywords(String text) {
        for (Intent intent : ACTION_INTENTS) {
            if (matches(intent, text)) {
                return intent;
            }
        }
        return null;
    }
}
