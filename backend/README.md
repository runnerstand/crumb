# Smart Pantry Backend

FastAPI backend for the Smart Pantry app.

## Setup

From the `backend` directory, create and activate a virtual environment:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

Install dependencies:

```powershell
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m pip install -r requirements-dev.txt
```

Run the API locally:

```powershell
uvicorn app.main:app --reload
```

The health check is available at:

```text
http://127.0.0.1:8000/health
```

Run tests:

```powershell
python -m pytest
```

Build the local ChromaDB recipe index:

```powershell
python -m app.build_recipe_vectors
```

Configurable environment variables:

- `CHROMA_DB_PATH`: local ChromaDB persistence path. Defaults to `backend/chroma_db`.
- `CHROMA_COLLECTION_NAME`: recipe collection name. Defaults to `smart_pantry_recipes`.
- `EMBEDDING_MODEL_NAME`: Sentence Transformers model. Defaults to `sentence-transformers/all-MiniLM-L6-v2`.

## Dependencies

- `fastapi`: web framework for the API.
- `uvicorn`: local ASGI server for running FastAPI.
- `chromadb`: local vector database used for semantic recipe retrieval.
- `sentence-transformers`: creates recipe and pantry embeddings with `sentence-transformers/all-MiniLM-L6-v2`.

## Development dependencies

- `pytest`: test runner.
- `httpx`: HTTP client used by FastAPI's test client.
