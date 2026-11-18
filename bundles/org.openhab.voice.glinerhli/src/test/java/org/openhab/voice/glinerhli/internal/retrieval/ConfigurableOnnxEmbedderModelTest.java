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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ConfigurableOnnxEmbedder} with tiny ONNX models from {@code src/test/resources/embedder} (see
 * {@code generate.py}), whose output for each token is a fixed row of an embedding table: {@code [CLS]} = (1, 0, 0, 0),
 * {@code [SEP]} = (0, 1, 0, 0), {@code hello} = (0, 0, 3, 4), {@code world} = (2, 2, 0, 0).
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
class ConfigurableOnnxEmbedderModelTest {

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(ConfigurableOnnxEmbedderModelTest.class.getResource("/embedder/" + name))
                .toURI());
    }

    private static ConfigurableOnnxEmbedder load(String model, EmbeddingPooling pooling, int dimension)
            throws Exception {
        Path modelPath = resource(model);
        return new ConfigurableOnnxEmbedder(Objects.requireNonNull(modelPath.getParent()), modelPath, pooling,
                dimension, "", "");
    }

    @Test
    void feedsInt32TextInputsAndEmptyModalityFeatures() throws Exception {
        try (ConfigurableOnnxEmbedder embedder = load("model_int32.onnx", EmbeddingPooling.CLS, 4)) {
            assertArrayEquals(new float[] { 1.0f, 0.0f, 0.0f, 0.0f }, embedder.embedQuery("hello world"), 1e-6f);
        }
    }

    @Test
    void meanPoolsAllTokensIncludingSpecialTokens() throws Exception {
        try (ConfigurableOnnxEmbedder embedder = load("model_int32.onnx", EmbeddingPooling.MEAN, 4)) {
            // ([CLS] + hello + world + [SEP]) / 4 = (0.75, 0.75, 0.75, 1), then L2-normalized
            float norm = (float) Math.sqrt(3 * 0.75 * 0.75 + 1);
            assertArrayEquals(new float[] { 0.75f / norm, 0.75f / norm, 0.75f / norm, 1.0f / norm },
                    embedder.embedQuery("hello world"), 1e-6f);
        }
    }

    @Test
    void limitsDimensionToModelOutputAndTruncatesBelow() throws Exception {
        try (ConfigurableOnnxEmbedder embedder = load("model_int32.onnx", EmbeddingPooling.MEAN, 768)) {
            assertEquals(4, embedder.embedQuery("hello").length);
        }
        try (ConfigurableOnnxEmbedder embedder = load("model_int32.onnx", EmbeddingPooling.MEAN, 2)) {
            // ([CLS] + hello + [SEP]) / 3 truncated to (1/3, 1/3), then L2-normalized
            float component = (float) (1 / Math.sqrt(2));
            assertArrayEquals(new float[] { component, component }, embedder.embedQuery("hello"), 1e-6f);
        }
    }

    @Test
    void rejectsModelsWithInputsItCannotFeed() {
        IOException e = assertThrows(IOException.class,
                () -> load("model_bool_input.onnx", EmbeddingPooling.CLS, 4).close());
        assertTrue(String.valueOf(e.getMessage()).contains("use_cache"), e.getMessage());
    }
}
