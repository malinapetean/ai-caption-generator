import io
import sys
import types
import unittest
from pathlib import Path

from PIL import Image
from fastapi.testclient import TestClient


BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))


class StubClipService:
    def __init__(self):
        self.concepts = ["lake", "sunset"]

    def extract_concepts(self, _image, top_k=5):
        return self.concepts[:top_k]


class StubBlipService:
    def __init__(self):
        self.caption = "baseline caption"

    def generate_caption(self, _image):
        return self.caption


class StubLlmService:
    def __init__(self):
        self.caption = "styled caption"
        self.health_payload = {
            "provider": "api",
            "ok": True,
            "message": "healthy",
            "model": "test-model",
            "base_url": "http://example.test",
        }
        self.error = None
        self.prompts = []

    def generate_caption(self, prompt):
        self.prompts.append(prompt)
        if self.error is not None:
            raise self.error
        return self.caption

    def health(self):
        return self.health_payload


class StubHistoryService:
    def __init__(self):
        self.saved_examples = []

    def save_example(self, payload):
        self.saved_examples.append(payload)


class StubEvaluator:
    def __init__(self):
        self.response = {"ok": True}

    def run_evaluation(self, **_kwargs):
        return self.response


clip_stub = StubClipService()
blip_stub = StubBlipService()
llm_stub = StubLlmService()
history_stub = StubHistoryService()
evaluator_stub = StubEvaluator()


class StubLlmServiceError(RuntimeError):
    pass


clip_module = types.ModuleType("app.services.clip_service")
clip_module.clip_service = clip_stub
clip_module.candidate_labels = ["lake", "sunset"]
sys.modules["app.services.clip_service"] = clip_module

blip_module = types.ModuleType("app.services.blip_service")
blip_module.blip_service = blip_stub
sys.modules["app.services.blip_service"] = blip_module

llm_module = types.ModuleType("app.services.llm_service")
llm_module.LLMServiceError = StubLlmServiceError
llm_module.llm_service = llm_stub
sys.modules["app.services.llm_service"] = llm_module

history_module = types.ModuleType("app.services.history_service")
history_module.save_example = history_stub.save_example
sys.modules["app.services.history_service"] = history_module

evaluation_module = types.ModuleType("app.evaluation.evaluator")
evaluation_module.run_evaluation = evaluator_stub.run_evaluation
sys.modules["app.evaluation.evaluator"] = evaluation_module

from app.main import app  # noqa: E402


client = TestClient(app)


def image_bytes():
    buffer = io.BytesIO()
    Image.new("RGB", (2, 2), color="white").save(buffer, format="PNG")
    return buffer.getvalue()


class ApiRouteTests(unittest.TestCase):
    def setUp(self):
        clip_stub.concepts = ["lake", "sunset"]
        blip_stub.caption = "baseline caption"
        llm_stub.caption = "styled caption"
        llm_stub.health_payload = {
            "provider": "api",
            "ok": True,
            "message": "healthy",
            "model": "test-model",
            "base_url": "http://example.test",
        }
        llm_stub.error = None
        llm_stub.prompts.clear()
        history_stub.saved_examples.clear()
        evaluator_stub.response = {"ok": True}

    def test_generate_caption_returns_caption_and_records_example(self):
        response = client.post(
            "/api/generate-caption",
            files={"image": ("lake.png", image_bytes(), "image/png")},
            data={"style": "poetic"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["filename"], "lake.png")
        self.assertEqual(response.json()["style"], "poetic")
        self.assertEqual(response.json()["concepts"], ["lake", "sunset"])
        self.assertEqual(response.json()["caption"], "styled caption")
        self.assertIn("The image contains the following elements: lake, sunset.", response.json()["prompt"])
        self.assertEqual(len(history_stub.saved_examples), 1)
        self.assertEqual(history_stub.saved_examples[0]["caption"], "styled caption")
        self.assertEqual(len(llm_stub.prompts), 1)

    def test_generate_caption_with_invalid_style_returns_bad_request(self):
        response = client.post(
            "/api/generate-caption",
            files={"image": ("lake.png", image_bytes(), "image/png")},
            data={"style": "invalid-style"},
        )

        self.assertEqual(response.status_code, 400)
        self.assertIn("Invalid style 'invalid-style'.", response.json()["detail"])

    def test_generate_caption_with_llm_failure_returns_bad_gateway(self):
        llm_stub.error = StubLlmServiceError("LLM unavailable")

        response = client.post(
            "/api/generate-caption",
            files={"image": ("lake.png", image_bytes(), "image/png")},
            data={"style": "travel"},
        )

        self.assertEqual(response.status_code, 502)
        self.assertEqual(response.json()["detail"], "LLM unavailable")

    def test_generate_baseline_returns_stub_caption(self):
        blip_stub.caption = "simple baseline"

        response = client.post(
            "/api/generate-baseline",
            files={"image": ("lake.png", image_bytes(), "image/png")},
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"caption": "simple baseline"})

    def test_llm_health_returns_service_payload(self):
        response = client.get("/api/health/llm")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), llm_stub.health_payload)


if __name__ == "__main__":
    unittest.main()
