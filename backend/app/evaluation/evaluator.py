import json
import logging
import math
import os
import re
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

from nltk.translate.bleu_score import SmoothingFunction, sentence_bleu
from nltk.translate.meteor_score import meteor_score
from PIL import Image

from app.core.styles import ALLOWED_STYLES
from app.services.clip_service import candidate_labels, clip_service
from app.services.llm_service import llm_service
from app.services.prompt_service import build_prompt


logger = logging.getLogger(__name__)

DEFAULT_MAX_IMAGES = 30
DEFAULT_SAMPLE_LIMIT = 5
DEFAULT_LLM_CONCURRENCY = 2
BACKEND_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_COCO_ANNOTATIONS_PATH = BACKEND_ROOT / "data" / "coco" / "annotations" / "captions_val2017.json"
DEFAULT_COCO_IMAGES_DIR = BACKEND_ROOT / "data" / "coco" / "val2017"

VACATION_TRAVEL_KEYWORDS = {
    "beach",
    "sea",
    "ocean",
    "mountain",
    "mountains",
    "sunset",
    "lake",
    "sky",
    "nature",
    "forest",
    "snow",
    "travel",
    "vacation",
}

SIMILAR_LABEL_TERMS = {
    "ocean": "sea",
    "mountain": "mountains",
    "trees": "forest",
    "tree": "forest",
    "woods": "forest",
    "woodland": "forest",
    "seaside": "beach",
    "shore": "beach",
    "shoreline": "beach",
}


class _NoSynonymWordNet:
    def synsets(self, *_args, **_kwargs):
        return []


def load_filtered_coco_subset(
    annotations_path: str | Path | None = None,
    images_dir: str | Path | None = None,
    max_images: int = DEFAULT_MAX_IMAGES,
) -> list[tuple[str, list[str]]]:
    annotations_file = _resolve_path(
        annotations_path,
        env_name="COCO_ANNOTATIONS_PATH",
        default_path=DEFAULT_COCO_ANNOTATIONS_PATH,
    )
    image_root = _resolve_path(
        images_dir,
        env_name="COCO_IMAGES_DIR",
        default_path=DEFAULT_COCO_IMAGES_DIR,
    )

    if max_images < 1:
        raise ValueError("max_images must be at least 1.")
    if max_images > 50:
        raise ValueError("max_images cannot be greater than 50 for evaluation runs.")
    if not annotations_file.exists():
        raise FileNotFoundError(f"COCO annotations file was not found: {annotations_file}")
    if not image_root.exists():
        raise FileNotFoundError(f"COCO images directory was not found: {image_root}")

    logger.info("Loading COCO captions from %s", annotations_file)
    with annotations_file.open("r", encoding="utf-8") as file:
        coco = json.load(file)

    image_filenames = {
        image["id"]: image["file_name"]
        for image in coco.get("images", [])
        if "id" in image and "file_name" in image
    }
    captions_by_image_id: dict[int, list[str]] = defaultdict(list)
    for annotation in coco.get("annotations", []):
        image_id = annotation.get("image_id")
        caption = annotation.get("caption")
        if image_id in image_filenames and caption:
            captions_by_image_id[image_id].append(caption)

    logger.info(
        "Filtering COCO validation set: %d images, max_images=%d",
        len(image_filenames),
        max_images,
    )
    filtered_subset: list[tuple[str, list[str]]] = []
    for image_id in sorted(image_filenames):
        references = captions_by_image_id.get(image_id, [])
        if not references:
            continue
        if not _matches_vacation_travel_content(references):
            continue
        if not _is_compatible_with_candidate_labels(references):
            continue

        image_path = image_root / image_filenames[image_id]
        if not image_path.exists():
            continue

        filtered_subset.append((str(image_path), references))
        if len(filtered_subset) >= max_images:
            break

    logger.info("Selected %d filtered COCO images for evaluation", len(filtered_subset))
    return filtered_subset


