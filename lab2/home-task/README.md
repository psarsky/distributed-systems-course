# Grand Prix Trip Planner

Simple FastAPI app for planning Formula 1 Grand Prix trip costs.

## Requirements

- Python 3.11+
- SerpAPI key

## Installation

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

Create `.env` file:

```env
SERPAPI_API_KEY=your_key_here
```

## Run (Uvicorn)

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## Run (Docker)
No venv needed, just the .env file.

```bash
docker compose up
```

App:       http://127.0.0.1:8000/  
SwaggerUI: http://127.0.0.1:8000/docs
