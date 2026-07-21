from datetime import datetime
import re

from pydantic import BaseModel
from pydantic import ConfigDict
from pydantic import Field
from pydantic import field_validator
from pydantic import model_validator


class SavedRecipeCreate(BaseModel):
    recipe_id: int = Field(gt=0)


class SavedRecipeRead(SavedRecipeCreate):
    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: str
    recipe: "RecipeRead"
    created_at: datetime


class RecipeIngredientCreate(BaseModel):
    ingredient_name: str = Field(min_length=1)
    quantity: str | None = None
    unit: str | None = None

    @field_validator("ingredient_name")
    @classmethod
    def ingredient_name_must_not_be_blank(cls, value: str) -> str:
        cleaned_value = value.strip()
        if not cleaned_value:
            raise ValueError("Ingredient name must not be blank.")

        return cleaned_value

    @field_validator("quantity", "unit")
    @classmethod
    def optional_text_must_be_clean(cls, value: str | None) -> str | None:
        if value is None:
            return None

        cleaned_value = value.strip()
        return cleaned_value or None


class RecipeCreate(BaseModel):
    title: str = Field(min_length=1)
    ingredients: list[RecipeIngredientCreate] = Field(min_length=1)
    instructions: list[str] = Field(min_length=1)
    cooking_time_minutes: int | None = Field(default=None, gt=0)
    servings: int = Field(default=2, ge=1)
    image_url: str | None = None

    @field_validator("title")
    @classmethod
    def recipe_title_must_not_be_blank(cls, value: str) -> str:
        cleaned_value = value.strip()
        if not cleaned_value:
            raise ValueError("Title must not be blank.")

        return cleaned_value

    @field_validator("instructions")
    @classmethod
    def instructions_must_not_be_blank(cls, values: list[str]) -> list[str]:
        cleaned_values = [value.strip() for value in values]
        if any(not value for value in cleaned_values):
            raise ValueError("Instructions must not be blank.")

        return cleaned_values

    @field_validator("image_url")
    @classmethod
    def image_url_must_be_upload_path(cls, value: str | None) -> str | None:
        if value is None:
            return None

        cleaned_value = value.strip()
        if not cleaned_value:
            return None

        if not re.fullmatch(r"/uploads/recipes/[a-f0-9]{64}\.(jpg|png|webp)", cleaned_value):
            raise ValueError("Recipe image URL must reference an uploaded recipe image.")

        return cleaned_value


class RecipeUpdate(RecipeCreate):
    pass


class RecipeIngredientRead(BaseModel):
    ingredient_id: int
    ingredient_name: str
    quantity: str | None = None
    unit: str | None = None


class RecipeRead(BaseModel):
    id: int
    user_id: str
    creator_name: str
    title: str
    ingredients: list[RecipeIngredientRead]
    instructions: list[str]
    cooking_time_minutes: int | None = None
    servings: int
    image_url: str | None = None
    average_rating: float | None = None
    rating_count: int = 0
    user_rating: int | None = None
    created_at: datetime


class RecipeRatingUpsert(BaseModel):
    rating: int = Field(ge=1, le=5)


class RecipeRatingRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    recipe_id: int
    average_rating: float | None
    rating_count: int
    user_rating: int | None


class RecipeImageUploadRead(BaseModel):
    image_url: str
    sha256_hash: str
    content_type: str


class CommunityPostCreate(BaseModel):
    title: str = ""
    ingredients: list[str] = Field(default_factory=list)
    caption: str = Field(default="", max_length=200)
    recipe_id: int | None = None

    @model_validator(mode="after")
    def standalone_posts_require_title_and_ingredients(self) -> "CommunityPostCreate":
        if self.recipe_id is not None:
            return self

        if not self.title.strip():
            raise ValueError("Title must not be blank.")

        if not self.ingredients:
            raise ValueError("Ingredients must not be blank.")

        return self

    @field_validator("title")
    @classmethod
    def title_must_not_be_blank(cls, value: str) -> str:
        cleaned_value = value.strip()
        return cleaned_value

    @field_validator("ingredients")
    @classmethod
    def ingredients_must_not_be_blank(cls, values: list[str]) -> list[str]:
        cleaned_values = [value.strip() for value in values]
        if any(not value for value in cleaned_values):
            raise ValueError("Ingredients must not be blank.")

        return cleaned_values

    @field_validator("caption")
    @classmethod
    def clean_caption(cls, value: str) -> str:
        return value.strip()


class CommunityPostUpdate(CommunityPostCreate):
    pass


class CommunityPostRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    creator_id: str
    creator_name: str
    recipe_id: int | None = None
    title: str
    ingredients_json: list[str]
    caption: str
    recipe: RecipeRead | None = None
    created_at: datetime
    updated_at: datetime


class CommunityCommentCreate(BaseModel):
    comment_text: str = Field(min_length=1, max_length=500)
    rating: int | None = Field(default=None, ge=1, le=5)

    @field_validator("comment_text")
    @classmethod
    def comment_text_must_not_be_blank(cls, value: str) -> str:
        cleaned_value = value.strip()
        if not cleaned_value:
            raise ValueError("Comment must not be blank.")

        return cleaned_value


class CommunityCommentUpdate(CommunityCommentCreate):
    pass


class CommunityCommentRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    post_id: int
    creator_id: str
    creator_name: str
    comment_text: str
    created_at: datetime
    updated_at: datetime


SavedRecipeRead.model_rebuild()
