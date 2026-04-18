from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routes.caption import router as caption_router

app = FastAPI(title="Thesis Caption Generator")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
    ],
    allow_origin_regex=r"^http://(localhost|127\.0\.0\.1):\d+$",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(caption_router, prefix="/api")

@app.get("/")
def root():
    return {"message": "Backend is running"}
