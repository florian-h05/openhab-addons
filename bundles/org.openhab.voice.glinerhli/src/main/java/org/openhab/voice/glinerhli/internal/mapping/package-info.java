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
/**
 * Stage 3: Deterministic openHAB command mapping and localized spoken response formatting.
 * <p>
 * {@link CommandMapper} resolves Stage 1 intents and values against the target openHAB item(s) selected by Stage 2,
 * formatting current item states for state queries and mapping control intents onto typed openHAB {@code Command}s and
 * spoken confirmations while enforcing read-only item constraints.
 *
 * @author Florian Hotze - Initial contribution
 */
package org.openhab.voice.glinerhli.internal.mapping;
