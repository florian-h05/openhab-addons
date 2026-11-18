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
 * Token-to-vector pooling strategy for ONNX embedding models.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public enum EmbeddingPooling {
    /**
     * Uses the hidden state vector of the leading classification ({@code [CLS]}) token
     * as the sequence embedding.
     */
    CLS,

    /**
     * Computes the element-wise average of the hidden state vectors across all
     * non-padding tokens (weighted by the attention mask).
     */
    MEAN
}