def run_evaluation(
    annotations_path: str | Path | None = None,
    images_dir: str | Path | None = None,
    max_images: int = DEFAULT_MAX_IMAGES,
    styles: list[str] | tuple[str, ...] | None = None,
    sample_limit: int = DEFAULT_SAMPLE_LIMIT,
    llm_concurrency: int = DEFAULT_LLM_CONCURRENCY,
) -> dict[str, Any]:
    selected_styles = tuple(styles or ALLOWED_STYLES)
    invalid_styles = [style for style in selected_styles if style not in ALLOWED_STYLES]
    if invalid_styles:
        allowed = ", ".join(ALLOWED_STYLES)
        raise ValueError(f"Invalid styles: {', '.join(invalid_styles)}. Allowed styles: {allowed}.")
    if llm_concurrency < 1:
        raise ValueError("llm_concurrency must be at least 1.")

    subset = load_filtered_coco_subset(
        annotations_path=annotations_path,
        images_dir=images_dir,
        max_images=max_images,
    )

    caption_jobs: list[dict[str, Any]] = []
    logger.info(
        "Starting CLIP extraction for %d images and styles=%s",
        len(subset),
        ", ".join(selected_styles),
    )
    for image_index, (image_path, reference_captions) in enumerate(subset, start=1):
        logger.info("Extracting concepts %d/%d: %s", image_index, len(subset), image_path)
        with Image.open(image_path) as image:
            pil_image = image.convert("RGB")
            concepts = clip_service.extract_concepts(pil_image, top_k=5)

        logger.info("Concepts %d/%d: %s", image_index, len(subset), ", ".join(concepts))
        for style in selected_styles:
            prompt = build_prompt(concepts, style)
            caption_jobs.append(
                {
                    "image_path": image_path,
                    "style": style,
                    "concepts": concepts,
                    "prompt": prompt,
                    "reference": reference_captions,
                }
            )

    logger.info(
        "Starting caption generation for %d jobs with llm_concurrency=%d",
        len(caption_jobs),
        llm_concurrency,
    )
    predictions = _generate_captions(caption_jobs, llm_concurrency)
    logger.info("Caption generation finished. Computing metrics for %d predictions", len(predictions))
    metrics = _metrics_for_predictions(predictions)
    logger.info(
        "Evaluation finished: images=%d predictions=%d BLEU=%.4f METEOR=%.4f CIDEr=%.4f",
        len(subset),
        len(predictions),
        metrics["BLEU"],
        metrics["METEOR"],
        metrics["CIDEr"],
    )

    return {
        "num_images": len(subset),
        "num_predictions": len(predictions),
        "styles": list(selected_styles),
        "llm_concurrency": llm_concurrency,
        "BLEU": metrics["BLEU"],
        "METEOR": metrics["METEOR"],
        "CIDEr": metrics["CIDEr"],
        "by_style": {
            style: _metrics_for_predictions(
                [item for item in predictions if item["style"] == style]
            )
            for style in selected_styles
        },
        "samples": [
            {
                "style": item["style"],
                "concepts": item["concepts"],
                "generated": item["generated"],
                "reference": item["reference"],
            }
            for item in predictions[:sample_limit]
        ],
    }


def _generate_captions(caption_jobs: list[dict[str, Any]], llm_concurrency: int) -> list[dict[str, Any]]:
    if llm_concurrency == 1:
        predictions = []
        for index, job in enumerate(caption_jobs, start=1):
            predictions.append(_generate_caption_for_job(job, index, len(caption_jobs)))
        return predictions

    predictions: list[dict[str, Any] | None] = [None] * len(caption_jobs)
    with ThreadPoolExecutor(max_workers=llm_concurrency) as executor:
        future_to_index = {
            executor.submit(_generate_caption_for_job, job, index + 1, len(caption_jobs)): index
            for index, job in enumerate(caption_jobs)
        }
        for future in as_completed(future_to_index):
            index = future_to_index[future]
            predictions[index] = future.result()
            logger.info("Completed LLM job %d/%d", index + 1, len(caption_jobs))

    return [prediction for prediction in predictions if prediction is not None]


def _generate_caption_for_job(job: dict[str, Any], index: int, total: int) -> dict[str, Any]:
    logger.info("Starting LLM job %d/%d for style=%s", index, total, job["style"])
    prediction = dict(job)
    prediction["generated"] = llm_service.generate_caption(job["prompt"])
    prediction.pop("prompt", None)
    return prediction


def _resolve_path(path: str | Path | None, env_name: str, default_path: Path) -> Path:
    configured_path = path or os.getenv(env_name)
    return Path(configured_path).expanduser().resolve() if configured_path else default_path.resolve()


def _matches_vacation_travel_content(captions: list[str]) -> bool:
    return any(_caption_terms(caption) & VACATION_TRAVEL_KEYWORDS for caption in captions)


def _is_compatible_with_candidate_labels(captions: list[str]) -> bool:
    labels = set(candidate_labels)
    for caption in captions:
        terms = _caption_terms(caption)
        normalized_terms = terms | {SIMILAR_LABEL_TERMS[term] for term in terms if term in SIMILAR_LABEL_TERMS}
        if normalized_terms & labels:
            return True
    return False


