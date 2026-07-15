from collections.abc import Generator

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.orm import sessionmaker

from app.database import Base
from app.database import get_db
from app.main import app


@pytest.fixture()
def client(tmp_path) -> Generator[TestClient]:
    database_url = f"sqlite:///{tmp_path / 'test_smart_pantry.db'}"
    engine = create_engine(
        database_url,
        connect_args={"check_same_thread": False},
    )
    testing_session_local = sessionmaker(
        autocommit=False,
        autoflush=False,
        bind=engine,
    )

    Base.metadata.create_all(bind=engine)

    def override_get_db() -> Generator[Session]:
        db = testing_session_local()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db

    with TestClient(app) as test_client:
        yield test_client

    app.dependency_overrides.clear()
    Base.metadata.drop_all(bind=engine)


def community_post_payload() -> dict[str, object]:
    return {
        "title": "Tomato Egg Rice",
        "ingredients": ["egg", "tomatoes", "rice"],
        "caption": "Simple pantry dinner.",
    }


def create_community_post(client: TestClient) -> dict:
    response = client.post("/community/posts", json=community_post_payload())

    assert response.status_code == 201

    return response.json()


def create_community_comment(client: TestClient, post_id: int) -> dict:
    response = client.post(
        f"/community/posts/{post_id}/comments",
        json={"comment_text": "This looks easy to make."},
    )

    assert response.status_code == 201

    return response.json()


def test_create_community_post_uses_local_identity_and_catalogue_ingredients(
    client: TestClient,
) -> None:
    response = client.post("/community/posts", json=community_post_payload())

    assert response.status_code == 201
    body = response.json()
    assert body["id"] == 1
    assert body["creator_id"] == "local-user"
    assert body["creator_name"] == "Local User"
    assert body["title"] == "Tomato Egg Rice"
    assert body["ingredients_json"] == ["egg", "tomato", "rice"]
    assert body["caption"] == "Simple pantry dinner."
    assert "created_at" in body
    assert "updated_at" in body


def test_create_community_post_rejects_blank_title(client: TestClient) -> None:
    payload = community_post_payload()
    payload["title"] = " "

    response = client.post("/community/posts", json=payload)

    assert response.status_code == 422


def test_create_community_post_rejects_empty_ingredients(
    client: TestClient,
) -> None:
    payload = community_post_payload()
    payload["ingredients"] = []

    response = client.post("/community/posts", json=payload)

    assert response.status_code == 422


def test_create_community_post_rejects_unsupported_ingredient(
    client: TestClient,
) -> None:
    payload = community_post_payload()
    payload["ingredients"] = ["egg", "dragonfruit"]

    response = client.post("/community/posts", json=payload)

    assert response.status_code == 400
    assert response.json() == {"detail": "Unsupported ingredient: dragonfruit"}


def test_create_community_post_rejects_long_caption(client: TestClient) -> None:
    payload = community_post_payload()
    payload["caption"] = "a" * 201

    response = client.post("/community/posts", json=payload)

    assert response.status_code == 422


def test_list_community_posts_returns_newest_first(client: TestClient) -> None:
    client.post("/community/posts", json=community_post_payload())
    second_payload = community_post_payload()
    second_payload["title"] = "Garlic Spinach"
    second_payload["ingredients"] = ["spinach", "garlic"]
    client.post("/community/posts", json=second_payload)

    response = client.get("/community/posts")

    assert response.status_code == 200
    body = response.json()
    assert [post["title"] for post in body] == [
        "Garlic Spinach",
        "Tomato Egg Rice",
    ]


def test_update_community_post_with_local_creator(client: TestClient) -> None:
    post = create_community_post(client)
    payload = {
        "title": "Garlic Spinach Bowl",
        "ingredients": ["spinach", "garlic", "rice"],
        "caption": "Updated with greens.",
    }

    response = client.patch(f"/community/posts/{post['id']}", json=payload)

    assert response.status_code == 200
    body = response.json()
    assert body["id"] == post["id"]
    assert body["creator_id"] == "local-user"
    assert body["title"] == "Garlic Spinach Bowl"
    assert body["ingredients_json"] == ["spinach", "garlic", "rice"]
    assert body["caption"] == "Updated with greens."
    assert body["created_at"] == post["created_at"]
    assert body["updated_at"] != post["updated_at"]


def test_update_community_post_rejects_invalid_payload(client: TestClient) -> None:
    post = create_community_post(client)
    payload = community_post_payload()
    payload["title"] = " "

    response = client.patch(f"/community/posts/{post['id']}", json=payload)

    assert response.status_code == 422


def test_update_community_post_rejects_different_creator(
    client: TestClient,
) -> None:
    post = create_community_post(client)
    payload = community_post_payload()
    payload["title"] = "Other User Edit"

    response = client.patch(
        f"/community/posts/{post['id']}",
        params={"creator_id": "other-user"},
        json=payload,
    )

    assert response.status_code == 403
    assert response.json() == {
        "detail": "Only the post creator can update this post.",
    }


def test_update_missing_community_post_returns_404(client: TestClient) -> None:
    response = client.patch("/community/posts/999", json=community_post_payload())

    assert response.status_code == 404
    assert response.json() == {"detail": "Community post not found."}


