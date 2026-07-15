from datetime import datetime

from pydantic import BaseModel
from pydantic import ConfigDict
from pydantic import Field
from pydantic import field_validator


class SavedRecipeCreate(BaseModel):
    title: str = Field(min_length=1)
    ingredients: list[str] = Field(min_length=1)
    ingredient_measurements: list[dict] = Field(default_factory=list)
    missing_ingredients: list[str] = Field(default_factory=list)
    instructions: list[str] = Field(min_length=1)
    cooking_time_minutes: int = Field(gt=0)

    @field_validator("title")
    @classmethod
    def title_must_not_be_blank(cls, value: str) -> str:
        cleaned_value = value.strip()
        if not cleaned_value:
            raise ValueError("Title must not be blank.")

        return cleaned_value

    @field_validator("ingredients", "missing_ingredients", "instructions")
    @classmethod
    def list_items_must_not_be_blank(cls, values: list[str]) -> list[str]:
        cleaned_values = [value.strip() for value in values]
        if any(not value for value in cleaned_values):
            raise ValueError("List items must not be blank.")

        return cleaned_values


class SavedRecipeRead(SavedRecipeCreate):
    model_config = ConfigDict(from_attributes=True)

    id: int
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
    title: str
    ingredients: list[RecipeIngredientRead]
    instructions: list[str]
    cooking_time_minutes: int | None = None
    created_at: datetime


class CommunityPostCreate(BaseModel):
    title: str = Field(min_length=1)
    ingredients: list[str] = Field(min_length=1)
    caption: str = Field(default="", max_length=200)

    @field_validator("title")
    @classmethod
    def title_must_not_be_blank(cls, value: str) -> str:
        cleaned_value = value.strip()
        if not cleaned_value:
            raise ValueError("Title must not be blank.")

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
    title: str
    ingredients_json: list[str]
    caption: str
    created_at: datetime
    updated_at: datetime


class CommunityCommentCreate(BaseModel):
    comment_text: str = Field(min_length=1, max_length=500)

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
