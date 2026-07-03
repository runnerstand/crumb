import os
import tempfile
from pathlib import Path


TEST_DATABASE_FILE = Path(tempfile.gettempdir()) / "smart_pantry_test_collection.db"
TEST_DATABASE_URL = f"sqlite:///{TEST_DATABASE_FILE}"
os.environ["DATABASE_URL"] = TEST_DATABASE_URL
os.environ["SMART_PANTRY_DATABASE_URL"] = TEST_DATABASE_URL
