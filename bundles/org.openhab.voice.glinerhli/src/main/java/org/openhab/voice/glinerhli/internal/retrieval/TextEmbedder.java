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
package org.openhab.voice.glinerhli.internal.retrieval;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Abstraction for generating L2-normalized text embeddings for indexed openHAB Items and queries.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public interface TextEmbedder {

    /**
     * Embeds an indexed Item document.
     *
     * @param title item label or name used when the configured document prefix contains {@code {title}}
     * @param content serialized semantic representation of the item
     * @return L2-normalized embedding vector
     */
    float[] embedDocument(String title, String content);

    /**
     * Embeds a user search query.
     *
     * @param query formatted query text
     * @return L2-normalized embedding vector
     */
    float[] embedQuery(String query);
}
