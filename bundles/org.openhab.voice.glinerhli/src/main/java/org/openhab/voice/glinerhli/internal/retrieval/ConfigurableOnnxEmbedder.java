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

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

/**
 * Configurable ONNX text embedding engine supporting swappable encoder models (such as Granite Embedding Multilingual
 * with {@link EmbeddingPooling#CLS} pooling or EmbeddingGemma 2 with {@link EmbeddingPooling#MEAN} pooling /
 * {@code sentence_embedding} output), Matryoshka dimension truncation, L2 normalization, and configurable
 * document/query prompt prefixes.
 * <p>
 * Pipeline per text: optional prompt prefix, tokenization (with the model's special tokens such as CLS/SEP),
 * one ONNX inference with batch size 1, pooling of the token hidden states into one vector (or use of a vector the
 * graph already pooled), Matryoshka truncation, and L2 normalization, so that the dot product of two embeddings is
 * their cosine similarity (as {@link SemanticItemIndex} relies on).
 * <p>
 * Supported model inputs are the text inputs {@code input_ids} (required), {@code attention_mask},
 * {@code token_type_ids}, and {@code position_ids} as int64 or int32, plus float modality feature inputs of
 * multimodal models (e.g. {@code image_features} of EmbeddingGemma 2), which are fed empty. Models with other inputs
 * are rejected when loading.
 * <p>
 * Thread safety: {@link OrtSession#run} and the Hugging Face tokenizer may be used concurrently, which happens when
 * the item index embeds documents on its executor while an utterance is embedded for a search. All per-call state
 * (buffers, tensors) is local. Callers must not use the embedder after {@link #close()}; {@link SemanticItemIndex}
 * guards this with its embedder lock.
 *
 * @author Florian Hotze - Initial contribution
 */
@NonNullByDefault
public class ConfigurableOnnxEmbedder implements TextEmbedder, AutoCloseable {

    /**
     * Token limit per text. Item documents and queries are far shorter; the limit only bounds the cost of unexpectedly
     * long inputs (the default Granite model supports up to 32,768 tokens).
     */
    private static final int MAX_SEQUENCE_LENGTH = 512;

    private static final String INPUT_IDS = "input_ids";
    private static final String ATTENTION_MASK = "attention_mask";
    private static final String TOKEN_TYPE_IDS = "token_type_ids";
    private static final String POSITION_IDS = "position_ids";
    private static final Set<String> TEXT_INPUTS = Set.of(INPUT_IDS, ATTENTION_MASK, TOKEN_TYPE_IDS, POSITION_IDS);

    private static final String OUTPUT_SENTENCE_EMBEDDING = "sentence_embedding";
    private static final String OUTPUT_LAST_HIDDEN_STATE = "last_hidden_state";

    private static final String TITLE_PLACEHOLDER = "{title}";

    private final Logger logger = LoggerFactory.getLogger(ConfigurableOnnxEmbedder.class);
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    /** Element type (int64 or int32) of each text input the graph declares. */
    private final Map<String, OnnxJavaType> textInputTypes;
    /** Shape of the empty tensor fed to each modality feature input. */
    private final Map<String, long[]> featureInputShapes;
    private final Set<String> outputNames;
    private final EmbeddingPooling poolingStrategy;
    /** Configured dimension, limited to the dimension of the model output if known. */
    private final int embeddingDimension;
    private final String docPrefix;
    private final String queryPrefix;

