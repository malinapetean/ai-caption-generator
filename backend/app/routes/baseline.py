import io

from fastapi import APIRouter, File, UploadFile
from PIL import Image

from app.models.caption import BaselineResponse
from app.services.blip_service import blip_service

router = APIRouter()


@router.post("/generate-baseline", response_model=BaselineResponse)
async def generate_baseline(image: UploadFile = File(...)):
    content = await image.read()
    pil_image = Image.open(io.BytesIO(content)).convert("RGB")

    caption = blip_service.generate_caption(pil_image)
    return {"caption": caption}
