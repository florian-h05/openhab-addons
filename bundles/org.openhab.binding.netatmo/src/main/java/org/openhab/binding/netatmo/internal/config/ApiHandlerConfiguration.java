/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.netatmo.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ApiHandlerConfiguration} is responsible for holding configuration
 * information needed to access Netatmo API and general binding behavior setup
 *
 * @author Gaël L'hopital - Initial contribution
 */
@NonNullByDefault
public class ApiHandlerConfiguration {
    public static final String CLIENT_ID = "clientId";

    public String clientId = "";
    public String clientSecret = "";
    public String webHookUrl = "";
    public String webHookPostfix = "";
    public int reconnectInterval = 300;

    public ConfigurationLevel check() {
        if (clientId.isBlank()) {
            return ConfigurationLevel.EMPTY_CLIENT_ID;
        } else if (clientSecret.isBlank()) {
            return ConfigurationLevel.EMPTY_CLIENT_SECRET;
        }
        return ConfigurationLevel.COMPLETED;
    }
}