    /**
     * Loads the ONNX embedding session and Hugging Face tokenizer from local model files.
     *
     * @param embeddingDir directory containing {@code tokenizer.json}
     * @param onnxModelPath path to the ONNX model file
     * @param poolingStrategy {@link EmbeddingPooling#CLS} or {@link EmbeddingPooling#MEAN}
     * @param embeddingDimension target Matryoshka dimension (limited to the dimension of the model output); only
     *            Matryoshka-trained models such as EmbeddingGemma keep their quality below their native dimension
     * @param docPrefix optional prefix for documents (supports {@code {title}} placeholder)
     * @param queryPrefix optional prefix for queries
     * @throws IOException if tokenizer loading fails or the model has unsupported inputs
     * @throws OrtException if ONNX Runtime session creation fails
     */
    public ConfigurableOnnxEmbedder(Path embeddingDir, Path onnxModelPath, EmbeddingPooling poolingStrategy,
            int embeddingDimension, String docPrefix, String queryPrefix) throws IOException, OrtException {
        this.poolingStrategy = poolingStrategy;
        this.docPrefix = docPrefix;
        this.queryPrefix = queryPrefix;

        // ONNX Runtime and DJL locate their native libraries through the thread context class loader, which in OSGi is
        // not the bundle class loader of the calling thread; restored in the finally block
        ClassLoader previousTccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ConfigurableOnnxEmbedder.class.getClassLoader());
        HuggingFaceTokenizer loadedTokenizer = null;
        OrtSession loadedSession = null;
        try {
            // The environment is a JVM-wide singleton shared with the GLiNER extractor and must not be closed here
            this.environment = OrtEnvironment.getEnvironment();
            // At most 4 threads per inference: more rarely pays off for these small models and would starve other
            // openHAB work; inter-op parallelism is pointless for a sequential encoder graph
            int intraThreads = Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4);
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(intraThreads);
                options.setInterOpNumThreads(1);
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                loadedSession = environment.createSession(onnxModelPath.toAbsolutePath().toString(), options);
            }
            // No padding, as each text is inferred on its own (batch size 1); truncation keeps the special tokens
            loadedTokenizer = HuggingFaceTokenizer.newInstance(embeddingDir.resolve("tokenizer.json"),
                    Map.of("padding", "false", "truncation", "true", "maxLength", String.valueOf(MAX_SEQUENCE_LENGTH)));

            this.session = loadedSession;
            this.tokenizer = loadedTokenizer;
            // Inputs differ between exports (e.g. with or without token_type_ids, int64 or int32), so they are read
            // from the graph once; inputs that cannot be fed are rejected now rather than failing every inference
            this.textInputTypes = new HashMap<>();
            this.featureInputShapes = new LinkedHashMap<>();
            List<String> unsupportedInputs = new ArrayList<>();
            for (Map.Entry<String, NodeInfo> input : session.getInputInfo().entrySet()) {
                classifyInput(input.getKey(), input.getValue(), unsupportedInputs);
            }
            if (!textInputTypes.containsKey(INPUT_IDS)) {
                unsupportedInputs.add(INPUT_IDS + " (missing)");
            }
            if (!unsupportedInputs.isEmpty()) {
                throw new IOException("Unsupported inputs of embedding model " + onnxModelPath.getFileName() + ": "
                        + String.join(", ", unsupportedInputs));
            }
            this.outputNames = Set.copyOf(session.getOutputNames());

            int requestedDimension = Math.max(1, embeddingDimension);
            int modelDimension = modelOutputDimension(session.getOutputInfo());
            if (modelDimension > 0 && requestedDimension > modelDimension) {
                logger.warn("Embedding dimension {} exceeds the {} dimensions of embedding model {}, using {}",
                        requestedDimension, modelDimension, onnxModelPath.getFileName(), modelDimension);
                this.embeddingDimension = modelDimension;
            } else {
                this.embeddingDimension = requestedDimension;
            }

            logger.debug(
                    "Loaded ONNX embedding model {} (text inputs={}, feature inputs={}, outputs={}, pooling={}, "
                            + "dim={})",
                    onnxModelPath.getFileName(), textInputTypes, featureInputShapes.keySet(), outputNames,
                    this.poolingStrategy, this.embeddingDimension);
        } catch (IOException | OrtException | RuntimeException e) {
            if (loadedTokenizer != null) {
                loadedTokenizer.close();
            }
            if (loadedSession != null) {
                try {
                    loadedSession.close();
                } catch (OrtException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        } finally {
            Thread.currentThread().setContextClassLoader(previousTccl);
        }
    }

    /**
     * Records a graph input as text input (int64 or int32) or as float modality feature input, or adds it to
     * {@code unsupportedInputs}.
     */
    private void classifyInput(String name, NodeInfo info, List<String> unsupportedInputs) {
        if (!(info.getInfo() instanceof TensorInfo tensorInfo)) {
            unsupportedInputs.add(name + " (not a tensor)");
        } else if (TEXT_INPUTS.contains(name)) {
            if (tensorInfo.type == OnnxJavaType.INT64 || tensorInfo.type == OnnxJavaType.INT32) {
                textInputTypes.put(name, tensorInfo.type);
            } else {
                unsupportedInputs.add(name + " (" + tensorInfo.type + ")");
            }
        } else if (tensorInfo.type == OnnxJavaType.FLOAT && tensorInfo.getShape().length > 0) {
            // Multimodal models such as EmbeddingGemma 2 take features of other modalities, e.g. image_features of
            // shape [num_image_tokens, 512]: zero tokens of each modality embed the text alone
            long[] shape = tensorInfo.getShape().clone();
            shape[0] = 0;
            for (int i = 1; i < shape.length; i++) {
                shape[i] = shape[i] > 0 ? shape[i] : 1;
            }
            featureInputShapes.put(name, shape);
        } else {
            unsupportedInputs.add(name + " (" + tensorInfo.type + ")");
        }
    }

    /**
     * Returns the last dimension of the pooled or token output, or {@code -1} if the graph does not declare it.
     */
    private static int modelOutputDimension(Map<String, NodeInfo> outputInfo) {
        NodeInfo output = outputInfo.get(OUTPUT_SENTENCE_EMBEDDING);
        if (output == null) {
            output = outputInfo.get(OUTPUT_LAST_HIDDEN_STATE);
        }
        if (output == null && !outputInfo.isEmpty()) {
            output = outputInfo.values().iterator().next();
        }
        if (output != null && output.getInfo() instanceof TensorInfo tensorInfo && tensorInfo.getShape().length > 0) {
            long last = tensorInfo.getShape()[tensorInfo.getShape().length - 1];
            return last > 0 ? (int) last : -1;
        }
        return -1;
    }

    @Override
    public float[] embedDocument(String title, String content) {
        return embedText(formatDocument(docPrefix, title, content));
    }

    @Override
    public float[] embedQuery(String query) {
        return embedText(formatQuery(queryPrefix, query));
    }

    /**
     * Tokenizes and runs ONNX inference for a formatted input string, applying configured pooling, Matryoshka
     * truncation, and L2 normalization.
     *
     * @param formattedText text already formatted with any configured prompt prefix
     * @return L2-normalized embedding vector
     */
    private float[] embedText(String formattedText) {
        // addSpecialTokens = true adds the model's CLS/SEP (here <|startoftext|> and <|return|>), which CLS pooling
        // requires at position 0; the tokenizer truncates to MAX_SEQUENCE_LENGTH while keeping them
        Encoding encoding = tokenizer.encode(formattedText, true, false);
        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        int seqLen = inputIds.length;
        if (seqLen == 0) {
            // Scores 0 against every embedding
            return new float[embeddingDimension];
        }
        // Batch size 1: [batch, sequence]
        long[] shape = new long[] { 1, seqLen };

        // Native tensors are created per call and closed in the finally block; ONNX Runtime does not free them on GC
        Map<String, OnnxTensor> feeds = new HashMap<>();
        List<OnnxTensor> createdTensors = new ArrayList<>();
        try {
            for (Map.Entry<String, OnnxJavaType> input : textInputTypes.entrySet()) {
                long[] values = switch (input.getKey()) {
                    case INPUT_IDS -> inputIds;
                    case ATTENTION_MASK -> attentionMask;
                    // BERT-style exports expect segment ids; a single text is segment 0 throughout
                    case TOKEN_TYPE_IDS -> new long[seqLen];
                    // Some exports take explicit positions 0..n-1 as input instead of deriving them in the graph
                    default -> LongStream.range(0, seqLen).toArray();
                };
                OnnxTensor tensor = createIndexTensor(values, shape, input.getValue());
                createdTensors.add(tensor);
                feeds.put(input.getKey(), tensor);
            }
            for (Map.Entry<String, long[]> input : featureInputShapes.entrySet()) {
                OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.allocate(0), input.getValue());
                createdTensors.add(tensor);
                feeds.put(input.getKey(), tensor);
            }

            // The result owns the output tensors; they are only valid within this block
            try (OrtSession.Result result = session.run(feeds)) {
                float[] pooled = extractPooledEmbedding(result, attentionMask);
                return truncateAndNormalize(pooled, embeddingDimension);
            }
        } catch (OrtException e) {
            // TextEmbedder declares no checked exceptions; the interpreter turns this into a recoverable
            // InterpretationException and the index skips the item
            throw new IllegalStateException("ONNX embedding inference failed: " + e.getMessage(), e);
        } finally {
            for (OnnxTensor tensor : createdTensors) {
                tensor.close();
            }
        }
    }

    private OnnxTensor createIndexTensor(long[] values, long[] shape, OnnxJavaType type) throws OrtException {
        if (type == OnnxJavaType.INT32) {
            int[] intValues = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                intValues[i] = Math.toIntExact(values[i]);
            }
            return OnnxTensor.createTensor(environment, IntBuffer.wrap(intValues), shape);
        }
        return OnnxTensor.createTensor(environment, LongBuffer.wrap(values), shape);
    }

    /**
     * Pools the model outputs into a single vector. The output values are owned and closed by the caller's
     * {@link OrtSession.Result} and must not be closed here: ONNX Runtime would log a warning for the double close.
     */
    private float[] extractPooledEmbedding(OrtSession.Result result, long[] attentionMask) throws OrtException {
        // When MEAN pooling is selected and the ONNX graph already emits a pooled sentence_embedding, prefer it
        if (poolingStrategy == EmbeddingPooling.MEAN && outputNames.contains(OUTPUT_SENTENCE_EMBEDDING)
                && result.get(OUTPUT_SENTENCE_EMBEDDING).orElse(null) instanceof OnnxTensor tensor) {
            return readFlatVector(tensor);
        }

        // Otherwise use the token hidden states; exports without a last_hidden_state output are assumed to emit them
        // (or an already pooled vector) as their first output
        OnnxValue hiddenValue = outputNames.contains(OUTPUT_LAST_HIDDEN_STATE)
                ? result.get(OUTPUT_LAST_HIDDEN_STATE).orElse(null)
                : result.get(0);
        if (!(hiddenValue instanceof OnnxTensor hiddenTensor)) {
            throw new OrtException("Expected OnnxTensor output from embedding model");
        }

        long[] dims = hiddenTensor.getInfo().getShape();
        if (dims.length == 2) {
            // Already pooled [1, hiddenSize]
            return readFlatVector(hiddenTensor);
        }
        if (dims.length != 3) {
            throw new OrtException("Unsupported embedding output rank: " + dims.length);
        }

        // [batch = 1, sequence, hidden], row-major: token t occupies floats [t * hidden, (t + 1) * hidden)
        int seqLen = (int) dims[1];
        int hiddenSize = (int) dims[2];
        FloatBuffer buffer = hiddenTensor.getFloatBuffer();
        if (poolingStrategy == EmbeddingPooling.CLS) {
            return poolCls(buffer, hiddenSize);
        }
        float[][] tokenEmbeddings = new float[seqLen][hiddenSize];
        for (int t = 0; t < seqLen; t++) {
            buffer.get(tokenEmbeddings[t]);
        }
        return poolMean(tokenEmbeddings, attentionMask);
    }

    private static float[] readFlatVector(OnnxTensor tensor) {
        FloatBuffer buffer = tensor.getFloatBuffer();
        float[] vector = new float[buffer.remaining()];
        buffer.get(vector);
        return vector;
    }

    /**
     * Formats an indexed document using the configured document prefix, substituting {@code {title}} if present.
     */
    static String formatDocument(String docPrefix, String title, String content) {
        if (docPrefix.isEmpty()) {
            return content;
        }
        // EmbeddingGemma's document prompt expects "title: none" when there is no title
        String effectiveTitle = title.isBlank() ? "none" : title.trim();
        return docPrefix.replace(TITLE_PLACEHOLDER, effectiveTitle) + content;
    }

    /**
     * Formats a search query using the configured query prefix.
     */
    static String formatQuery(String queryPrefix, String query) {
        return queryPrefix.isEmpty() ? query : queryPrefix + query;
    }

    /**
     * Extracts the CLS token representation, i.e. the first {@code hiddenSize} values of the row-major hidden states
     * {@code [1, sequence, hiddenSize]}. Models such as Granite are trained to summarize the whole text in the hidden
     * state of this first special token, so the other tokens need not be read.
     */
    static float[] poolCls(FloatBuffer hiddenStates, int hiddenSize) {
        float[] cls = new float[Math.min(hiddenSize, hiddenStates.remaining())];
        hiddenStates.get(cls);
        return cls;
    }

    /**
     * Computes attention-masked mean pooling over token embeddings {@code [seqLen][hiddenSize]}. Without padding the
     * mask is all ones; it is still honored so padded inputs would not dilute the mean.
     */
    static float[] poolMean(float[][] tokenEmbeddings, long[] attentionMask) {
        if (tokenEmbeddings.length == 0) {
            return new float[0];
        }
        int hiddenSize = tokenEmbeddings[0].length;
        float[] sum = new float[hiddenSize];
        int activeTokens = 0;
        int limit = Math.min(tokenEmbeddings.length, attentionMask.length);
        for (int t = 0; t < limit; t++) {
            if (attentionMask[t] != 0L) {
                activeTokens++;
                float[] row = tokenEmbeddings[t];
                for (int d = 0; d < hiddenSize; d++) {
                    sum[d] += row[d];
                }
            }
        }
        if (activeTokens > 0) {
            for (int d = 0; d < hiddenSize; d++) {
                sum[d] /= activeTokens;
            }
        }
        return sum;
    }

    /**
     * Applies Matryoshka truncation to {@code Math.min(targetDimension, vector.length)} and L2-normalizes the result.
     * Normalizing after truncation keeps unit length, so dot products remain cosine similarities; a zero vector is
     * left unchanged instead of dividing by zero.
     */
    static float[] truncateAndNormalize(float[] vector, int targetDimension) {
        int dim = Math.clamp(targetDimension, 1, vector.length);
        float[] truncated = new float[dim];
        double normSq = 0.0;
        for (int i = 0; i < dim; i++) {
            float val = vector[i];
            truncated[i] = val;
            normSq += (double) val * val;
        }
        double norm = Math.sqrt(normSq);
        if (norm > 1e-12) {
            for (int i = 0; i < dim; i++) {
                truncated[i] = (float) (truncated[i] / norm);
            }
        }
        return truncated;
    }

    /**
     * Releases the native tokenizer and session. The shared {@link OrtEnvironment} stays open.
     */
    @Override
    public void close() {
        try {
            tokenizer.close();
        } catch (Exception e) {
            logger.debug("Error closing tokenizer: {}", e.getMessage());
        }
        try {
            session.close();
        } catch (OrtException e) {
            logger.debug("Error closing ONNX embedding session: {}", e.getMessage());
        }
    }
}
