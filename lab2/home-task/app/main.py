from datetime import date, timedelta
from pathlib import Path

from fastapi import FastAPI, Form, HTTPException, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from pydantic import ValidationError

from app.models import TripSearchRequest
from app.services import build_trip_plan


app = FastAPI(title="Grand Prix Trip Planner API")

BASE_DIR = Path(__file__).resolve().parent.parent
templates = Jinja2Templates(directory=str(BASE_DIR / "templates"))


@app.get("/", response_class=HTMLResponse)
async def index(request: Request) -> HTMLResponse:
    """Render the main page with an empty trip search form."""
    return templates.TemplateResponse(
        "index.html",
        {
            "request": request,
            "today": date.today().isoformat(),
            "default_to": (date.today() + timedelta(days=90)).isoformat(),
            "results": None,
            "error": None,
        },
    )


@app.post("/plan", response_class=HTMLResponse)
async def plan_from_form(
    request: Request,
    from_date: str = Form(...),
    to_date: str = Form(...),
    departure_iata: str = Form(...),
    adults: int = Form(1),
    stay_nights: int = Form(4),
    currency: str = Form("USD"),
) -> HTMLResponse:
    """Process the trip search form and return results with estimated costs for matching races."""
    try:
        payload = TripSearchRequest(
            from_date=from_date,
            to_date=to_date,
            departure_iata=departure_iata,
            adults=adults,
            stay_nights=stay_nights,
            currency=currency,
        )
        results = await build_trip_plan(payload)
        return templates.TemplateResponse(
            "index.html",
            {
                "request": request,
                "today": from_date,
                "default_to": to_date,
                "results": results,
                "error": None,
            },
        )
    except ValidationError as exc:
        message = "; ".join(err["msg"] for err in exc.errors())
        return templates.TemplateResponse(
            "index.html",
            {
                "request": request,
                "today": from_date,
                "default_to": to_date,
                "results": None,
                "error": message,
            },
            status_code=422,
        )
    except HTTPException as exc:
        message = str(exc)
        return templates.TemplateResponse(
            "index.html",
            {
                "request": request,
                "today": from_date,
                "default_to": to_date,
                "results": None,
                "error": message,
            },
            status_code=400,
        )
