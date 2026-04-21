from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.core.styles import ALLOWED_STYLES
from app.evaluation.evaluator import run_evaluation
from app.services.llm_service import LLMServiceError


router = APIRouter()


class EvaluationRequest(BaseModel):
    annotations_path: str | None = None
    images_dir: str | None = None
    max_images: int = Field(default=30, ge=1, le=50)
    styles: list[str] | None = None
    sample_limit: int = Field(default=5, ge=0, le=20)
    llm_concurrency: int = Field(default=2, ge=1, le=8)


@router.post("/evaluate")
def evaluate(request: EvaluationRequest | None = None):
    request = request or EvaluationRequest()
    styles = request.styles or list(ALLOWED_STYLES)

    try:
        return run_evaluation(
            annotations_path=request.annotations_path,
            images_dir=request.images_dir,
            max_images=request.max_images,
            styles=styles,
            sample_limit=request.sample_limit,
            llm_concurrency=request.llm_concurrency,
        )
    except FileNotFoundError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except LLMServiceError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
