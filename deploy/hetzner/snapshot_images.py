"""Capture the exact RUNNING container image IDs, never mutable tag resolution."""
import json
import subprocess
import sys

config = json.load(sys.stdin)
dc = ["docker", "compose", "--env-file", sys.argv[1], "-p", config["name"], "-f", "docker-compose.hetzner.yml"]
snapshot = {"services": {}}
for name in config["services"]:
    containers = subprocess.check_output(dc + ["ps", "-aq", name], text=True).split()
    if len(containers) != 1:
        raise RuntimeError("Expected one existing container per service for a complete rollback snapshot")
    image = subprocess.check_output(["docker", "inspect", "--format", "{{.Image}}", containers[0]], text=True).strip()
    snapshot["services"][name] = {"image": image, "pull_policy": "never"}
print(json.dumps(snapshot, indent=2))
