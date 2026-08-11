from __future__ import annotations

import io
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageOps, UnidentifiedImageError


class ImageRejected(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


@dataclass(frozen=True)
class ImageInfo:
    format: str
    width: int
    height: int


SIGNATURES = {
    "JPEG": lambda b: b.startswith(b"\xff\xd8\xff"),
    "PNG": lambda b: b.startswith(b"\x89PNG\r\n\x1a\n"),
    "WEBP": lambda b: len(b) >= 12 and b[:4] == b"RIFF" and b[8:12] == b"WEBP",
}


def inspect_signature(data: bytes, max_bytes: int) -> str:
    if not data or len(data) > max_bytes:
        raise ImageRejected("file_size")
    matches = [name for name, predicate in SIGNATURES.items() if predicate(data)]
    if len(matches) != 1:
        raise ImageRejected("signature")
    return matches[0]


def inspect_image(data: bytes, max_bytes: int, max_pixels: int) -> ImageInfo:
    signature_formats = {inspect_signature(data, max_bytes)}
    try:
        with Image.open(io.BytesIO(data)) as probe:
            fmt = str(probe.format or "").upper()
            width, height = probe.size
            if fmt not in signature_formats:
                raise ImageRejected("format_mismatch")
            if width <= 0 or height <= 0 or width * height > max_pixels:
                raise ImageRejected("pixel_limit")
            probe.verify()
        with Image.open(io.BytesIO(data)) as decoded:
            decoded.load()
    except ImageRejected:
        raise
    except (UnidentifiedImageError, OSError, SyntaxError, ValueError, Image.DecompressionBombError):
        raise ImageRejected("decode") from None
    return ImageInfo(fmt, width, height)


def sanitize_image(source: Path, destination: Path, max_edge: int) -> ImageInfo:
    try:
        with Image.open(source) as opened:
            image = ImageOps.exif_transpose(opened)
            image.load()
            if image.mode not in ("RGB", "RGBA"):
                image = image.convert("RGBA" if "transparency" in image.info else "RGB")
            if image.mode == "RGBA":
                background = Image.new("RGB", image.size, "black")
                background.paste(image, mask=image.getchannel("A"))
                image = background
            else:
                image = image.convert("RGB")
            image.thumbnail((max_edge, max_edge), Image.Resampling.LANCZOS)
            destination.parent.mkdir(parents=True, exist_ok=True)
            image.save(destination, "WEBP", quality=90, method=6, exif=b"")
            return ImageInfo("WEBP", image.width, image.height)
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        raise ImageRejected("sanitize") from None
