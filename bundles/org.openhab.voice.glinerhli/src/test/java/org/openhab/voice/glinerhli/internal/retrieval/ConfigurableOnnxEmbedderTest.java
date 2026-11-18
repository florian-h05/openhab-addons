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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.FloatBuffer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConfigurableOnnxEmbedder} pooling ({@code CLS} and {@code MEAN}), Matryoshka truncation,
 * L2 normalization, and configurable document/query prompt prefixes.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
class ConfigurableOnnxEmbedderTest {

    @Test
    void poolsClsTokenRepresentation() {
        // Row-major hidden states [1, 2 tokens, 4]
        FloatBuffer hidden = FloatBuffer.wrap(new float[] { 1.0f, 2.0f, 3.0f, 4.0f, 9.0f, 9.0f, 9.0f, 9.0f });
        float[] cls = ConfigurableOnnxEmbedder.poolCls(hidden, 4);

        assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f, 4.0f }, cls, 1e-6f);
    }

    @Test
    void poolsAttentionMaskedMeanExcludingPaddingTokens() {
        float[][] hidden = { { 2.0f, 4.0f, 6.0f }, { 4.0f, 8.0f, 12.0f }, { 100.0f, 100.0f, 100.0f } };
        long[] mask = { 1L, 1L, 0L };

        float[] mean = ConfigurableOnnxEmbedder.poolMean(hidden, mask);
        assertArrayEquals(new float[] { 3.0f, 6.0f, 9.0f }, mean, 1e-6f);
    }

    @Test
    void poolsMeanReturnsZeroVectorWhenAllTokensAreMasked() {
        float[][] hidden = { { 2.0f, 4.0f, 6.0f }, { 4.0f, 8.0f, 12.0f } };
        long[] mask = { 0L, 0L };

        float[] mean = ConfigurableOnnxEmbedder.poolMean(hidden, mask);
        assertArrayEquals(new float[] { 0.0f, 0.0f, 0.0f }, mean, 1e-6f);
    }

    @Test
    void truncatesMatryoshkaDimensionsAndNormalizesL2() {
        float[] raw = { 3.0f, 4.0f, 10.0f, 20.0f };
        float[] truncated = ConfigurableOnnxEmbedder.truncateAndNormalize(raw, 2);

        assertEquals(2, truncated.length);
        assertEquals(0.6f, truncated[0], 1e-5f);
        assertEquals(0.8f, truncated[1], 1e-5f);

        // Dimension larger than hidden size clamps safely
        float[] clamped = ConfigurableOnnxEmbedder.truncateAndNormalize(new float[] { 0.0f, 5.0f }, 384);
        assertEquals(2, clamped.length);
        assertEquals(0.0f, clamped[0], 1e-5f);
        assertEquals(1.0f, clamped[1], 1e-5f);
    }

    @Test
    void truncateAndNormalizeHandlesZeroVectorAndFullDimension() {
        // Zero vector must not produce NaNs
        float[] zeros = ConfigurableOnnxEmbedder.truncateAndNormalize(new float[] { 0.0f, 0.0f, 0.0f }, 2);
        assertArrayEquals(new float[] { 0.0f, 0.0f }, zeros, 1e-6f);

        // Non-positive dimension (0 or negative) retains full hidden size
        float[] full = ConfigurableOnnxEmbedder.truncateAndNormalize(new float[] { 3.0f, 4.0f }, 0);
        assertArrayEquals(new float[] { 0.6f, 0.8f }, full, 1e-5f);
    }

    @Test
    void formatsDocumentAndQueryWithConfigurablePrefixes() {
        // Default Granite (no prefix)
        assertEquals("Location: Kitchen | Point: Light",
                ConfigurableOnnxEmbedder.formatDocument("", "Kitchen Light", "Location: Kitchen | Point: Light"));
        assertEquals("Location: Kitchen | Target: Light",
                ConfigurableOnnxEmbedder.formatQuery("", "Location: Kitchen | Target: Light"));

        // EmbeddingGemma 2 prefixes with {title} placeholder
        assertEquals("title: Kitchen Light | text: Location: Kitchen | Point: Light", ConfigurableOnnxEmbedder
                .formatDocument("title: {title} | text: ", "Kitchen Light", "Location: Kitchen | Point: Light"));
        assertEquals("title: none | text: Location: Kitchen | Point: Light", ConfigurableOnnxEmbedder
                .formatDocument("title: {title} | text: ", "   ", "Location: Kitchen | Point: Light"));
        assertEquals("task: search result | query: Kitchen Light",
                ConfigurableOnnxEmbedder.formatQuery("task: search result | query: ", "Kitchen Light"));

        // Prefix without {title} placeholder (e.g. E5 / BGE style)
        assertEquals("passage: Location: Kitchen | Point: Light", ConfigurableOnnxEmbedder
                .formatDocument("passage: ", "Kitchen Light", "Location: Kitchen | Point: Light"));
        assertEquals("query: Kitchen Light", ConfigurableOnnxEmbedder.formatQuery("query: ", "Kitchen Light"));
    }
}
