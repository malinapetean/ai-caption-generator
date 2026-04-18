from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from PIL import Image
import io

from app.core.styles import ALLOWED_STYLES
from app.models.caption import CaptionResponse, LLMHealthResponse
from app.services.clip_service import clip_service
from app.services.history_service import save_example
from app.services.llm_service import LLMServiceError, llm_service
from app.services.prompt_service import build_prompt

router = APIRouter()

@router.get("/health/llm", response_model=LLMHealthResponse)
def llm_health():
    return llm_service.health()


@router.post("/generate-caption", response_model=CaptionResponse)
async def generate_caption(
    image: UploadFile = File(...),
    style: str = Form(...)
):
    if style not in ALLOWED_STYLES:
        allowed = ", ".join(ALLOWED_STYLES)
        raise HTTPException(
            status_code=400,
            detail=f"Invalid style '{style}'. Allowed styles: {allowed}.",
        )

    content = await image.read()
    pil_image = Image.open(io.BytesIO(content)).convert("RGB")

    concepts = clip_service.extract_concepts(pil_image, top_k=5)
    prompt = build_prompt(concepts, style)
    try:
        caption = llm_service.generate_caption(prompt)
    except LLMServiceError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    result = {
        "filename": image.filename,
        "style": style,
        "concepts": concepts,
        "prompt": prompt,
        "caption": caption
    }
    save_example(result)
    return result
