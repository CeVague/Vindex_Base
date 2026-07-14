"""Exporte des modèles EdgeFace (Idiap) en ONNX fp32 pour Vindex.

Chaque modèle : entrée `input` [batch, 3, 112, 112], sortie `embedding`
[batch, 512], normalisation appliquée côté app (mean/std [0.5]).

Usage (via un Python avec torch/onnx/onnxruntime/onnxscript/timm) :
    python export_edgeface.py                 # tout le set connu
    python export_edgeface.py edgeface_xxs    # une ou plusieurs variantes

Pièges gérés : torch.hub trust_repo ; exporteur legacy (dynamo=False) plus
fiable pour ces CNN ; les checkpoints doivent être pré-téléchargés dans le
cache torch.hub si le Python n'a pas de magasin de certificats CA (SSL).
"""
import os
import sys

import onnx
import onnxruntime as ort
import torch

# entrypoint torch.hub -> nom de fichier ONNX de sortie
KNOWN = {
    "edgeface_base": "edgeface_base.onnx",
    "edgeface_s_gamma_05": "edgeface_s.onnx",
    "edgeface_xs_gamma_06": "edgeface_xs.onnx",
    "edgeface_xxs": "edgeface_xxs.onnx",
}


def export_one(entry: str, out: str) -> None:
    model = torch.hub.load(
        "otroshi/edgeface", entry, source="github", pretrained=True, trust_repo=True
    ).eval()
    dummy = torch.randn(1, 3, 112, 112)
    kw = dict(
        input_names=["input"],
        output_names=["embedding"],
        dynamic_axes={"input": {0: "batch"}, "embedding": {0: "batch"}},
        opset_version=17,
    )
    try:
        torch.onnx.export(model, dummy, out, dynamo=False, **kw)
    except Exception as e:  # legacy exporter absent -> nouvel exporteur
        print("legacy export failed:", repr(e), "-> dynamo", flush=True)
        torch.onnx.export(model, dummy, out, dynamo=True, **kw)

    onnx.checker.check_model(out)
    sess = ort.InferenceSession(out, providers=["CPUExecutionProvider"])
    o = sess.get_outputs()[0]
    print(
        f"RESULT {entry} -> {out} dim={o.shape[-1]} bytes={os.path.getsize(out)}",
        flush=True,
    )


def main() -> None:
    targets = sys.argv[1:] or list(KNOWN)
    for entry in targets:
        export_one(entry, KNOWN[entry])
    print("BATCH_DONE", flush=True)


if __name__ == "__main__":
    main()
