import asyncio
import os
from datetime import date, timedelta
from typing import Any
from dotenv import load_dotenv
import httpx
from fastapi import HTTPException

from app.constants import (
    DEFAULT_TIMEOUT,
    MAX_RACES_PER_REQUEST,
    OPENF1_BASE,
    SERPAPI_BASE,
)
from app.models import PriceOption, TripSearchRequest
from app.util import (
    destination_iata,
    extract_flight_prices,
    extract_hotel_prices,
    fetch_json,
    match_option,
    ticket_prices_for_race,
    usd_exchange_rate,
)

load_dotenv()

SERPAPI_KEY = os.getenv("SERPAPI_API_KEY")


async def race_trip_cost(
    client: httpx.AsyncClient,
    race: dict[str, Any],
    request_data: TripSearchRequest,
    usd_to_target_rate: float,
) -> dict[str, Any]:
    """Calculate the total trip cost for a specific race including flights, hotels, and tickets."""
    schedule = race.get("schedule", {}).get("race", {})
    race_date = date.fromisoformat(schedule["date"])
    check_in = race_date - timedelta(days=3)
    check_out = check_in + timedelta(days=request_data.stay_nights)

    circuit = race.get("circuit", {})
    dest_iata = destination_iata(circuit)
    city = str(circuit.get("city") or "")

    if not dest_iata:
        return {
            "race": race,
            "destination_iata": None,
            "travel_dates": {
                "check_in": check_in.isoformat(),
                "check_out": check_out.isoformat(),
            },
            "error": "No destination airport mapping found for this GP",
        }

    flights_task = fetch_json(
        client,
        SERPAPI_BASE,
        {
            "engine": "google_flights",
            "api_key": SERPAPI_KEY,
            "type": "1",
            "departure_id": request_data.departure_iata,
            "arrival_id": dest_iata,
            "outbound_date": check_in.isoformat(),
            "return_date": check_out.isoformat(),
            "adults": request_data.adults,
            "currency": request_data.currency,
            "hl": "en",
            "gl": "us",
        },
    )

    hotels_task = fetch_json(
        client,
        SERPAPI_BASE,
        {
            "engine": "google_hotels",
            "api_key": SERPAPI_KEY,
            "q": f"{city} hotels",
            "check_in_date": check_in.isoformat(),
            "check_out_date": check_out.isoformat(),
            "adults": request_data.adults,
            "currency": request_data.currency,
            "hl": "en",
            "gl": "us",
            "sort_by": "3",
        },
    )

    try:
        flights_payload, hotels_payload = await asyncio.gather(
            flights_task, hotels_task
        )
    except HTTPException as exc:
        detail_text = str(exc.detail).lower()
        if "google flights hasn't returned any results" in detail_text:
            return {
                "race": race,
                "destination_iata": dest_iata,
                "travel_dates": {
                    "check_in": check_in.isoformat(),
                    "check_out": check_out.isoformat(),
                },
                "error": "No available flights found for the selected dates.",
            }
        if "google hotels hasn't returned any results" in detail_text:
            return {
                "race": race,
                "destination_iata": dest_iata,
                "travel_dates": {
                    "check_in": check_in.isoformat(),
                    "check_out": check_out.isoformat(),
                },
                "error": "No available hotels found for the selected dates.",
            }
        raise

    flight_prices = extract_flight_prices(flights_payload)
    hotel_prices = extract_hotel_prices(hotels_payload)

    if not flight_prices or not hotel_prices:
        return {
            "race": race,
            "destination_iata": dest_iata,
            "travel_dates": {
                "check_in": check_in.isoformat(),
                "check_out": check_out.isoformat(),
            },
            "error": f"No available {'flights' if not flight_prices else 'hotels'} found for the selected dates.",
        }

    flight_low, flight_mid, flight_high = match_option(flight_prices)
    hotel_low, hotel_mid, hotel_high = match_option(hotel_prices)
    ticket_low_usd, ticket_mid_usd, ticket_high_usd = ticket_prices_for_race(race)
    ticket_low = ticket_low_usd * usd_to_target_rate
    ticket_mid = ticket_mid_usd * usd_to_target_rate
    ticket_high = ticket_high_usd * usd_to_target_rate

    options = [
        PriceOption("Budget", flight_low, hotel_low, ticket_low),
        PriceOption("Balanced", flight_mid, hotel_mid, ticket_mid),
        PriceOption("Premium", flight_high, hotel_high, ticket_high),
    ]

    return {
        "race": race,
        "destination_iata": dest_iata,
        "travel_dates": {
            "check_in": check_in.isoformat(),
            "check_out": check_out.isoformat(),
        },
        "price_options": [
            {
                "label": o.label,
                "flight": round(o.flight_price, 2),
                "hotel": round(o.hotel_price, 2),
                "ticket": round(o.ticket_price, 2),
                "total": round(o.total, 2),
            }
            for o in options
        ],
    }


async def list_races_in_range(request_data: TripSearchRequest) -> list[dict[str, Any]]:
    """Fetch and filter Grand Prix races that fall within the requested date range."""
    season = request_data.from_date.year

    async with httpx.AsyncClient(timeout=DEFAULT_TIMEOUT) as client:
        payload = await fetch_json(
            client, f"{OPENF1_BASE}/{season}", {"limit": 50, "offset": 0}
        )

    races = payload.get("races", []) or []
    filtered: list[dict[str, Any]] = []

    for race in races:
        race_date_str = race.get("schedule", {}).get("race", {}).get("date")
        if not race_date_str:
            continue
        race_date = date.fromisoformat(race_date_str)
        if request_data.from_date <= race_date <= request_data.to_date:
            filtered.append(race)

    return filtered[:MAX_RACES_PER_REQUEST]


async def build_trip_plan(data: TripSearchRequest) -> dict[str, Any]:
    """Build a trip plan with cost estimates for all matching Grand Prix races."""
    if not SERPAPI_KEY:
        raise HTTPException(
            status_code=500,
            detail="Missing SERPAPI_API_KEY in environment variables",
        )

    races = await list_races_in_range(data)
    if not races:
        return {
            "query": data.model_dump(mode="json"),
            "count": 0,
            "items": [],
            "message": "No Grand Prix found in the selected date range.",
        }

    async with httpx.AsyncClient(timeout=DEFAULT_TIMEOUT) as client:
        usd_to_target_rate = await usd_exchange_rate(client, data.currency)
        tasks = [
            race_trip_cost(client, race, data, usd_to_target_rate) for race in races
        ]
        items = await asyncio.gather(*tasks)

    return {
        "query": data.model_dump(mode="json"),
        "count": len(items),
        "items": items,
    }
