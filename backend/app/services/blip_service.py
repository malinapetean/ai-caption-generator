from PIL import Image
import torch
from transformers import BlipForConditionalGeneration, BlipProcessor

MODEL_NAME = "Salesforce/blip-image-captioning-base"


class BlipService:
    def __init__(self):
        self.processor = BlipProcessor.from_pretrained(MODEL_NAME)
        self.model = BlipForConditionalGeneration.from_pretrained(MODEL_NAME)
        self.model.eval()

    def generate_caption(self, image: Image.Image) -> str:
        inputs = self.processor(images=image, return_tensors="pt")

        with torch.no_grad():
            output = self.model.generate(**inputs)

        caption = self.processor.decode(output[0], skip_special_tokens=True)
        return caption.strip()


blip_service = BlipService()
