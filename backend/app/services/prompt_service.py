from app.core.styles import STYLE_INSTRUCTIONS


def build_prompt(concepts: list[str], style: str) -> str:
    concepts_text = ", ".join(concepts)
    instruction = STYLE_INSTRUCTIONS[style]

    prompt = f"""
{instruction}

The image contains the following elements: {concepts_text}.

Return only the caption.
"""
    return prompt
