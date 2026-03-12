from typing import List

from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel
from sqlalchemy import Column, ForeignKey, Integer, String, create_engine
from sqlalchemy.orm import (Session, declarative_base, relationship,
                            sessionmaker)

# DATABASE

DATABASE_URL = "sqlite:///./voting.db"

engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(bind=engine)
Base = declarative_base()


# MODELS

class Poll(Base):
    __tablename__ = "polls"

    id = Column(Integer, primary_key=True)
    title = Column(String)
    description = Column(String)

    options = relationship("Option", back_populates="poll", cascade="all, delete")


class Option(Base):
    __tablename__ = "options"

    id = Column(Integer, primary_key=True)
    text = Column(String)
    votes = Column(Integer, default=0)

    poll_id = Column(Integer, ForeignKey("polls.id"))
    poll = relationship("Poll", back_populates="options")


Base.metadata.create_all(bind=engine)


# SCHEMAS

class OptionCreate(BaseModel):
    text: str


class PollCreate(BaseModel):
    title: str
    description: str
    options: List[OptionCreate]


class Vote(BaseModel):
    option_id: int


class OptionResponse(BaseModel):
    id: int
    text: str
    votes: int

    class Config:
        from_attributes = True


class PollResponse(BaseModel):
    id: int
    title: str
    description: str
    options: List[OptionResponse]

    class Config:
        from_attributes = True


# FASTAPI

app = FastAPI(title="Simple voting API")


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# ENDPOINTS

@app.post("/polls", response_model=PollResponse)
def create_poll(poll: PollCreate, db: Session = Depends(get_db)):
    db_poll = Poll(title=poll.title, description=poll.description)
    db.add(db_poll)
    db.commit()
    db.refresh(db_poll)

    for opt in poll.options:
        option = Option(text=opt.text, poll_id=db_poll.id)
        db.add(option)

    db.commit()
    db.refresh(db_poll)
    return db_poll


@app.get("/polls", response_model=List[PollResponse])
def get_polls(db: Session = Depends(get_db)):
    return db.query(Poll).all()


@app.get("/polls/{poll_id}", response_model=PollResponse)
def get_poll(poll_id: int, db: Session = Depends(get_db)):
    poll = db.query(Poll).filter(Poll.id == poll_id).first()
    if not poll:
        raise HTTPException(status_code=404, detail="Poll not found")
    return poll


@app.post("/polls/{poll_id}/vote")
def vote_function(poll_id: int, vote: Vote, db: Session = Depends(get_db)):
    option = db.query(Option).filter(
        Option.id == vote.option_id,
        Option.poll_id == poll_id
    ).first()

    if not option:
        raise HTTPException(status_code=404, detail="Option not found")

    option.votes += 1
    db.commit()

    return {"message": "Vote recorded", "option_id": option.id}


@app.put("/polls/{poll_id}")
def update_poll(poll_id: int, updated: PollCreate, db: Session = Depends(get_db)):
    poll = db.query(Poll).filter(Poll.id == poll_id).first()

    if not poll:
        raise HTTPException(status_code=404, detail="Poll not found")

    poll.title = updated.title
    poll.description = updated.description

    db.query(Option).filter(Option.poll_id == poll_id).delete()

    for opt in updated.options:
        db.add(Option(text=opt.text, poll_id=poll_id))

    db.commit()

    return {"message": "Poll updated"}


@app.delete("/polls/{poll_id}")
def delete_poll(poll_id: int, db: Session = Depends(get_db)):
    poll = db.query(Poll).filter(Poll.id == poll_id).first()

    if not poll:
        raise HTTPException(status_code=404, detail="Poll not found")

    db.delete(poll)
    db.commit()

    return {"message": "Poll deleted"}