def test_delete_community_post_with_local_creator(client: TestClient) -> None:
    post = create_community_post(client)

    delete_response = client.delete(f"/community/posts/{post['id']}")
    list_response = client.get("/community/posts")

    assert delete_response.status_code == 200
    assert delete_response.json() == {"message": "Community post deleted."}
    assert list_response.json() == []


def test_delete_community_post_rejects_different_creator(
    client: TestClient,
) -> None:
    post = create_community_post(client)

    response = client.delete(
        f"/community/posts/{post['id']}",
        params={"creator_id": "other-user"},
    )

    assert response.status_code == 403
    assert response.json() == {
        "detail": "Only the post creator can delete this post.",
    }


def test_delete_missing_community_post_returns_404(client: TestClient) -> None:
    response = client.delete("/community/posts/999")

    assert response.status_code == 404
    assert response.json() == {"detail": "Community post not found."}


def test_create_community_comment_uses_local_identity(client: TestClient) -> None:
    post = create_community_post(client)

    response = client.post(
        f"/community/posts/{post['id']}/comments",
        json={"comment_text": "  This looks easy to make.  "},
    )

    assert response.status_code == 201
    body = response.json()
    assert body["id"] == 1
    assert body["post_id"] == post["id"]
    assert body["creator_id"] == "local-user"
    assert body["creator_name"] == "Local User"
    assert body["comment_text"] == "This looks easy to make."
    assert "created_at" in body
    assert "updated_at" in body


def test_list_community_comments_returns_oldest_first(client: TestClient) -> None:
    post = create_community_post(client)
    client.post(
        f"/community/posts/{post['id']}/comments",
        json={"comment_text": "First comment."},
    )
    client.post(
        f"/community/posts/{post['id']}/comments",
        json={"comment_text": "Second comment."},
    )

    response = client.get(f"/community/posts/{post['id']}/comments")

    assert response.status_code == 200
    assert [comment["comment_text"] for comment in response.json()] == [
        "First comment.",
        "Second comment.",
    ]


def test_update_own_community_comment(client: TestClient) -> None:
    post = create_community_post(client)
    comment = create_community_comment(client, post["id"])

    response = client.patch(
        f"/community/comments/{comment['id']}",
        json={"comment_text": "Updated comment."},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["id"] == comment["id"]
    assert body["post_id"] == post["id"]
    assert body["comment_text"] == "Updated comment."
    assert body["created_at"] == comment["created_at"]
    assert body["updated_at"] != comment["updated_at"]


def test_delete_own_community_comment(client: TestClient) -> None:
    post = create_community_post(client)
    comment = create_community_comment(client, post["id"])

    delete_response = client.delete(f"/community/comments/{comment['id']}")
    list_response = client.get(f"/community/posts/{post['id']}/comments")

    assert delete_response.status_code == 200
    assert delete_response.json() == {"message": "Community comment deleted."}
    assert list_response.json() == []


def test_create_community_comment_rejects_empty_text(client: TestClient) -> None:
    post = create_community_post(client)

    response = client.post(
        f"/community/posts/{post['id']}/comments",
        json={"comment_text": " "},
    )

    assert response.status_code == 422


def test_create_community_comment_for_missing_post_returns_404(
    client: TestClient,
) -> None:
    response = client.post(
        "/community/posts/999/comments",
        json={"comment_text": "Missing post comment."},
    )

    assert response.status_code == 404
    assert response.json() == {"detail": "Community post not found."}


def test_list_community_comments_for_missing_post_returns_404(
    client: TestClient,
) -> None:
    response = client.get("/community/posts/999/comments")

    assert response.status_code == 404
    assert response.json() == {"detail": "Community post not found."}


def test_update_missing_community_comment_returns_404(client: TestClient) -> None:
    response = client.patch(
        "/community/comments/999",
        json={"comment_text": "Updated comment."},
    )

    assert response.status_code == 404
    assert response.json() == {"detail": "Community comment not found."}


def test_delete_missing_community_comment_returns_404(client: TestClient) -> None:
    response = client.delete("/community/comments/999")

    assert response.status_code == 404
    assert response.json() == {"detail": "Community comment not found."}


def test_update_community_comment_rejects_different_creator(
    client: TestClient,
) -> None:
    post = create_community_post(client)
    comment = create_community_comment(client, post["id"])

    response = client.patch(
        f"/community/comments/{comment['id']}",
        params={"creator_id": "other-user"},
        json={"comment_text": "Other user edit."},
    )

    assert response.status_code == 403
    assert response.json() == {
        "detail": "Only the comment creator can update this comment.",
    }


def test_delete_community_comment_rejects_different_creator(
    client: TestClient,
) -> None:
    post = create_community_post(client)
    comment = create_community_comment(client, post["id"])

    response = client.delete(
        f"/community/comments/{comment['id']}",
        params={"creator_id": "other-user"},
    )

    assert response.status_code == 403
    assert response.json() == {
        "detail": "Only the comment creator can delete this comment.",
    }


def test_delete_community_post_deletes_comments(client: TestClient) -> None:
    post = create_community_post(client)
    comment = create_community_comment(client, post["id"])

    post_delete_response = client.delete(f"/community/posts/{post['id']}")
    comment_delete_response = client.delete(f"/community/comments/{comment['id']}")

    assert post_delete_response.status_code == 200
    assert comment_delete_response.status_code == 404
    assert comment_delete_response.json() == {
        "detail": "Community comment not found.",
    }
