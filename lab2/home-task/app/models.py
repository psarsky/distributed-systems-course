from dataclasses import dataclass
from datetime import date
from typing import Any

from pydantic import BaseModel, Field, field_validator


class TripSearchRequest(BaseModel):
    """Request model for trip search form."""

    from_date: date = Field(description="Start date of the search range")
    to_date: date = Field(description="End date of the search range")
    departure_iata: str = Field(
        min_length=3, max_length=3, description="Departure airport IATA code"
    )
    adults: int = Field(default=1, ge=1, le=6)
    stay_nights: int = Field(default=4, ge=1, le=14)
    currency: str = Field(default="USD", min_length=3, max_length=3)

    @field_validator("departure_iata", "currency")
    @classmethod
    def upper_code(cls, value: str) -> str:
        return value.strip().upper()

    @field_validator("from_date", "to_date")
    @classmethod
    def validate_future_dates(cls, value: date) -> date:
        today = date.today()
        if value < today:
            raise ValueError("date must be in the future")
        return value

    @field_validator("to_date")
    @classmethod
    def validate_dates(cls, to_date: date, info: Any) -> date:
        from_date = info.data.get("from_date")
        if from_date and to_date < from_date:
            raise ValueError("to_date must be >= from_date")
        if from_date and (to_date - from_date).days > 365:
            raise ValueError("maximum date range is 365 days")
        return to_date


@dataclass
class PriceOption:
    """Represents a price breakdown for a trip option with flight, hotel, and ticket costs."""

    label: str
    flight_price: float
    hotel_price: float
    ticket_price: float

    @property
    def total(self) -> float:
        """Calculate the total cost by summing flight, hotel, and ticket prices."""
        return self.flight_price + self.hotel_price + self.ticket_price
