"""Generates the tiny ONNX models and tokenizer used by ConfigurableOnnxEmbedderModelTest.

Requires: pip install onnx tokenizers
Run from this directory: python generate.py
"""
import numpy as np
import onnx
from onnx import TensorProto, helper
from tokenizers import Tokenizer, models, pre_tokenizers, processors

VOCAB = {"[CLS]": 0, "[SEP]": 1, "hello": 2, "world": 3, "[UNK]": 4}
# One 4-dimensional row per token id; the model output is the row of each input token
TABLE = np.array([[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 3, 4], [2, 2, 0, 0], [0, 0, 0, 1]], dtype=np.float32)

tokenizer = Tokenizer(models.WordLevel(vocab=VOCAB, unk_token="[UNK]"))
tokenizer.pre_tokenizer = pre_tokenizers.WhitespaceSplit()
tokenizer.post_processor = processors.TemplateProcessing(single="[CLS] $A [SEP]",
                                                         special_tokens=[("[CLS]", 0), ("[SEP]", 1)])
tokenizer.save("tokenizer.json")


def save(name, id_type, extra_inputs, nodes_for_extra):
    table = helper.make_tensor("table", TensorProto.FLOAT, TABLE.shape, TABLE.flatten())
    inputs = [helper.make_tensor_value_info("input_ids", id_type, ["batch", "sequence"]),
              helper.make_tensor_value_info("attention_mask", id_type, ["batch", "sequence"])] + extra_inputs
    output = helper.make_tensor_value_info("last_hidden_state", TensorProto.FLOAT, ["batch", "sequence", 4])
    nodes = [helper.make_node("Gather", ["table", "input_ids"], ["gathered"]),
             # Zero the hidden states of masked tokens, so the mask input is used
             helper.make_node("Cast", ["attention_mask"], ["mask_float"], to=TensorProto.FLOAT),
             helper.make_node("Unsqueeze", ["mask_float", "last_axis"], ["mask_expanded"]),
             helper.make_node("Mul", ["gathered", "mask_expanded"], ["masked"])] + nodes_for_extra
    if not nodes_for_extra:
        nodes.append(helper.make_node("Identity", ["masked"], ["last_hidden_state"]))
    axis = helper.make_tensor("last_axis", TensorProto.INT64, [1], [-1])
    graph = helper.make_graph(nodes, name, inputs, [output], initializer=[table, axis])
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)])
    model.ir_version = 8
    onnx.checker.check_model(model)
    onnx.save(model, name)


# int32 text inputs and a float modality feature input as in EmbeddingGemma 2 ([num_image_tokens, 512]); the sum of
# the (empty) features is added, so the input is used
save("model_int32.onnx", TensorProto.INT32,
     [helper.make_tensor_value_info("image_features", TensorProto.FLOAT, ["num_image_tokens", 512])],
     [helper.make_node("ReduceSum", ["image_features"], ["feature_sum"], keepdims=0),
      helper.make_node("Add", ["masked", "feature_sum"], ["last_hidden_state"])])
# An input of a type the embedder cannot feed
save("model_bool_input.onnx", TensorProto.INT64,
     [helper.make_tensor_value_info("use_cache", TensorProto.BOOL, [1])], [])
