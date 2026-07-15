import os
import tempfile
from pathlib import Path


TEST_DATABASE_FILE = Path(tempfile.gettempdir()) / "smart_pantry_test_collection.db"
os.environ.setdefault(
    "SMART_PANTRY_DATABASE_URL",
    f"sqlite:///{TEST_DATABASE_FILE}",
)
