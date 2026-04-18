import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


DATA_PATH = Path(__file__).resolve().parents[2] / "data" / "generated_examples.json"


def save_example(example: dict[str, Any]) -> None:
    DATA_PATH.parent.mkdir(parents=True, exist_ok=True)

    examples = []
    if DATA_PATH.exists():
        try:
            examples = json.loads(DATA_PATH.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            examples = []

    examples.append(
        {
            "created_at": datetime.now(UTC).isoformat(),
            **example,
        }
    )
    DATA_PATH.write_text(
        json.dumps(examples, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
