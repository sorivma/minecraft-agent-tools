import hashlib
import os
from pathlib import Path
import subprocess
import sys

# A fixed release is the API boundary; never consume a moving branch or latest.
REPOSITORY = "sorivma/minecraft-harness"
TAG = "v0.1.0"
ASSET = "minecraft-harness-forge-1.7.10-0.1.0-dev.jar"
target = Path(sys.argv[1] if len(sys.argv) > 1 else ".dependencies")
target.mkdir(parents=True, exist_ok=True)
subprocess.run(["gh", "release", "download", TAG, "--repo", REPOSITORY,
                "--pattern", ASSET, "--pattern", "SHA256SUMS", "--dir", str(target), "--clobber"], check=True)
checksums = dict((line.split(maxsplit=1)[1].lstrip(" *"), line.split(maxsplit=1)[0])
                 for line in (target / "SHA256SUMS").read_text(encoding="utf-8").splitlines() if line.strip())
actual = hashlib.sha256((target / ASSET).read_bytes()).hexdigest()
if actual != checksums.get(ASSET):
    raise SystemExit("Harness API checksum mismatch")
print(f"Verified {REPOSITORY}@{TAG}: {ASSET} ({actual})")
if os.environ.get("GITHUB_OUTPUT"):
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"jar={(target / ASSET).resolve().as_posix()}\n")
