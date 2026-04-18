from transformers import CLIPProcessor, CLIPModel
from PIL import Image
import torch

MODEL_NAME = "openai/clip-vit-base-patch32"

candidate_labels = [
    "beach",
    "sea",
    "mountains",
    "sunset",
    "city",
    "street",
    "coffee",
    "food",
    "flowers",
    "nature",
    "forest",
    "snow",
    "travel",
    "vacation",
    "fashion",
    "portrait",
    "sky",
    "lake",
    "building",
    "night"
]

class ClipService:
    def __init__(self):
        self.model = CLIPModel.from_pretrained(MODEL_NAME)
        self.processor = CLIPProcessor.from_pretrained(MODEL_NAME)
        self.model.eval()

    def extract_concepts(self, image: Image.Image, top_k: int = 5):
        with torch.no_grad():
            inputs = self.processor(
                text=candidate_labels,
                images=image,
                return_tensors="pt",
                padding=True,
                truncation=True
            )

            outputs = self.model(**inputs)
            similarity = outputs.logits_per_image[0]
            top_k = min(top_k, len(candidate_labels))
            top_indices = similarity.topk(top_k).indices.tolist()

        return [candidate_labels[i] for i in top_indices]

clip_service = ClipService()
