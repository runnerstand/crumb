from dataclasses import dataclass
from hashlib import sha256
from pathlib import Path

from fastapi import HTTPException
from fastapi import UploadFile


UPLOAD_ROOT = Path(__file__).resolve().parents[1] / "uploads" / "recipes"
UPLOAD_ROUTE = "/uploads/recipes"
MAX_RECIPE_IMAGE_BYTES = 5 * 1024 * 1024


@dataclass(frozen=True)
class StoredRecipeImage:
    image_url: str
    sha256_hash: str
    content_type: str


def ensure_recipe_upload_dir() -> None:
    UPLOAD_ROOT.mkdir(parents=True, exist_ok=True)


async def store_recipe_image(file: UploadFile) -> StoredRecipeImage:
    ensure_recipe_upload_dir()
    data = await file.read(MAX_RECIPE_IMAGE_BYTES + 1)

    if not data:
        raise HTTPException(status_code=400, detail="Please choose an image to upload.")

    if len(data) > MAX_RECIPE_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image must be 5 MB or smaller.")

    detected = detect_image_type(data)
    if detected is None:
        raise HTTPException(status_code=400, detail="Only JPEG, PNG, and WebP images are supported.")

    extension, content_type = detected
    digest = sha256(data).hexdigest()
    filename = f"{digest}.{extension}"
    destination = UPLOAD_ROOT / filename

    if not destination.exists():
        destination.write_bytes(data)

    return StoredRecipeImage(
        image_url=f"{UPLOAD_ROUTE}/{filename}",
        sha256_hash=digest,
        content_type=content_type,
    )


def detect_image_type(data: bytes) -> tuple[str, str] | None:
    if data.startswith(b"\xff\xd8\xff"):
        return "jpg", "image/jpeg"

    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png", "image/png"

    if len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp", "image/webp"

    return None
