from datetime import datetime
from datetime import timezone

from sqlalchemy import DateTime
from sqlalchemy import ForeignKey
from sqlalchemy import Integer
from sqlalchemy import JSON
from sqlalchemy import String
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column
from sqlalchemy.orm import relationship

from app.database import Base


class SavedRecipe(Base):
    __tablename__ = "saved_recipes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    title: Mapped[str] = mapped_column(String, nullable=False)
    ingredients: Mapped[list[str]] = mapped_column(JSON, nullable=False)
    ingredient_measurements: Mapped[list[dict]] = mapped_column(
        JSON,
        default=list,
        nullable=False,
    )
    missing_ingredients: Mapped[list[str]] = mapped_column(JSON, nullable=False)
    instructions: Mapped[list[str]] = mapped_column(JSON, nullable=False)
    cooking_time_minutes: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )


class CommunityPost(Base):
    __tablename__ = "community_posts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    creator_id: Mapped[str] = mapped_column(String, nullable=False, index=True)
    creator_name: Mapped[str] = mapped_column(String, nullable=False)
    title: Mapped[str] = mapped_column(String, nullable=False)
    ingredients_json: Mapped[list[str]] = mapped_column(JSON, nullable=False)
    caption: Mapped[str] = mapped_column(String(200), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )
    comments: Mapped[list["CommunityComment"]] = relationship(
        back_populates="post",
        cascade="all, delete-orphan",
    )


class CommunityComment(Base):
    __tablename__ = "community_comments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    post_id: Mapped[int] = mapped_column(
        ForeignKey("community_posts.id"),
        nullable=False,
        index=True,
    )
    creator_id: Mapped[str] = mapped_column(String, nullable=False, index=True)
    creator_name: Mapped[str] = mapped_column(String, nullable=False)
    comment_text: Mapped[str] = mapped_column(String(500), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )
    post: Mapped[CommunityPost] = relationship(back_populates="comments")
