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
 * Stage 2: ONNX text embedding generation and live semantic item retrieval.
 * <p>
 * {@link SemanticItemIndex} tracks openHAB's {@code ItemRegistry} and maintains an in-memory vector index over
 * accessible items, serializing their Semantic Model hierarchy, tags, and description options via
 * {@link ConfigurableOnnxEmbedder}. Queries built from the extracted Stage 1 slots are ranked by vector similarity,
 * filtered by access permissions and intent compatibility, and collapsed to a common parent group when an all-scope
 * quantifier applies.
 *
 * @author Florian Hotze - Initial contribution
 */
package org.openhab.voice.glinerhli.internal.retrieval;
