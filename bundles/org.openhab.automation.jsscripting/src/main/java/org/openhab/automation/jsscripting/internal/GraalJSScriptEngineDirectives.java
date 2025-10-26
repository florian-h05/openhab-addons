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
package org.openhab.automation.jsscripting.internal;

import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes and holds JavaScript directives.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class GraalJSScriptEngineDirectives {
    private static final Pattern USE_WRAPPER_DIRECTIVE = Pattern
            .compile("^\\s*([\"'])use wrapper(?:=(?<enabled>true|false))?\\1;?\\s*$");
    private static final Pattern USE_DEBUGGER_DIRECTIVE = Pattern
            .compile("^\\s*([\"'])use debugger=(?<port>\\d+)\\1;?\\s*$");

    private final Logger logger = LoggerFactory.getLogger(GraalJSScriptEngineDirectives.class);

    private @Nullable Boolean useWrapper;
    private boolean useDebugger = false;
    private @Nullable Integer debuggerPort;

    public GraalJSScriptEngineDirectives(String script) {
        // keep this extendable for more directives by checking the first n lines (n = number of directives)
        // up to two directives: "use strict" (handled by Graal) and "use wrapper"
        List<String> header = script.lines().limit(2).toList();
        for (String line : header) {
            var matcher = USE_WRAPPER_DIRECTIVE.matcher(line);
            if (matcher.matches()) {
                var enabled = matcher.group("enabled");
                if (enabled == null || enabled.isBlank()) {
                    useWrapper = true;
                } else if ("false".equals(enabled)) {
                    useWrapper = false;
                } else if ("true".equals(enabled)) {
                    useWrapper = true;
                } else {
                    logger.warn(
                            "Invalid value '{}' for 'use wrapper' directive in script. Allowed values: \"true\", \"false\", none.",
                            enabled);
                }
            }
            matcher = USE_DEBUGGER_DIRECTIVE.matcher(line);
            if (matcher.matches()) {
                var port = matcher.group("port");
                if (port == null || port.isBlank()) {
                    logger.warn(
                            "Invalid value '{}' for 'use debugger' directive in script. Allowed values: a valid port number.",
                            port);
                    continue;
                }
                try {
                    debuggerPort = Integer.parseInt(port);
                } catch (NumberFormatException e) {
                    logger.warn(
                            "Invalid value '{}' for 'use debugger' directive in script. Allowed values: a valid port number.",
                            port);
                    continue;
                }
                useDebugger = true;
            }
        }
    }

    public @Nullable Boolean useWrapper() {
        return useWrapper;
    }

    public boolean useDebugger() {
        return useDebugger;
    }

    public @Nullable Integer getDebuggerPort() {
        return debuggerPort;
    }

    @Override
    public String toString() {
        return "GraalJSScriptEngineDirectives(useWrapper=" + useWrapper + ", useDebugger=" + useDebugger
                + ", debuggerPort=" + debuggerPort + ')';
    }
}
