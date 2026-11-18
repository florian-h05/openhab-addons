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
package org.openhab.voice.glinerhli.internal.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openhab.voice.glinerhli.internal.GlinerHliConfiguration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Tests for {@link ModelManager} downloads against a local HTTP server.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
class ModelManagerTest {

    private static final String GLINER_CONFIG = """
            {"special_tokens": {}, "onnx_files": {"fp32": {"encoder": "onnx/encoder.onnx",
            "boundary": "onnx/boundary.onnx", "classifier": "onnx/classifier.onnx"}}}""";
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(1);

    private final Map<String, String> files = new ConcurrentHashMap<>();
    private final CountDownLatch releaseStalledResponses = new CountDownLatch(1);
    private @NonNullByDefault({}) HttpServer server;
    private @NonNullByDefault({}) ExecutorService serverExecutor;
    private @NonNullByDefault({}) HttpClient httpClient;
    private @NonNullByDefault({}) ModelManager modelManager;
    private @TempDir @NonNullByDefault({}) Path modelDir;

    @BeforeEach
    void setUp() throws Exception {
        files.put("/test/gliner/resolve/main/gliner2_config.json", GLINER_CONFIG);
        for (String file : new String[] { "config.json", "tokenizer.json", "tokenizer_config.json", "onnx/encoder.onnx",
                "onnx/boundary.onnx", "onnx/classifier.onnx" }) {
            files.putIfAbsent("/test/gliner/resolve/main/" + file, "gliner " + file);
        }
        for (String file : new String[] { "config.json", "tokenizer.json", "tokenizer_config.json",
                "onnx/model.onnx" }) {
            files.put("/test/embedding/resolve/main/" + file, "embedding " + file);
        }

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/", this::handle);
        server.start();

        httpClient = new HttpClient();
        httpClient.start();
        modelManager = new ModelManager(httpClient, "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                IDLE_TIMEOUT);
    }

    @AfterEach
    void tearDown() throws Exception {
        releaseStalledResponses.countDown();
        httpClient.stop();
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = files.get(exchange.getRequestURI().getPath());
        if (body == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        boolean stall = body.startsWith("STALL");
        exchange.sendResponseHeaders(200, stall ? bytes.length + 1000 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
            out.flush();
            if (stall) {
                // Keep the connection open without sending the announced remainder
                releaseStalledResponses.await(30, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private GlinerHliConfiguration config(boolean autoDownload) {
        GlinerHliConfiguration config = new GlinerHliConfiguration();
        config.modelBasePath = modelDir.toString();
        config.glinerRepoId = "test/gliner";
        config.embeddingRepoId = "test/embedding";
        config.embeddingModelRelPath = "onnx/model.onnx";
        config.glinerVariant = "fp32";
        config.autoDownloadModels = autoDownload;
        return config;
    }

    private long countTempFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(modelDir)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".tmp")).count();
        }
    }

    @Test
    void downloadsAllModelFiles() throws Exception {
        ModelManager.ModelPaths paths = modelManager.ensureModelsAvailable(config(true));

        assertEquals("gliner onnx/encoder.onnx",
                Files.readString(paths.glinerDir().resolve("onnx/encoder.onnx"), StandardCharsets.UTF_8));
        assertEquals("embedding onnx/model.onnx", Files.readString(paths.embeddingModelFile(), StandardCharsets.UTF_8));
        assertEquals(0, countTempFiles());
    }

    @Test
    void abortsStalledDownloadAfterIdleTimeout() throws Exception {
        files.put("/test/gliner/resolve/main/onnx/encoder.onnx", "STALL partial content");

        assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> assertThrows(IOException.class, () -> modelManager.ensureModelsAvailable(config(true))));
        assertFalse(Files.exists(modelDir.resolve("gliner/test--gliner/onnx/encoder.onnx")));
        assertEquals(0, countTempFiles());
    }

    @Test
    void keepsFilesOfRepositoriesApart() throws Exception {
        ModelManager.ModelPaths first = modelManager.ensureModelsAvailable(config(true));
        files.put("/test/other/resolve/main/gliner2_config.json", GLINER_CONFIG);
        for (String file : new String[] { "config.json", "tokenizer.json", "tokenizer_config.json", "onnx/encoder.onnx",
                "onnx/boundary.onnx", "onnx/classifier.onnx" }) {
            files.put("/test/other/resolve/main/" + file, "other " + file);
        }
        GlinerHliConfiguration otherConfig = config(true);
        otherConfig.glinerRepoId = "test/other";

        ModelManager.ModelPaths second = modelManager.ensureModelsAvailable(otherConfig);

        // Files of the previous repository must not be reused, e.g. a tokenizer of another model
        assertNotEquals(first.glinerDir(), second.glinerDir());
        assertEquals("other tokenizer.json",
                Files.readString(second.glinerDir().resolve("tokenizer.json"), StandardCharsets.UTF_8));
        assertEquals(first.embeddingDir(), second.embeddingDir());
    }

    @Test
    void failsOnHttpError() {
        files.remove("/test/gliner/resolve/main/tokenizer.json");

        IOException e = assertThrows(IOException.class, () -> modelManager.ensureModelsAvailable(config(true)));
        assertTrue(String.valueOf(e.getMessage()).contains("HTTP 404"), e.getMessage());
    }

    @Test
    void failsForMissingFilesWithoutAutoDownload() {
        IOException e = assertThrows(IOException.class, () -> modelManager.ensureModelsAvailable(config(false)));
        assertTrue(String.valueOf(e.getMessage()).contains("autoDownloadModels is disabled"), e.getMessage());
    }
}
