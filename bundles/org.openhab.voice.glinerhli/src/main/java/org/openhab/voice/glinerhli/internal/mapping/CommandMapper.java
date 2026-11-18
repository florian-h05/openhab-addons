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
package org.openhab.voice.glinerhli.internal.mapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.measure.Quantity;
import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.items.Item;
import org.openhab.core.library.CoreItemFactory;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.transform.util.ItemDisplayStateUtil;
import org.openhab.core.types.Command;
import org.openhab.core.types.CommandDescription;
import org.openhab.core.types.CommandOption;
import org.openhab.core.types.State;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.TypeParser;
import org.openhab.core.types.UnDefType;
import org.openhab.core.types.util.UnitUtils;
import org.openhab.core.voice.text.InterpretationException;
import org.openhab.core.voice.text.UnrecoverableInterpretationException;
import org.openhab.voice.glinerhli.internal.I18nSupport;
import org.openhab.voice.glinerhli.internal.Intent;
import org.openhab.voice.glinerhli.internal.ParsedUtterance;
import org.openhab.voice.glinerhli.internal.language.LanguageRegistry;
import org.openhab.voice.glinerhli.internal.retrieval.SemanticItemIndex;

/**
 * Deterministic Stage 3 command mapper and localized state formatter.
 * Delegates language-specific value/color parsing to
 * {@link org.openhab.voice.glinerhli.internal.language.LanguageSupport} and user-facing messages to
 * {@link I18nSupport}.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class CommandMapper {

    private static final int DIMMER_STEP_PERCENT = 10;
    private static final Pattern INTEGER_CONVERSION_PATTERN = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*d");
    private static final Pattern NUMBER_AND_UNIT_PATTERN = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(.*)");

    private final I18nSupport i18n;

    public CommandMapper(I18nSupport i18n) {
        this.i18n = i18n;
    }

    /**
     * Maps a {@link ParsedUtterance} and target {@link Item} to an openHAB {@link Command}.
     *
     * @param parsed Stage 1 extraction result
     * @param item target openHAB Item
     * @param locale active user locale
     * @return resolved openHAB Command
     * @throws InterpretationException if the intent cannot be mapped to a valid command for the item
     */
    public Command mapCommand(ParsedUtterance parsed, Item item, Locale locale) throws InterpretationException {
        Item effective = SemanticItemIndex.resolveEffectiveItem(item);
        if (effective == null) {
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.cannot_accept_commands",
                    "Item ''{0}'' cannot accept commands.", displayName(item)));
        }
        Intent intent = parsed.intent();
        if (intent == null) {
            throw new InterpretationException(
                    i18n.getText(locale, "glinerhli.error.no_action_recognized", "No action was recognized."));
        }
        String baseType = SemanticItemIndex.baseItemType(effective);

        return switch (intent) {
            case TURN_ON -> mapOnOff(item, effective, OnOffType.ON, locale);
            case TURN_OFF -> mapOnOff(item, effective, OnOffType.OFF, locale);
            case SET_VALUE -> mapSetValue(parsed.value(), item, effective, baseType, locale);
            case INCREASE -> mapIncreaseDecrease(item, baseType, true, locale);
            case DECREASE -> mapIncreaseDecrease(item, baseType, false, locale);
            case OPEN -> mapOpenClose(item, baseType, true, locale);
            case CLOSE -> mapOpenClose(item, baseType, false, locale);
            case STOP -> mapStop(item, effective, baseType, locale);
            case TOGGLE -> mapToggle(item, effective, baseType, locale);
            case PLAY -> mapPlayPause(item, effective, PlayPauseType.PLAY, locale);
            case PAUSE -> mapPlayPause(item, effective, PlayPauseType.PAUSE, locale);
            case NEXT -> mapNextPrevious(item, effective, NextPreviousType.NEXT, locale);
            case PREVIOUS -> mapNextPrevious(item, effective, NextPreviousType.PREVIOUS, locale);
            case QUERY_STATE -> throw new InterpretationException(
                    i18n.getText(locale, "glinerhli.error.query_no_command", "State queries do not produce commands."));
        };
    }

    private Command mapOnOff(Item targetItem, Item effectiveItem, OnOffType command, Locale locale)
            throws InterpretationException {
        if (!targetItem.getAcceptedCommandTypes().contains(OnOffType.class)
                && !effectiveItem.getAcceptedCommandTypes().contains(OnOffType.class)) {
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.on_off_unsupported",
                    "{0} does not support switching on or off.", displayName(targetItem)));
        }
        return command;
    }

    private Command mapSetValue(@Nullable String rawValue, Item targetItem, Item effectiveItem, String baseType,
            Locale locale) throws InterpretationException {
        if (rawValue == null || rawValue.isBlank()) {
            throw new InterpretationException(
                    i18n.getText(locale, "glinerhli.error.no_target_value", "No target value was specified."));
        }

        String trimmed = rawValue.trim();

        // 1. Match localized CommandDescription / StateDescription option labels (StandardInterpreter pattern)
        Command optionCommand = matchDescriptionOption(trimmed, targetItem, locale);
        if (optionCommand != null) {
            return optionCommand;
        }

        // 2. Color items: match localized color names, brightness percentages, or HSB strings
        if (CoreItemFactory.COLOR.equals(baseType)) {
            HSBType hsb = LanguageRegistry.forLocale(locale).parseColor(trimmed);
            if (hsb != null) {
                return hsb;
            }
            String normalizedColor = normalizeLocalizedValue(locale, trimmed);
            if (!normalizedColor.contains(",") && extractNumber(normalizedColor) != null) {
                return parsePercent(normalizedColor, trimmed, targetItem, locale);
            }
            Command parsedColor = TypeParser.parseCommand(targetItem.getAcceptedCommandTypes(), trimmed);
            if (parsedColor != null) {
                return parsedColor;
            }
            throw new InterpretationException(
                    i18n.getText(locale, "glinerhli.error.unknown_color", "Unknown color value: {0}", trimmed));
        }

        String normalized = normalizeLocalizedValue(locale, trimmed);

        // 3. Dimmer / Rollershutter: percentage
        if (CoreItemFactory.DIMMER.equals(baseType) || CoreItemFactory.ROLLERSHUTTER.equals(baseType)) {
            return parsePercent(normalized, trimmed, targetItem, locale);
        }

        // 4. Number / Number:* items: parse QuantityType when unit present or item has dimension/unit
        if (CoreItemFactory.NUMBER.equals(baseType)) {
            return mapNumberCommand(normalized, trimmed, effectiveItem, locale);
        }

        // 5. Generic TypeParser fallback (e.g. StringItem or custom command types)
        Command genericCommand = TypeParser.parseCommand(targetItem.getAcceptedCommandTypes(), normalized);
        if (genericCommand != null) {
            return genericCommand;
        }
        throw new InterpretationException(i18n.getText(locale, "glinerhli.error.unparsed_command",
                "Could not parse command ''{0}'' for {1}.", trimmed, displayName(targetItem)));
    }

    /**
     * Parses a percentage, rejecting values outside 0-100 % or with another unit (e.g. a color temperature in K)
     * instead of silently clamping or misinterpreting them.
     */
    private PercentType parsePercent(String normalized, String rawValue, Item targetItem, Locale locale)
            throws InterpretationException {
        Matcher matcher = NUMBER_AND_UNIT_PATTERN.matcher(normalized);
        BigDecimal numeric = matcher.find() ? extractNumber(normalized) : null;
        if (numeric == null) {
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.invalid_percentage",
                    "Could not parse percentage from: {0}", rawValue));
        }
        String unit = matcher.group(2).trim();
        if ((!unit.isEmpty() && !"%".equals(unit)) || numeric.signum() < 0
                || numeric.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.percentage_out_of_range",
                    "{0} cannot be set to {1}, it accepts percentages from 0 to 100.", displayName(targetItem),
                    rawValue));
        }
        return new PercentType(numeric.setScale(0, RoundingMode.HALF_UP));
    }

    private Command mapNumberCommand(String normalized, String rawValue, Item effectiveItem, Locale locale)
            throws InterpretationException {
        Matcher matcher = NUMBER_AND_UNIT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.invalid_number",
                    "Could not parse numeric value from: {0}", rawValue));
        }
        String numberPart = matcher.group(1);
        String unitPart = matcher.group(2).trim();

        BigDecimal decimal;
        try {
            decimal = new BigDecimal(numberPart);
        } catch (NumberFormatException e) {
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.invalid_number_syntax",
                    "Invalid numeric value: {0}", rawValue), e);
        }

        if (!unitPart.isEmpty()) {
            try {
                return QuantityType.valueOf(numberPart + " " + unitPart);
            } catch (IllegalArgumentException e) {
                // Fall through if trailing words are not a recognized UoM unit
            }
        }

        if (effectiveItem instanceof NumberItem numberItem) {
            Unit<? extends Quantity<?>> itemUnit = numberItem.getUnit();
            if (itemUnit != null) {
                return new QuantityType<>(decimal, itemUnit);
            }
        }
        return new DecimalType(decimal);
    }

    private Command mapIncreaseDecrease(Item targetItem, String baseType, boolean increase, Locale locale)
            throws InterpretationException {
        switch (baseType) {
            case CoreItemFactory.DIMMER, CoreItemFactory.COLOR -> {
                if (targetItem.getAcceptedCommandTypes().contains(IncreaseDecreaseType.class)) {
                    return increase ? IncreaseDecreaseType.INCREASE : IncreaseDecreaseType.DECREASE;
                }
                PercentType current = targetItem.getStateAs(PercentType.class);
                int basePercent = current != null ? current.intValue() : (increase ? 0 : 100);
                int nextPercent = Math.clamp(basePercent + (increase ? DIMMER_STEP_PERCENT : -DIMMER_STEP_PERCENT), 0,
                        100);
                return new PercentType(nextPercent);
            }
            case CoreItemFactory.ROLLERSHUTTER -> {
                return increase ? UpDownType.UP : UpDownType.DOWN;
            }
            case CoreItemFactory.NUMBER -> {
                State state = targetItem.getState();
                BigDecimal delta = increase ? BigDecimal.ONE : BigDecimal.ONE.negate();
                if (state instanceof QuantityType<?> quantityState) {
                    QuantityType<?> base = quantityState;
                    // Dimensionless states are commonly held in the system unit "one" (e.g. 0.25 for 25 %), where
                    // a step of 1 would mean 100 %, so step them in percent as they are presented
                    if (Units.ONE.isCompatible(quantityState.getUnit())
                            && !Units.PERCENT.equals(quantityState.getUnit())) {
                        QuantityType<?> percent = quantityState.toUnit(Units.PERCENT);
                        if (percent != null) {
                            base = percent;
                        }
                    }
                    return new QuantityType<>(base.toBigDecimal().add(delta), base.getUnit());
                }
                if (state instanceof DecimalType decimalState) {
                    return new DecimalType(decimalState.toBigDecimal().add(delta));
                }
                throw new InterpretationException(i18n.getText(locale, "glinerhli.error.adjust_unknown_state",
                        "Cannot adjust {0} because its current value is unknown.", displayName(targetItem)));
            }
        }

        throw new InterpretationException(i18n.getText(locale, "glinerhli.error.adjust_unsupported",
                "{0} cannot be increased or decreased.", displayName(targetItem)));
    }

    private Command mapOpenClose(Item targetItem, String baseType, boolean open, Locale locale)
            throws InterpretationException {
        if (CoreItemFactory.CONTACT.equals(baseType)) {
            throw new UnrecoverableInterpretationException(i18n.getText(locale, "glinerhli.error.contact_read_only",
                    "{0} is a read-only contact sensor and cannot be controlled.", displayName(targetItem)));
        }
        if (CoreItemFactory.ROLLERSHUTTER.equals(baseType)
                || targetItem.getAcceptedCommandTypes().contains(UpDownType.class)) {
            return open ? UpDownType.UP : UpDownType.DOWN;
        }
        throw new InterpretationException(i18n.getText(locale, "glinerhli.error.open_close_unsupported",
                "{0} cannot be opened or closed.", displayName(targetItem)));
    }

    private Command mapStop(Item targetItem, Item effectiveItem, String baseType, Locale locale)
            throws InterpretationException {
        if (CoreItemFactory.ROLLERSHUTTER.equals(baseType)
                || targetItem.getAcceptedCommandTypes().contains(StopMoveType.class)
                || effectiveItem.getAcceptedCommandTypes().contains(StopMoveType.class)) {
            return StopMoveType.STOP;
        }
        if (CoreItemFactory.PLAYER.equals(baseType)
                || targetItem.getAcceptedCommandTypes().contains(PlayPauseType.class)
                || effectiveItem.getAcceptedCommandTypes().contains(PlayPauseType.class)) {
            return PlayPauseType.PAUSE;
        }
        throw new InterpretationException(i18n.getText(locale, "glinerhli.error.stop_unsupported",
                "{0} cannot be stopped.", displayName(targetItem)));
    }

    private Command mapToggle(Item targetItem, Item effectiveItem, String baseType, Locale locale)
            throws InterpretationException {
        if (targetItem.getAcceptedCommandTypes().contains(OnOffType.class)
                || effectiveItem.getAcceptedCommandTypes().contains(OnOffType.class)) {
            OnOffType current = targetItem.getStateAs(OnOffType.class);
            if (current != null) {
                return current == OnOffType.ON ? OnOffType.OFF : OnOffType.ON;
            }
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.toggle_unknown_state",
                    "Cannot toggle {0} because its current state is unknown.", displayName(targetItem)));
        }
        if (CoreItemFactory.PLAYER.equals(baseType)
                || targetItem.getAcceptedCommandTypes().contains(PlayPauseType.class)
                || effectiveItem.getAcceptedCommandTypes().contains(PlayPauseType.class)) {
            State state = targetItem.getState();
            if (state instanceof PlayPauseType playPause) {
                return playPause == PlayPauseType.PLAY ? PlayPauseType.PAUSE : PlayPauseType.PLAY;
            }
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.toggle_unknown_state",
                    "Cannot toggle {0} because its current state is unknown.", displayName(targetItem)));
        }
        throw new InterpretationException(i18n.getText(locale, "glinerhli.error.toggle_unsupported",
                "{0} cannot be toggled.", displayName(targetItem)));
    }

    private Command mapPlayPause(Item targetItem, Item effectiveItem, PlayPauseType command, Locale locale)
            throws InterpretationException {
        if (!targetItem.getAcceptedCommandTypes().contains(PlayPauseType.class)
                && !effectiveItem.getAcceptedCommandTypes().contains(PlayPauseType.class)) {
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.play_pause_unsupported",
                    "{0} does not support play or pause.", displayName(targetItem)));
        }
        return command;
    }

    private Command mapNextPrevious(Item targetItem, Item effectiveItem, NextPreviousType command, Locale locale)
            throws InterpretationException {
        if (!targetItem.getAcceptedCommandTypes().contains(NextPreviousType.class)
                && !effectiveItem.getAcceptedCommandTypes().contains(NextPreviousType.class)) {
            throw new InterpretationException(i18n.getText(locale, "glinerhli.error.next_prev_unsupported",
                    "{0} does not support skipping tracks.", displayName(targetItem)));
        }
        return command;
    }

    /**
     * Checks {@link CommandDescription} and {@link StateDescription} options for a case-insensitive label match and
     * parses the corresponding command via {@link TypeParser#parseCommand(List, String)}.
     */
    static @Nullable Command matchDescriptionOption(String extractedValue, Item item, Locale locale) {
        CommandDescription cmdDesc = item.getCommandDescription(locale);
        if (cmdDesc != null) {
            for (CommandOption option : cmdDesc.getCommandOptions()) {
                String label = option.getLabel();
                if ((label != null && label.trim().equalsIgnoreCase(extractedValue))
                        || option.getCommand().trim().equalsIgnoreCase(extractedValue)) {
                    Command parsed = TypeParser.parseCommand(item.getAcceptedCommandTypes(), option.getCommand());
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }

        StateDescription stateDesc = item.getStateDescription(locale);
        if (stateDesc != null) {
            for (StateOption option : stateDesc.getOptions()) {
                String label = option.getLabel();
                if ((label != null && label.trim().equalsIgnoreCase(extractedValue))
                        || option.getValue().trim().equalsIgnoreCase(extractedValue)) {
                    Command parsed = TypeParser.parseCommand(item.getAcceptedCommandTypes(), option.getValue());
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }
        return null;
    }

    static String normalizeLocalizedValue(Locale locale, String rawValue) {
        return LanguageRegistry.forLocale(locale).normalizeValue(rawValue);
    }

    private static @Nullable BigDecimal extractNumber(String normalized) {
        Matcher matcher = NUMBER_AND_UNIT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Formats an Item's current state into a natural, localized spoken response for {@code query_state}.
     *
     * @param item item whose state to report
     * @param locale active user locale
     * @param zoneId timezone used for {@code DateTimeType} formatting
     * @return spoken response sentence
     */
    public String formatStateResponse(Item item, Locale locale, ZoneId zoneId) {
        String name = displayName(item);
        State state = item.getState();

        if (state instanceof UnDefType) {
            return i18n.getText(locale, "glinerhli.state.unavailable", "{0} is currently unavailable.", name);
        }

        String formattedValue = formatLocalizedStateValue(item, state, locale, zoneId, i18n);
        return i18n.getText(locale, "glinerhli.state.format", "{0} is {1}", name, formattedValue);
    }

    /**
     * Formats an Item state value for a spoken response in the requested locale.
     * <p>
     * Numbers with a format pattern are formatted here in the requested locale, as {@link ItemDisplayStateUtil}
     * formats them in the default locale of the JVM. State options, transformations, and other patterns (e.g. dates or
     * durations) are delegated to {@link ItemDisplayStateUtil}. States without pattern or option are spoken naturally
     * rather than as raw values such as {@code ON} or {@code 21.481577496826375 °C}.
     */
    static String formatLocalizedStateValue(Item item, State state, Locale locale, ZoneId zoneId, I18nSupport i18n) {
        StateDescription stateDesc = item.getStateDescription(locale);
        if (stateDesc != null) {
            String pattern = stateDesc.getPattern();
            boolean hasPattern = pattern != null && !pattern.isBlank();
            boolean hasOption = hasMatchingOption(stateDesc, state);
            if (pattern != null && hasPattern && !hasOption) {
                String formatted = formatNumber(state, pattern, locale);
                if (formatted != null) {
                    return formatted;
                }
            }
            if (hasPattern || hasOption) {
                String displayState = ItemDisplayStateUtil.formatState(item.getName(), pattern, stateDesc.getOptions(),
                        state, zoneId);
                if (displayState != null && !displayState.isBlank()) {
                    return displayState;
                }
            }
        }

        State presentationState = toPresentationUnit(state);
        switch (presentationState) {
            case OnOffType onOff -> {
                return onOff == OnOffType.ON ? i18n.getText(locale, "glinerhli.state.on", "on")
                        : i18n.getText(locale, "glinerhli.state.off", "off");
            }
            case OpenClosedType openClosed -> {
                return openClosed == OpenClosedType.OPEN ? i18n.getText(locale, "glinerhli.state.open", "open")
                        : i18n.getText(locale, "glinerhli.state.closed", "closed");
            }
            case PlayPauseType playPause -> {
                return playPause == PlayPauseType.PLAY ? i18n.getText(locale, "glinerhli.state.playing", "playing")
                        : i18n.getText(locale, "glinerhli.state.paused", "paused");
            }
            case PercentType percent -> {
                return percent.intValue() + "%";
            }
            case QuantityType<?> quantity -> {
                NumberFormat nf = NumberFormat.getNumberInstance(locale);
                nf.setGroupingUsed(false);
                nf.setMaximumFractionDigits(2);
                String numStr = nf.format(quantity.toBigDecimal());
                String unitStr = quantity.getUnit().toString();
                return unitStr.isEmpty() || "one".equalsIgnoreCase(unitStr) ? numStr : numStr + " " + unitStr;
            }
            case DecimalType decimal -> {
                NumberFormat nf = NumberFormat.getNumberInstance(locale);
                nf.setGroupingUsed(false);
                nf.setMaximumFractionDigits(2);
                return nf.format(decimal.toBigDecimal());
            }
            default -> {
            }
        }
        return presentationState.toFullString();
    }

    private static boolean hasMatchingOption(StateDescription stateDesc, State state) {
        String value = state.toString();
        return stateDesc.getOptions().stream()
                .anyMatch(option -> option.getLabel() != null && option.getValue().equals(value));
    }

    /**
     * Formats a number or quantity with a plain format pattern in the given locale, converting quantities to the
     * pattern unit and substituting {@code %unit%} like {@link QuantityType#format(String)}. Returns {@code null} for
     * other states and patterns, e.g. transformations, {@code %s}, or time patterns of durations.
     */
    private static @Nullable String formatNumber(State state, String pattern, Locale locale) {
        if (ItemDisplayStateUtil.EXTRACT_TRANSFORM_FUNCTION_PATTERN.matcher(pattern).find() || pattern.contains("%s")
                || pattern.contains("%S")) {
            return null;
        }
        String formatPattern = pattern;
        BigDecimal value;
        if (state instanceof QuantityType<?> quantity) {
            boolean unitPlaceholder = pattern.contains(UnitUtils.UNIT_PLACEHOLDER);
            if (quantity.getUnit().isCompatible(Units.SECOND) && !unitPlaceholder) {
                return null;
            }
            QuantityType<?> effective = quantity;
            Unit<?> patternUnit = UnitUtils.parseUnit(pattern);
            if (patternUnit != null && !quantity.getUnit().equals(patternUnit)) {
                QuantityType<?> converted = quantity.toInvertibleUnit(patternUnit);
                if (converted != null) {
                    effective = converted;
                }
            } else if (patternUnit == null && toPresentationUnit(quantity) instanceof QuantityType<?> presentation) {
                effective = presentation;
            }
            if (unitPlaceholder) {
                String unitSymbol = Units.PERCENT.equals(effective.getUnit()) ? "%%" : effective.getUnit().toString();
                formatPattern = pattern.replace(UnitUtils.UNIT_PLACEHOLDER, unitSymbol);
            }
            value = effective.toBigDecimal();
        } else if (state instanceof DecimalType decimal) {
            value = decimal.toBigDecimal();
        } else {
            return null;
        }
        try {
            return String.format(locale, formatPattern, formatArgument(formatPattern, value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Dimensionless states are commonly held in the system unit "one" (e.g. 0.25), but presented as percent (25 %).
     */
    private static State toPresentationUnit(State state) {
        if (state instanceof QuantityType<?> quantity && Units.ONE.equals(quantity.getUnit())) {
            QuantityType<?> percent = quantity.toUnit(Units.PERCENT);
            if (percent != null) {
                return percent;
            }
        }
        return state;
    }

    /**
     * Returns the value as formatting argument; integer conversions such as {@code %d} reject {@link BigDecimal}.
     */
    private static Object formatArgument(String pattern, BigDecimal value) {
        return INTEGER_CONVERSION_PATTERN.matcher(pattern).find()
                ? value.setScale(0, RoundingMode.HALF_UP).toBigInteger()
                : value;
    }

    /**
     * Formats a concise spoken confirmation after dispatching one or more commands.
     */
    public String formatCommandConfirmation(List<Item> items, Command command, Locale locale) {
        String names = items.stream().map(CommandMapper::displayName).reduce((a, b) -> a + ", " + b).orElse("");
        return switch (command) {
            case OnOffType onOff ->
                onOff == OnOffType.ON ? i18n.getText(locale, "glinerhli.confirm.turned_on", "Turned on {0}.", names)
                        : i18n.getText(locale, "glinerhli.confirm.turned_off", "Turned off {0}.", names);
            case UpDownType upDown ->
                upDown == UpDownType.UP ? i18n.getText(locale, "glinerhli.confirm.opened", "Opened {0}.", names)
                        : i18n.getText(locale, "glinerhli.confirm.closed", "Closed {0}.", names);
            case StopMoveType stopMoveType -> i18n.getText(locale, "glinerhli.confirm.stopped", "Stopped {0}.", names);
            case PlayPauseType playPause -> playPause == PlayPauseType.PLAY
                    ? i18n.getText(locale, "glinerhli.confirm.playing", "Started playback on {0}.", names)
                    : i18n.getText(locale, "glinerhli.confirm.paused", "Paused {0}.", names);
            case NextPreviousType nextPrev -> nextPrev == NextPreviousType.NEXT
                    ? i18n.getText(locale, "glinerhli.confirm.next", "Skipped to next on {0}.", names)
                    : i18n.getText(locale, "glinerhli.confirm.previous", "Skipped to previous on {0}.", names);
            default -> i18n.getText(locale, "glinerhli.confirm.set", "Set {0} to {1}.", names, command.toFullString());
        };
    }

    public static String displayName(Item item) {
        String label = item.getLabel();
        return label != null && !label.isBlank() ? label.trim() : item.getName();
    }
}
