from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

PACKAGE_ROOT = Path(__file__).resolve().parents[1]
if str(PACKAGE_ROOT) not in sys.path:
    sys.path.insert(0, str(PACKAGE_ROOT))

from vr3d_pc.depth import VideoDepthAnythingAdapter


def main() -> None:
    if len(sys.argv) != 5:
        raise SystemExit(2)
    runtime, variant, source, destination = sys.argv[1:]
    image = np.load(source, allow_pickle=False)
    depth = VideoDepthAnythingAdapter(Path(runtime), allow_subprocess=False).infer(image, variant)
    np.save(destination, depth, allow_pickle=False)


if __name__ == "__main__":
    main()
