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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.core.OpenHAB;
import org.openhab.voice.glinerhli.internal.GlinerHliConfiguration;
import org.openhab.voice.glinerhli.internal.GlinerHliConstants;
import org.openhab.voice.glinerhli.internal.extraction.GlinerMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Resolves local model storage paths and optionally downloads missing GLiNER and ONNX embedding model artifacts from
 * Hugging Face using atomic file moves (never symlinks).
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class ModelManager {

    private static final String USERDATA_PLACEHOLDER = "${openhab.userdata}";
    private static final String HF_BASE_URL = "https://huggingface.co/";
    private static final Pattern SAFE_REPO_ID = Pattern.compile("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$");
    private static final Pattern SAFE_VARIANT = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HEAD_TIMEOUT = Duration.ofSeconds(20);
    /** Aborts a download once no data arrives for this long, without limiting the total download time. */
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofSeconds(60);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Logger logger = LoggerFactory.getLogger(ModelManager.class);
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration idleTimeout;

    /**
     * @param httpClient started HTTP client, e.g. openHAB's common client
     */
    public ModelManager(HttpClient httpClient) {
        this(httpClient, HF_BASE_URL, DEFAULT_IDLE_TIMEOUT);
    }

    ModelManager(HttpClient httpClient, String baseUrl, Duration idleTimeout) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.idleTimeout = idleTimeout;
    }

    /**
     * Resolved paths to all local model directories and files required to initialize inference engines.
     *
     * @param baseDir root directory containing all model subdirectories
     * @param glinerDir directory containing GLiNER model configuration, tokenizer, and ONNX files
     * @param embeddingDir directory containing embedding model configuration, tokenizer, and ONNX files
     * @param embeddingModelFile path to the primary ONNX embedding model file
     */
    public record ModelPaths(Path baseDir, Path glinerDir, Path embeddingDir, Path embeddingModelFile) {
    }

    /**
     * Resolves the local folder of a Hugging Face repository below the model base path. Each repository has its own
     * folder (named like in the Hugging Face cache, e.g. {@code owner--name}), as files present locally are not
     * downloaded again and must not be mixed between repositories (e.g. a tokenizer of another model).
     *
     * @param baseDir model base path
     * @param modelType {@link GlinerHliConstants#GLINER_SUBDIR} or {@link GlinerHliConstants#EMBEDDING_SUBDIR}
     * @param repoId validated Hugging Face repository identifier ({@code owner/name})
     * @return local repository folder
     */
    public static Path resolveRepositoryDir(Path baseDir, String modelType, String repoId) {
        return baseDir.resolve(modelType).resolve(repoId.trim().replace("/", "--")).normalize();
    }

    /**
     * Resolves {@code modelBasePath}, expanding {@code ${openhab.userdata}} via {@link OpenHAB#getUserDataFolder()}.
     *
     * @param configuredBasePath configured path string
     * @return normalized absolute path
     */
    public Path resolveModelBasePath(String configuredBasePath) {
        String raw = configuredBasePath.isBlank() ? USERDATA_PLACEHOLDER + "/gliner-hli" : configuredBasePath.trim();
        String expanded = raw.replace(USERDATA_PLACEHOLDER, OpenHAB.getUserDataFolder());
        return Path.of(expanded).toAbsolutePath().normalize();
    }

    /**
     * Ensures all required GLiNER and ONNX embedding model files exist locally, downloading missing artifacts from
     * Hugging Face if {@code autoDownloadModels} is enabled.
     *
     * @param config active add-on configuration
     * @return resolved local model paths
     * @throws IOException if downloads fail or required files are missing when auto-download is disabled
     * @throws InterruptedException if interrupted while downloading
     */
    public ModelPaths ensureModelsAvailable(GlinerHliConfiguration config) throws IOException, InterruptedException {
        validateConfiguration(config);

        Path baseDir = resolveModelBasePath(config.modelBasePath);
        Path glinerDir = resolveRepositoryDir(baseDir, GlinerHliConstants.GLINER_SUBDIR, config.glinerRepoId);
        Path embeddingDir = resolveRepositoryDir(baseDir, GlinerHliConstants.EMBEDDING_SUBDIR, config.embeddingRepoId);
        Files.createDirectories(glinerDir);
        Files.createDirectories(embeddingDir);

        ensureGlinerModels(config, glinerDir);
        Path embeddingModelPath = ensureEmbeddingModels(config, embeddingDir);

        return new ModelPaths(baseDir, glinerDir, embeddingDir, embeddingModelPath);
    }

    /**
     * Ensures all required GLiNER configuration, tokenizer, and variant-specific ONNX model files are present in
     * {@code glinerDir}.
     *
     * @param config active add-on configuration
     * @param glinerDir local target directory for GLiNER model artifacts
     * @throws IOException if a required file is missing and auto-download is disabled, or if downloading/parsing fails
     * @throws InterruptedException if interrupted while downloading
     */
    private void ensureGlinerModels(GlinerHliConfiguration config, Path glinerDir)
            throws IOException, InterruptedException {
        Path glinerConfigPath = resolveSafeChild(glinerDir, "gliner2_config.json");
        ensureFilePresent(config.glinerRepoId, "gliner2_config.json", glinerConfigPath, config.autoDownloadModels);
        ensureFilePresent(config.glinerRepoId, "config.json", resolveSafeChild(glinerDir, "config.json"),
                config.autoDownloadModels);
        ensureFilePresent(config.glinerRepoId, "tokenizer.json", resolveSafeChild(glinerDir, "tokenizer.json"),
                config.autoDownloadModels);
        ensureFilePresent(config.glinerRepoId, "tokenizer_config.json",
                resolveSafeChild(glinerDir, "tokenizer_config.json"), config.autoDownloadModels);

        boolean dualHead = config.glinerMode == GlinerMode.GLINER2_DUAL_HEAD;
        List<String> onnxRelPaths = resolveGlinerOnnxRelPaths(glinerConfigPath, config.glinerVariant, dualHead);
        for (String relPath : onnxRelPaths) {
            ensureFilePresent(config.glinerRepoId, relPath, resolveSafeChild(glinerDir, relPath),
                    config.autoDownloadModels);
        }
    }

    /**
     * Parses {@code gliner2_config.json} to determine the relative paths of the ONNX files required for the given
     * variant and inference mode.
     *
     * @param glinerConfigPath path to the local {@code gliner2_config.json} file
     * @param variant model variant key under {@code onnx_files} (e.g., {@code fp32}, {@code int8})
     * @param dualHead whether the dual-head classifier ONNX file should be included in addition to encoder and boundary
     * @return ordered list of normalized relative ONNX file paths
     * @throws IOException if the configuration file cannot be read, the variant is missing, or any path is invalid
     */
    static List<String> resolveGlinerOnnxRelPaths(Path glinerConfigPath, String variant, boolean dualHead)
            throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(glinerConfigPath.toFile());
        JsonNode variantNode = root.path("onnx_files").path(variant);
        if (!variantNode.isObject()) {
            throw new IOException("Variant '" + variant + "' not found in " + glinerConfigPath);
        }
        String encoderRel = normalizeRelativePath(variantNode.path("encoder").asText(""));
        String boundaryRel = normalizeRelativePath(variantNode.path("boundary").asText(""));
        List<String> files = new ArrayList<>();
        files.add(encoderRel);
        files.add(boundaryRel);
        if (dualHead) {
            String classifierRel = normalizeRelativePath(variantNode.path("classifier").asText(""));
            files.add(classifierRel);
        }
        return files;
    }

    /**
     * Ensures all required embedding model configuration, tokenizer, ONNX model, and optional external data shard
     * files are present in {@code embeddingDir}.
     *
     * @param config active add-on configuration
     * @param embeddingDir local target directory for embedding model artifacts
     * @return resolved path to the primary ONNX embedding model file
     * @throws IOException if a required file is missing and auto-download is disabled, or if downloading fails
     * @throws InterruptedException if interrupted while downloading or probing remote files
     */
    private Path ensureEmbeddingModels(GlinerHliConfiguration config, Path embeddingDir)
            throws IOException, InterruptedException {
        Path configJsonPath = resolveSafeChild(embeddingDir, "config.json");
        ensureFilePresent(config.embeddingRepoId, "config.json", configJsonPath, config.autoDownloadModels);
        ensureFilePresent(config.embeddingRepoId, "tokenizer.json", resolveSafeChild(embeddingDir, "tokenizer.json"),
                config.autoDownloadModels);
        ensureFilePresent(config.embeddingRepoId, "tokenizer_config.json",
                resolveSafeChild(embeddingDir, "tokenizer_config.json"), config.autoDownloadModels);

        String normalizedModelRelPath = normalizeRelativePath(config.embeddingModelRelPath);
        Path modelPath = resolveSafeChild(embeddingDir, normalizedModelRelPath);
        ensureFilePresent(config.embeddingRepoId, normalizedModelRelPath, modelPath, config.autoDownloadModels);

        String externalDataRelPath = normalizedModelRelPath + "_data";
        Path externalDataPath = resolveSafeChild(embeddingDir, externalDataRelPath);
        if (!Files.isRegularFile(externalDataPath)) {
            boolean needsExternalData = declaresExternalData(configJsonPath, normalizedModelRelPath);
            if (!needsExternalData && config.autoDownloadModels) {
                needsExternalData = remoteFileExists(config.embeddingRepoId, externalDataRelPath);
            }
            if (needsExternalData) {
                ensureFilePresent(config.embeddingRepoId, externalDataRelPath, externalDataPath,
                        config.autoDownloadModels);
            }
        }

        return modelPath;
    }

    /**
     * Verifies that {@code targetPath} exists as a non-empty regular file, downloading it from Hugging Face if missing
     * and {@code autoDownload} is enabled.
     *
     * @param repoId Hugging Face repository identifier ({@code owner/name})
     * @param relPath repository-relative path of the artifact
     * @param targetPath destination path on the local filesystem
     * @param autoDownload whether missing files may be downloaded automatically
     * @throws IOException if the file is missing with {@code autoDownload} disabled, or if the download fails
     * @throws InterruptedException if interrupted while downloading
     */
    private void ensureFilePresent(String repoId, String relPath, Path targetPath, boolean autoDownload)
            throws IOException, InterruptedException {
        if (Files.isRegularFile(targetPath) && Files.size(targetPath) > 0) {
            return;
        }
        if (!autoDownload) {
            throw new IOException("Required model file is missing and autoDownloadModels is disabled: " + targetPath);
        }
        downloadAtomically(repoId, relPath, targetPath);
    }

    /**
     * Downloads a single artifact from Hugging Face into a temporary sibling file and atomically moves it to
     * {@code targetPath} upon completion.
     *
     * @param repoId Hugging Face repository identifier ({@code owner/name})
     * @param relPath repository-relative path of the artifact
     * @param targetPath destination path on the local filesystem
     * @throws IOException if the HTTP request fails, returns a non-2xx status, yields an empty file, or fails to move
     * @throws InterruptedException if the thread is interrupted before or during the download
     */
    private void downloadAtomically(String repoId, String relPath, Path targetPath)
            throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Model download interrupted before starting " + relPath);
        }

        Path parent = targetPath.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("Invalid model file path: " + targetPath);
        }
        Files.createDirectories(parent);

        URI uri = buildResolveUri(repoId, relPath);
        logger.info("Downloading model artifact {} -> {}", uri, targetPath);

        // Unlike a total timeout, the idle timeout aborts stalled transfers without limiting large downloads
        Request request = httpClient.newRequest(uri).method(HttpMethod.GET).followRedirects(true)
                .idleTimeout(idleTimeout.toMillis(), TimeUnit.MILLISECONDS);
        InputStreamResponseListener listener = new InputStreamResponseListener();
        Path tmpFile = Files.createTempFile(parent, targetPath.getFileName().toString() + ".", ".tmp");
        try {
            request.send(listener);
            Response response = listener.get(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            int status = response.getStatus();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " while downloading " + uri);
            }
            // Reading blocks at most for the idle timeout and responds to interruption
            try (InputStream in = listener.getInputStream()) {
                Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(tmpFile) == 0) {
                throw new IOException("Downloaded empty file from " + uri);
            }
            moveAtomically(tmpFile, targetPath);
            logger.debug("Finished downloading {} ({} bytes)", targetPath.getFileName(), Files.size(targetPath));
        } catch (TimeoutException e) {
            request.abort(e);
            throw new IOException("Timed out waiting for a response from " + uri, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("Failed to download " + uri + ": " + cause.getMessage(), cause);
        } catch (IOException | InterruptedException e) {
            request.abort(e);
            throw e;
        } finally {
            deleteQuietly(tmpFile);
        }
    }

    /**
     * Sends an HTTP {@code HEAD} request to check whether an artifact exists in the specified Hugging Face repository.
     *
     * @param repoId Hugging Face repository identifier ({@code owner/name})
     * @param relPath repository-relative path of the artifact
     * @return {@code true} if the server responds with a 2xx status code; {@code false} otherwise or on I/O error
     * @throws InterruptedException if interrupted while awaiting the response
     */
    private boolean remoteFileExists(String repoId, String relPath) throws InterruptedException {
        URI uri = buildResolveUri(repoId, relPath);
        try {
            ContentResponse response = httpClient.newRequest(uri).method(HttpMethod.HEAD).followRedirects(true)
                    .timeout(HEAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).send();
            return response.getStatus() >= 200 && response.getStatus() < 300;
        } catch (TimeoutException | ExecutionException e) {
            logger.debug("HEAD probe failed for {}: {}", uri, e.getMessage());
            return false;
        }
    }

    /**
     * Inspects {@code transformers.js_config.use_external_data_format} in a model's {@code config.json} to determine
     * whether the specified ONNX model relies on an external {@code _data} companion file.
     *
     * @param configJsonPath path to the local {@code config.json} file
     * @param normalizedModelRelPath normalized repository-relative path of the ONNX model
     * @return {@code true} if external data format is explicitly declared for the model; {@code false} otherwise
     */
    static boolean declaresExternalData(Path configJsonPath, String normalizedModelRelPath) {
        if (!Files.isRegularFile(configJsonPath)) {
            return false;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(configJsonPath.toFile());
            JsonNode externalMap = root.path("transformers.js_config").path("use_external_data_format");
            if (externalMap.isObject()) {
                String fileName = Path.of(normalizedModelRelPath).getFileName().toString();
                JsonNode byName = externalMap.get(fileName);
                JsonNode byRelPath = externalMap.get(normalizedModelRelPath);
                JsonNode entry = byName != null ? byName : byRelPath;
                if (entry != null) {
                    return (entry.isBoolean() && entry.asBoolean()) || (entry.isNumber() && entry.asInt() > 0);
                }
            }
        } catch (IOException e) {
            // Optional hint only; fallback HEAD probe still detects companion shards
        }
        return false;
    }

    /**
     * Builds the Hugging Face {@code resolve/main} download {@link URI} for a repository artifact.
     *
     * @param repoId Hugging Face repository identifier ({@code owner/name})
     * @param relPath normalized repository-relative file path
     * @return resolved artifact {@link URI}
     */
    private URI buildResolveUri(String repoId, String relPath) {
        return URI.create(baseUrl + repoId.trim() + "/resolve/main/" + relPath);
    }

    /**
     * Validates repository IDs, variant names, and relative model paths in {@code config} to prevent malformed URLs or
     * path traversal.
     *
     * @param config add-on configuration to validate
     * @throws IOException if any configured repository ID, variant, or relative model path is invalid
     */
    private static void validateConfiguration(GlinerHliConfiguration config) throws IOException {
        if (!SAFE_REPO_ID.matcher(config.glinerRepoId.trim()).matches()) {
            throw new IOException("Invalid glinerRepoId: " + config.glinerRepoId);
        }
        if (!SAFE_REPO_ID.matcher(config.embeddingRepoId.trim()).matches()) {
            throw new IOException("Invalid embeddingRepoId: " + config.embeddingRepoId);
        }
        if (!SAFE_VARIANT.matcher(config.glinerVariant.trim()).matches()) {
            throw new IOException("Invalid glinerVariant: " + config.glinerVariant);
        }
        normalizeRelativePath(config.embeddingModelRelPath);
    }

    static String normalizeRelativePath(String rawPath) throws IOException {
        String trimmed = rawPath.trim().replace('\\', '/');
        if (trimmed.isEmpty() || trimmed.startsWith("/") || trimmed.contains("..")) {
            throw new IOException("Invalid relative model path: " + rawPath);
        }
        return trimmed;
    }

    private static Path resolveSafeChild(Path baseDir, String relativePath) throws IOException {
        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IOException("Resolved path escapes model directory: " + relativePath);
        }
        return resolved;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
