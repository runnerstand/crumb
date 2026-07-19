from datetime import datetime
from datetime import timezone

from fastapi import APIRouter
from fastapi import Depends
from fastapi import HTTPException
from sqlalchemy.orm import Session
from sqlalchemy.orm import selectinload

from app.database import get_db
from app.ingredient_catalog import canonicalize_ingredient
from app.ingredient_catalog import load_ingredients
from app.models import CommunityComment
from app.models import CommunityPost
from app.models import Recipe
from app.models import RecipeIngredient
from app.rating_utils import get_recipe_rating_summary
from app.rating_utils import upsert_recipe_rating
from app.schemas import CommunityCommentCreate
from app.schemas import CommunityCommentRead
from app.schemas import CommunityCommentUpdate
from app.schemas import CommunityPostCreate
from app.schemas import CommunityPostRead
from app.schemas import CommunityPostUpdate


router = APIRouter()

LOCAL_CREATOR_ID = "local-user"
LOCAL_CREATOR_NAME = "Local User"


def serialize_recipe(recipe: Recipe, db: Session) -> dict:
    rating_summary = get_recipe_rating_summary(db, recipe.id)
    return {
        "id": recipe.id,
        "user_id": recipe.user_id,
        "creator_name": recipe.creator_name,
        "title": recipe.title,
        "ingredients": [
            {
                "ingredient_id": recipe_ingredient.ingredient_id,
                "ingredient_name": recipe_ingredient.ingredient.name,
                "quantity": recipe_ingredient.quantity,
                "unit": recipe_ingredient.unit,
            }
            for recipe_ingredient in sorted(
                recipe.ingredients,
                key=lambda item: item.ingredient.name,
            )
        ],
        "instructions": recipe.instructions,
        "cooking_time_minutes": recipe.cooking_time_minutes,
        "average_rating": rating_summary.average_rating,
        "rating_count": rating_summary.rating_count,
        "user_rating": rating_summary.user_rating,
        "created_at": recipe.created_at,
    }


def serialize_community_post(post: CommunityPost, db: Session) -> dict:
    recipe = post.recipe
    if recipe is None:
        return {
            "id": post.id,
            "creator_id": post.creator_id,
            "creator_name": post.creator_name,
            "recipe_id": post.recipe_id,
            "title": post.title,
            "ingredients_json": post.ingredients_json,
            "caption": post.caption,
            "recipe": None,
            "created_at": post.created_at,
            "updated_at": post.updated_at,
        }

    recipe_data = serialize_recipe(recipe, db)
    return {
        "id": post.id,
        "creator_id": post.creator_id,
        "creator_name": post.creator_name,
        "recipe_id": recipe.id,
        "title": recipe.title,
        "ingredients_json": [
            ingredient["ingredient_name"] for ingredient in recipe_data["ingredients"]
        ],
        "caption": post.caption,
        "recipe": recipe_data,
        "created_at": post.created_at,
        "updated_at": post.updated_at,
    }


def normalize_supported_community_ingredients(ingredients: list[str]) -> list[str]:
    catalogue = load_ingredients()
    supported_ingredients = {ingredient.name for ingredient in catalogue}
    normalized_ingredients: list[str] = []

    for ingredient in ingredients:
        normalized_ingredient = canonicalize_ingredient(ingredient, catalogue)

        if not normalized_ingredient:
            continue

        if normalized_ingredient not in supported_ingredients:
            raise HTTPException(
                status_code=400,
                detail=f"Unsupported ingredient: {ingredient.strip()}",
            )

        if normalized_ingredient not in normalized_ingredients:
            normalized_ingredients.append(normalized_ingredient)

    if not normalized_ingredients:
        raise HTTPException(
            status_code=400,
            detail="Please select at least one supported ingredient.",
        )

    return normalized_ingredients


def get_linked_recipe_id(community_post: CommunityPost) -> int | None:
    if community_post.recipe_id is not None:
        return community_post.recipe_id

    if community_post.recipe is not None:
        return community_post.recipe.id

    return None


@router.post(
    "/community/posts",
    response_model=CommunityPostRead,
    status_code=201,
)
def create_community_post(
    post: CommunityPostCreate,
    db: Session = Depends(get_db),
) -> dict:
    recipe = None
    if post.recipe_id is not None:
        recipe = (
            db.query(Recipe)
            .options(selectinload(Recipe.ingredients).selectinload(RecipeIngredient.ingredient))
            .filter(Recipe.id == post.recipe_id)
            .first()
        )
        if recipe is None:
            raise HTTPException(status_code=404, detail="Recipe not found.")

        existing_post = (
            db.query(CommunityPost)
            .filter(CommunityPost.recipe_id == post.recipe_id)
            .first()
        )
        if existing_post is not None:
            raise HTTPException(
                status_code=400,
                detail="Recipe is already published.",
            )

        title = recipe.title
        ingredients = [
            recipe_ingredient.ingredient.name
            for recipe_ingredient in sorted(
                recipe.ingredients,
                key=lambda item: item.ingredient.name,
            )
        ]
    else:
        title = post.title
        ingredients = normalize_supported_community_ingredients(post.ingredients)

    community_post = CommunityPost(
        creator_id=LOCAL_CREATOR_ID,
        creator_name=LOCAL_CREATOR_NAME,
        recipe_id=post.recipe_id,
        title=title,
        ingredients_json=ingredients,
        caption=post.caption,
    )

    db.add(community_post)
    db.commit()
    db.refresh(community_post)

    if recipe is not None:
        community_post.recipe = recipe

    return serialize_community_post(community_post, db)