def _caption_terms(caption: str) -> set[str]:
    return set(re.findall(r"[a-z]+", caption.lower()))


def _tokenize(text: str) -> list[str]:
    return re.findall(r"[a-z0-9]+", text.lower())


def _bleu_score(generated: str, references: list[str]) -> float:
    tokenized_references = [_tokenize(reference) for reference in references]
    tokenized_generated = _tokenize(generated)
    if not tokenized_generated:
        return 0.0

    smoothing = SmoothingFunction().method1
    return float(
        sentence_bleu(
            tokenized_references,
            tokenized_generated,
            weights=(0.25, 0.25, 0.25, 0.25),
            smoothing_function=smoothing,
        )
    )


def _meteor_score(generated: str, references: list[str]) -> float:
    tokenized_references = [_tokenize(reference) for reference in references]
    tokenized_generated = _tokenize(generated)
    if not tokenized_generated:
        return 0.0

    return float(
        meteor_score(
            tokenized_references,
            tokenized_generated,
            wordnet=_NoSynonymWordNet(),
        )
    )


def _metrics_for_predictions(predictions: list[dict[str, Any]]) -> dict[str, float]:
    bleu_scores = [_bleu_score(item["generated"], item["reference"]) for item in predictions]
    meteor_scores = [_meteor_score(item["generated"], item["reference"]) for item in predictions]
    return {
        "BLEU": _average(bleu_scores),
        "METEOR": _average(meteor_scores),
        "CIDEr": _cider_score(predictions),
    }


def _cider_score(predictions: list[dict[str, Any]]) -> float:
    if not predictions:
        return 0.0

    documents = [item["reference"] + [item["generated"]] for item in predictions]
    document_frequencies = _document_frequencies(documents)
    document_count = len(documents)

    scores = []
    for item in predictions:
        generated_vectors = _tfidf_vectors(item["generated"], document_frequencies, document_count)
        reference_vectors = [
            _tfidf_vectors(reference, document_frequencies, document_count)
            for reference in item["reference"]
        ]

        ngram_scores = []
        for n in range(1, 5):
            generated_vector = generated_vectors[n]
            if not generated_vector:
                ngram_scores.append(0.0)
                continue
            similarities = [
                _cosine_similarity(generated_vector, reference_vectors_by_n[n])
                for reference_vectors_by_n in reference_vectors
            ]
            ngram_scores.append(_average(similarities))

        scores.append(10.0 * _average(ngram_scores))

    return _average(scores)


def _document_frequencies(documents: list[list[str]]) -> dict[int, Counter[tuple[str, ...]]]:
    frequencies: dict[int, Counter[tuple[str, ...]]] = {n: Counter() for n in range(1, 5)}
    for captions in documents:
        seen_by_n = {n: set() for n in range(1, 5)}
        for caption in captions:
            tokens = _tokenize(caption)
            for n in range(1, 5):
                seen_by_n[n].update(_ngrams(tokens, n))
        for n in range(1, 5):
            frequencies[n].update(seen_by_n[n])
    return frequencies


def _tfidf_vectors(
    caption: str,
    document_frequencies: dict[int, Counter[tuple[str, ...]]],
    document_count: int,
) -> dict[int, dict[tuple[str, ...], float]]:
    tokens = _tokenize(caption)
    vectors = {}
    for n in range(1, 5):
        counts = Counter(_ngrams(tokens, n))
        total = sum(counts.values())
        if total == 0:
            vectors[n] = {}
            continue

        vectors[n] = {
            ngram: (count / total) * math.log((document_count + 1) / (document_frequencies[n][ngram] + 1))
            for ngram, count in counts.items()
        }
    return vectors


def _ngrams(tokens: list[str], n: int) -> list[tuple[str, ...]]:
    if len(tokens) < n:
        return []
    return [tuple(tokens[index : index + n]) for index in range(len(tokens) - n + 1)]


def _cosine_similarity(left: dict[tuple[str, ...], float], right: dict[tuple[str, ...], float]) -> float:
    if not left or not right:
        return 0.0

    shared = set(left) & set(right)
    numerator = sum(left[key] * right[key] for key in shared)
    left_norm = math.sqrt(sum(value * value for value in left.values()))
    right_norm = math.sqrt(sum(value * value for value in right.values()))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return numerator / (left_norm * right_norm)


def _average(values: list[float]) -> float:
    if not values:
        return 0.0
    return sum(values) / len(values)
