import statistics
from typing import Any

import httpx
from fastapi import HTTPException

from app.constants import (
    DEFAULT_TICKET_ESTIMATE_USD,
    IATA_BY_CIRCUIT_ID,
    IATA_BY_COUNTRY,
    TICKET_ESTIMATE_USD,
)


async def fetch_json(
    client: httpx.AsyncClient, url: str, params: dict[str, Any]
) -> dict[str, Any]:
    """Fetch JSON data from an external API with error handling for timeouts and HTTP errors."""
    try:
        response = await client.get(url, params=params)
        response.raise_for_status()
        data = response.json()
    except httpx.TimeoutException as exc:
        raise HTTPException(
            status_code=504,
            detail=f"Timeout while fetching data from external API ({url})",
        ) from exc
    except httpx.HTTPStatusError as exc:
        detail = f"External API error ({url}): {exc.response.text} ({exc.response.status_code})"
        raise HTTPException(status_code=502, detail=detail) from exc
    except ValueError as exc:
        raise HTTPException(
            status_code=502, detail=f"Invalid JSON response from external API ({url})"
        ) from exc

    if isinstance(data, dict) and data.get("error"):
        raise HTTPException(
            status_code=502, detail=f"External API error ({url}): {data['error']}"
        )

    return data


def destination_iata(circuit: dict[str, Any]) -> str | None:
    """Get the IATA airport code for a race circuit, looking up by circuit ID or country."""
    circuit_id = str(circuit.get("circuitId", "")).strip().lower()
    if circuit_id in IATA_BY_CIRCUIT_ID:
        return IATA_BY_CIRCUIT_ID[circuit_id]

    country = str(circuit.get("country", "")).strip().lower()
    return IATA_BY_COUNTRY.get(country)


def ticket_prices_for_race(race: dict[str, Any]) -> tuple[float, float, float]:
    """Get estimated ticket prices (low, mid, high) for a specific Grand Prix race."""
    circuit_id = str(race.get("circuit", {}).get("circuitId", "")).lower()
    prices = TICKET_ESTIMATE_USD.get(circuit_id, DEFAULT_TICKET_ESTIMATE_USD)
    return float(prices[0]), float(prices[1]), float(prices[2])


async def usd_exchange_rate(client: httpx.AsyncClient, currency: str) -> float:
    """Fetch the USD exchange rate for the requested target currency."""
    target_currency = currency.strip().upper()
    if target_currency == "USD":
        return 1.0

    payload = await fetch_json(
        client,
        "https://api.frankfurter.app/latest",
        {"from": "USD", "to": target_currency},
    )
    rates = payload.get("rates", {})
    rate = rates.get(target_currency)
    if not isinstance(rate, (int, float)):
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported or unavailable currency code: {target_currency}",
        )

    return float(rate)


def extract_flight_prices(flights_payload: dict[str, Any]) -> list[float]:
    """Extract all available flight prices from the SerpAPI flights response payload."""
    prices: list[float] = []
    for key in ("best_flights", "other_flights"):
        for offer in flights_payload.get(key, []) or []:
            price = offer.get("price")
            if isinstance(price, (int, float)):
                prices.append(float(price))
    return prices


def extract_hotel_prices(hotels_payload: dict[str, Any]) -> list[float]:
    """Extract all available hotel prices from the SerpAPI hotels response payload."""
    prices: list[float] = []
    for prop in hotels_payload.get("properties", []) or []:
        extracted = (
            prop.get("total_rate", {}).get("extracted_lowest")
            or prop.get("rate_per_night", {}).get("extracted_lowest")
            or prop.get("extracted_price")
        )
        if isinstance(extracted, (int, float)):
            prices.append(float(extracted))
    for ad in hotels_payload.get("ads", []) or []:
        extracted = ad.get("extracted_price")
        if isinstance(extracted, (int, float)):
            prices.append(float(extracted))
    return prices


def match_option(values: list[float]) -> tuple[float, float, float]:
    """Calculate low, median, and high price options from a list of values."""
    if not values:
        return 0.0, 0.0, 0.0

    sorted_values = sorted(values)
    low = sorted_values[0]
    med = statistics.median(sorted_values)
    high = sorted_values[-1]
    return float(low), float(med), float(high)
