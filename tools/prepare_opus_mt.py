#!/usr/bin/env python3
"""Assemble un dossier d'import Vindex pour un modèle de traduction OPUS-MT.

Prend un export ONNX existant (dépôts HuggingFace « Xenova/opus-mt-XX-YY »,
faits pour Transformers.js : encodeur + décodeur séparés + tokenizer.json) et
produit le dossier à pousser dans Download/ puis importer via l'UI :

    encoder_model.onnx   (rôle translation_encoder)
    decoder_model.onnx   (rôle translation_decoder)
    tokenizer.json       (rôle tokenizer_json — unigram SentencePiece)
    config.json          (schéma Vindex, ids lus dans le config HF)

Usage (toolchain uv confinée, cf. recette du projet) :

    uv run --with huggingface_hub python tools/prepare_opus_mt.py \
        --repo Xenova/opus-mt-fr-en --out OPUS-MT-fr-en-import

ou depuis un snapshot déjà téléchargé :

    python tools/prepare_opus_mt.py --source ./opus-mt-fr-en --out OPUS-MT-fr-en-import

Choix de variante (--variant) : fp32 (défaut), fp16 (moitié du poids), ou int8
(*_quantized, ~4× plus léger — la version compacte). ⚠ int8 : la leçon SigLIP
(u8 écrase les embeddings) ne s'applique pas mécaniquement à un traducteur
seq2seq (l'argmax peut survivre à la quantification), mais la qualité est À
VALIDER par l'étape 0 du guide avant de s'y fier. Les décodeurs *_merged /
*_with_past restent exclus : Vindex décode sans cache KV, exprès (entrées
stables d'un export à l'autre).
"""

import argparse
import json
import re
import shutil
import sys
from pathlib import Path

ROLES = {
    "translation_encoder": "encoder_model.onnx",
    "translation_decoder": "decoder_model.onnx",
    "tokenizer_json": "tokenizer.json",
}

# Suffixe de fichier des variantes dans les exports Xenova.
VARIANTS = {"fp32": "", "fp16": "_fp16", "int8": "_quantized"}


def fail(message: str) -> None:
    print(f"ERREUR : {message}", file=sys.stderr)
    sys.exit(1)


def resolve_snapshot(args: argparse.Namespace) -> Path:
    if args.source:
        source = Path(args.source)
        if not source.is_dir():
            fail(f"--source {source} n'est pas un dossier")
        return source
    try:
        from huggingface_hub import snapshot_download
    except ImportError:
        fail("huggingface_hub absent — utiliser `uv run --with huggingface_hub …` ou --source")
    print(f"Téléchargement de {args.repo}…")
    # Seulement les fichiers utiles : le dossier onnx/ complet contient toutes
    # les variantes (quantized, merged, with_past) — inutiles et lourdes.
    suffix = VARIANTS[args.variant]
    wanted = ["config.json", "tokenizer.json"]
    for stem in ("encoder_model", "decoder_model"):
        wanted.append(f"onnx/{stem}{suffix}.onnx")
    return Path(snapshot_download(args.repo, allow_patterns=wanted))


def pick_onnx(snapshot: Path, stem: str, variant: str) -> Path:
    """encoder_model / decoder_model dans la variante demandée, jamais merged."""
    onnx_dir = snapshot / "onnx" if (snapshot / "onnx").is_dir() else snapshot
    wanted = f"{stem}{VARIANTS[variant]}.onnx"
    candidate = onnx_dir / wanted
    if not candidate.is_file():
        fail(f"{wanted} introuvable dans {onnx_dir} — essayer une autre --variant")
    return candidate


def parse_pair(repo: str | None, source: str | None) -> tuple[str, str] | None:
    """Langues déduites du nom « opus-mt-fr-en » (dépôt ou dossier)."""
    for name in (repo, source and Path(source).name):
        if not name:
            continue
        match = re.search(r"opus-mt-([a-z]{2,3})-([a-z]{2,3})", name)
        if match:
            return match.group(1), match.group(2)
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", help="dépôt HuggingFace (ex. Xenova/opus-mt-fr-en)")
    parser.add_argument("--source", help="snapshot local déjà téléchargé")
    parser.add_argument("--out", required=True, help="dossier d'import à produire")
    parser.add_argument(
        "--variant", choices=sorted(VARIANTS), default="fp32",
        help="précision des ONNX (int8 = compact, qualité à valider par l'étape 0)"
    )
    parser.add_argument("--source-lang", help="force la langue source (sinon déduite du nom)")
    parser.add_argument("--target-lang", help="force la langue cible (sinon déduite du nom)")
    args = parser.parse_args()

    if not args.repo and not args.source:
        fail("--repo ou --source requis")

    snapshot = resolve_snapshot(args)

    pair = parse_pair(args.repo, args.source)
    source_lang = args.source_lang or (pair and pair[0])
    target_lang = args.target_lang or (pair and pair[1])
    if not source_lang or not target_lang:
        fail("paire de langues indéterminable — passer --source-lang/--target-lang")

    hf_config_path = snapshot / "config.json"
    if not hf_config_path.is_file():
        fail(f"config.json HuggingFace absent de {snapshot}")
    hf_config = json.loads(hf_config_path.read_text(encoding="utf-8"))

    # Convention Marian : decoder_start = pad si non déclaré explicitement.
    decoder_start = hf_config.get("decoder_start_token_id", hf_config.get("pad_token_id"))
    eos = hf_config.get("eos_token_id")
    if decoder_start is None or eos is None:
        fail("decoder_start_token_id / eos_token_id introuvables dans le config HF")

    tokenizer_path = snapshot / "tokenizer.json"
    if not tokenizer_path.is_file():
        fail(f"tokenizer.json absent de {snapshot}")
    if '"Unigram"' not in tokenizer_path.read_text(encoding="utf-8")[:200_000]:
        print("AVERTISSEMENT : tokenizer.json ne semble pas unigram — l'import le refusera peut-être")

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    shutil.copyfile(pick_onnx(snapshot, "encoder_model", args.variant), out / ROLES["translation_encoder"])
    shutil.copyfile(pick_onnx(snapshot, "decoder_model", args.variant), out / ROLES["translation_decoder"])
    shutil.copyfile(tokenizer_path, out / ROLES["tokenizer_json"])

    if args.variant == "int8":
        print("⚠ int8 : valider la qualité (étape 0 du guide) avant de s'y fier.")

    config = {
        "schema_version": 1,
        "model_type": "translation",
        # Le nom porte la variante : deux variantes du même modèle doivent
        # pouvoir coexister dans la liste (l'import refuse les homonymes).
        "display_name": f"OPUS-MT {source_lang}→{target_lang} ({args.variant})",
        "files": ROLES,
        "translation": {
            "source_languages": [source_lang],
            "target_language": target_lang,
            "decoder_start_token_id": int(decoder_start),
            "eos_token_id": int(eos),
            "max_output_tokens": 48,
        },
    }
    (out / "config.json").write_text(
        json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    total_mb = sum(f.stat().st_size for f in out.iterdir()) / 1_048_576
    print(f"OK : {out} ({total_mb:.0f} Mo) — à pousser dans Download/ puis importer via l'UI")


if __name__ == "__main__":
    main()
