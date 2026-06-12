import json
import importlib
import sys
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

# test_api_routes injects stubs into sys.modules, so reload the real helper module here.
sys.modules.pop("app.services.history_service", None)
history_service = importlib.import_module("app.services.history_service")

from app.services.prompt_service import build_prompt


class HelperUnitTests(unittest.TestCase):
    def test_build_prompt_includes_style_instruction_and_concepts(self):
        prompt = build_prompt(["lake", "sunset"], "poetic")

        self.assertIn("Write a short poetic Instagram caption.", prompt)
        self.assertIn("The image contains the following elements: lake, sunset.", prompt)
        self.assertIn("Return only the caption.", prompt)

    def test_save_example_appends_timestamped_payload(self):
        data_path = Path(__file__).resolve().parent / f"_history_service_test_{uuid.uuid4().hex}.json"

        try:
            with patch.object(history_service, "DATA_PATH", data_path):
                history_service.save_example({"style": "poetic", "caption": "first"})
                history_service.save_example({"style": "travel", "caption": "second"})

            saved = json.loads(data_path.read_text(encoding="utf-8"))
        finally:
            if data_path.exists():
                try:
                    data_path.unlink()
                except PermissionError:
                    pass

        self.assertEqual(len(saved), 2)
        self.assertEqual(saved[0]["style"], "poetic")
        self.assertEqual(saved[1]["caption"], "second")
        self.assertIn("created_at", saved[0])
        self.assertIn("created_at", saved[1])


if __name__ == "__main__":
    unittest.main()
