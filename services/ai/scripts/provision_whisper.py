"""Explicit public weight download; never imported by app/tests or run at startup."""
import argparse
import hashlib
import json
from pathlib import Path
import os

ROOT = Path(__file__).resolve().parents[3]
REPO = "Systran/faster-whisper-small"
REVISION = "536b0662742c02347bc0e980a01041f333bce120"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--download", action="store_true", help="Authorize the roughly 500 MB download")
    args = parser.parse_args()
    if not args.download:
        parser.error("Pass --download explicitly; this does not run during startup/tests")
    os.environ["HF_HOME"] = str(ROOT / ".audit/hf-cache")
    from huggingface_hub import snapshot_download
    folder = Path(snapshot_download(REPO, revision=REVISION, local_dir=ROOT / "models/faster-whisper-small",
        token=False, allow_patterns=["model.bin", "config.json", "tokenizer.json", "vocabulary.*", "preprocessor_config.json"]))
    files = {}
    for path in folder.iterdir():
        if path.is_file():
            with path.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            files[path.name] = {"bytes": path.stat().st_size, "sha256": digest}
    assert all(files.get(name, {}).get("bytes", 0) > 0 for name in ("model.bin", "config.json", "tokenizer.json"))
    target = ROOT / ".audit/ai-acceptance-2026-10-16/model-provenance.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({"repo": REPO, "revision": REVISION, "files": files}, indent=2), encoding="utf-8")
    print("Verified public CTranslate2 model; manifest saved locally. No application/provider started.")


if __name__ == "__main__":
    main()
