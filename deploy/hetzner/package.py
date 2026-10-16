"""Build a local transport bundle from tracked Git snapshots; no push/server API."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--include-models", action="store_true", help="Copy existing local weights only; never download")
    parser.add_argument("--worker-image", help="Optional existing local image to docker-save for the frozen-worker path")
    args = parser.parse_args()
    assert not args.output.exists(), "Refusing to overwrite an existing bundle"
    siblings = ROOT.parent if (ROOT.parent / "STEMotion_RENDER").is_dir() else ROOT.parents[2]
    repos = {"STEMotion_BE": ROOT, "STEMotion_RENDER": siblings / "STEMotion_RENDER", "STEMotion_FE": siblings / "STEMotion_FE"}
    manifest = {"sourceCommits": {}, "workerImage": args.worker_image, "workerImageMatchesCurrentSource": "unverified"}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.output, "w:gz", compresslevel=1) as bundle:
        for name, repo in repos.items():
            manifest["sourceCommits"][name] = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()
            raw = subprocess.check_output(["git", "-C", str(repo), "archive", "--format=tar", "--prefix="+name+"/", "HEAD"])
            with tarfile.open(fileobj=io.BytesIO(raw)) as archive:
                for member in archive:
                    basename = PurePosixPath(member.name).name
                    # Only explicitly public example envs belong in transport artifacts.
                    if basename.startswith(".env") and not basename.endswith(".example"):
                        continue
                    assert not member.name.endswith(("/.env", ".pem")) and "/.local-backups/" not in member.name
                    bundle.addfile(member, archive.extractfile(member) if member.isfile() else None)
        if args.include_models:
            expected = json.loads((ROOT / "services/ai/acceptance/vi-water-displacement/model-provenance.json").read_text())
            for name, metadata in expected["files"].items():
                path = ROOT / "models/faster-whisper-small" / name
                with path.open("rb") as stream: digest = hashlib.file_digest(stream, "sha256").hexdigest()
                assert digest == metadata["sha256"], "Weight checksum mismatch"
                bundle.add(path, arcname="STEMotion_BE/models/faster-whisper-small/"+name)
        if args.worker_image:
            manifest["workerImageId"] = subprocess.check_output(["docker", "image", "inspect", args.worker_image, "--format", "{{.Id}}"], text=True).strip()
            with tempfile.TemporaryDirectory(dir=args.output.parent) as temp:
                image = Path(temp) / "worker-image.tar"
                subprocess.run(["docker", "save", "-o", str(image), args.worker_image], check=True)
                bundle.add(image, arcname="worker-image.tar")
        data = json.dumps(manifest, indent=2).encode()
        member = tarfile.TarInfo("manifest.json"); member.size = len(data)
        bundle.addfile(member, io.BytesIO(data))
    print("Local bundle created. Tracked code only; no private .env. Image provenance/current-source equivalence remains unverified.")


if __name__ == "__main__": main()
