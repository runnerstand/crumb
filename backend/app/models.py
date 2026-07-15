from datetime import datetime
from datetime import timezone

from sqlalchemy import DateTime
from sqlalchemy import ForeignKey
from sqlalchemy import Integer
from sqlalchemy import Boolean
from sqlalchemy import JSON
from sqlalchemy import String
from sqlalchemy import UniqueConstraint
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column
from sqlalchemy.orm import relationship

from app.database import Base


LOCAL_USER_ID = "local-user"
LOCAL_USER_NAME = "Local User"


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(255), primary_key=True)
    display_name: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )

    recipes: Mapped[list["Recipe"]] = relationship(back_populates="creator")


class Recipe(Base):
    __tablename__ = "recipes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    user_id: Mapped[str] = mapped_column(
        ForeignKey("users.id"),
        nullable=False,
        index=True,
        default=LOCAL_USER_ID,
    )
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    instructions: Mapped[list[str]] = mapped_column(JSON, default=list, nullable=False)
    cooking_time_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )

    creator: Mapped[User] = relationship(back_populates="recipes")
    ingredients: Mapped[list["RecipeIngredient"]] = relationship(
        back_populates="recipe",
        cascade="all, delete-orphan",
    )


class Ingredient(Base):
    __tablename__ = "ingredients"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False, unique=True)
    category: Mapped[str | None] = mapped_column(String(255), nullable=True)
    aliases: Mapped[list[str]] = mapped_column(JSON, default=list, nullable=False)
    warning_tags: Mapped[list[str]] = mapped_column(JSON, default=list, nullable=False)
    needs_review: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

    recipes: Mapped[list["RecipeIngredient"]] = relationship(
        back_populates="ingredient",
        cascade="all, delete-orphan",
    )


class RecipeIngredient(Base):
    __tablename__ = "recipe_ingredients"
    __table_args__ = (
        UniqueConstraint("recipe_id", "ingredient_id", name="uq_recipe_ingredient"),
    )

    recipe_id: Mapped[int] = mapped_column(
        ForeignKey("recipes.id", ondelete="CASCADE"),
        primary_key=True,
    )
    ingredient_id: Mapped[int] = mapped_column(
        ForeignKey("ingredients.id", ondelete="CASCADE"),
        primary_key=True,
    )
    quantity: Mapped[str | None] = mapped_column(String(100), nullable=True)
    unit: Mapped[str | None] = mapped_column(String(100), nullable=True)

    recipe: Mapped[Recipe] = relationship(back_populates="ingredients")
    ingredient: Mapped[Ingredient] = relationship(back_populates="recipes")


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
