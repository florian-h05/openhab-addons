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

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.i18n.TranslationProvider;
import org.osgi.framework.Bundle;

/**
 * Helper binding openHAB's {@link TranslationProvider} to this add-on's bundle.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class I18nSupport {

    private final TranslationProvider translationProvider;
    private final Bundle bundle;

    public I18nSupport(TranslationProvider translationProvider, Bundle bundle) {
        this.translationProvider = translationProvider;
        this.bundle = bundle;
    }

    public String getText(Locale locale, String key, String defaultText, @Nullable Object... arguments) {
        String text = translationProvider.getText(bundle, key, defaultText, locale, arguments);
        return text != null ? text : defaultText;
    }
}