@router.get("/community/posts", response_model=list[CommunityPostRead])
def list_community_posts(db: Session = Depends(get_db)) -> list[dict]:
    posts = (
        db.query(CommunityPost)
        .options(
            selectinload(CommunityPost.recipe)
            .selectinload(Recipe.ingredients)
            .selectinload(RecipeIngredient.ingredient)
        )
        .order_by(CommunityPost.created_at.desc())
        .all()
    )
    return [serialize_community_post(post, db) for post in posts]


@router.patch("/community/posts/{post_id}", response_model=CommunityPostRead)
def update_community_post(
    post_id: int,
    post: CommunityPostUpdate,
    creator_id: str = LOCAL_CREATOR_ID,
    db: Session = Depends(get_db),
) -> dict:
    community_post = (
        db.query(CommunityPost)
        .options(
            selectinload(CommunityPost.recipe)
            .selectinload(Recipe.ingredients)
            .selectinload(RecipeIngredient.ingredient)
        )
        .filter(CommunityPost.id == post_id)
        .first()
    )

    if community_post is None:
        raise HTTPException(status_code=404, detail="Community post not found.")

    if community_post.creator_id != creator_id:
        raise HTTPException(
            status_code=403,
            detail="Only the post creator can update this post.",
        )

    if community_post.recipe_id is None:
        community_post.title = post.title
        community_post.ingredients_json = normalize_supported_community_ingredients(
            post.ingredients
        )

    community_post.caption = post.caption
    community_post.updated_at = datetime.now(timezone.utc)

    db.commit()
    db.refresh(community_post)

    return serialize_community_post(community_post, db)


@router.delete("/community/posts/{post_id}")
def delete_community_post(
    post_id: int,
    creator_id: str = LOCAL_CREATOR_ID,
    db: Session = Depends(get_db),
) -> dict[str, str]:
    community_post = db.get(CommunityPost, post_id)

    if community_post is None:
        raise HTTPException(status_code=404, detail="Community post not found.")

    if community_post.creator_id != creator_id:
        raise HTTPException(
            status_code=403,
            detail="Only the post creator can delete this post.",
        )

    db.delete(community_post)
    db.commit()

    return {"message": "Community post deleted."}


@router.post(
    "/community/posts/{post_id}/comments",
    response_model=CommunityCommentRead,
    status_code=201,
)
def create_community_comment(
    post_id: int,
    comment: CommunityCommentCreate,
    db: Session = Depends(get_db),
) -> CommunityComment:
    community_post = db.get(CommunityPost, post_id)

    if community_post is None:
        raise HTTPException(status_code=404, detail="Community post not found.")

    community_comment = CommunityComment(
        post_id=post_id,
        creator_id=LOCAL_CREATOR_ID,
        creator_name=LOCAL_CREATOR_NAME,
        comment_text=comment.comment_text,
    )

    db.add(community_comment)
    if comment.rating is not None:
        linked_recipe_id = get_linked_recipe_id(community_post)
        if linked_recipe_id is None:
            raise HTTPException(
                status_code=400,
                detail="Ratings require a linked recipe.",
            )
        upsert_recipe_rating(db, linked_recipe_id, comment.rating)
    db.commit()
    db.refresh(community_comment)

    return community_comment


@router.get(
    "/community/posts/{post_id}/comments",
    response_model=list[CommunityCommentRead],
)
def list_community_comments(
    post_id: int,
    db: Session = Depends(get_db),
) -> list[CommunityComment]:
    community_post = db.get(CommunityPost, post_id)

    if community_post is None:
        raise HTTPException(status_code=404, detail="Community post not found.")

    return (
        db.query(CommunityComment)
        .filter(CommunityComment.post_id == post_id)
        .order_by(CommunityComment.created_at.asc())
        .all()
    )


@router.patch(
    "/community/comments/{comment_id}",
    response_model=CommunityCommentRead,
)
def update_community_comment(
    comment_id: int,
    comment: CommunityCommentUpdate,
    creator_id: str = LOCAL_CREATOR_ID,
    db: Session = Depends(get_db),
) -> CommunityComment:
    community_comment = db.get(CommunityComment, comment_id)

    if community_comment is None:
        raise HTTPException(status_code=404, detail="Community comment not found.")

    if community_comment.creator_id != creator_id:
        raise HTTPException(
            status_code=403,
            detail="Only the comment creator can update this comment.",
        )

    community_comment.comment_text = comment.comment_text
    community_comment.updated_at = datetime.now(timezone.utc)

    if comment.rating is not None:
        linked_recipe_id = get_linked_recipe_id(community_comment.post)
        if linked_recipe_id is None:
            raise HTTPException(
                status_code=400,
                detail="Ratings require a linked recipe.",
            )
        upsert_recipe_rating(db, linked_recipe_id, comment.rating)

    db.commit()
    db.refresh(community_comment)

    return community_comment


@router.delete("/community/comments/{comment_id}")
def delete_community_comment(
    comment_id: int,
    creator_id: str = LOCAL_CREATOR_ID,
    db: Session = Depends(get_db),
) -> dict[str, str]:
    community_comment = db.get(CommunityComment, comment_id)

    if community_comment is None:
        raise HTTPException(status_code=404, detail="Community comment not found.")

    if community_comment.creator_id != creator_id:
        raise HTTPException(
            status_code=403,
            detail="Only the comment creator can delete this comment.",
        )

    db.delete(community_comment)
    db.commit()

    return {"message": "Community comment deleted."}
