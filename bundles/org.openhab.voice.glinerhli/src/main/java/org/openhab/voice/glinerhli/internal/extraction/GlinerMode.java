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
package org.openhab.voice.glinerhli.internal.extraction;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * GLiNER extraction mode controlling which model heads are loaded.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public enum GlinerMode {
    /**
     * <a href="https://github.com/fastino-ai/GLiNER2">GLiNER2</a> model with both NER and classifier heads loaded.
     */
    GLINER2_DUAL_HEAD,
    /**
     * <a href="https://github.com/urchade/GLiNER">GLiNER1</a> model with only NER head loaded.
     */
    GLINER_NER_ONLY
}
