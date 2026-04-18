from pydantic import BaseModel


class CaptionResponse(BaseModel):
    filename: str | None
    style: str
    concepts: list[str]
    prompt: str
    caption: str


class LLMHealthResponse(BaseModel):
    provider: str
    ok: bool
    message: str
    model: str | None = None
    base_url: str | None = None
