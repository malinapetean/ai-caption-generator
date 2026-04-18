import os

import httpx
from dotenv import load_dotenv


load_dotenv()

DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"
DEFAULT_OLLAMA_MODEL = "llama3.2"


class LLMServiceError(RuntimeError):
    pass


class LLMService:
    def __init__(self):
        self.provider = os.getenv("LLM_PROVIDER", "ollama").lower()
        self.timeout = float(os.getenv("LLM_TIMEOUT_SECONDS", "60"))

    def generate_caption(self, prompt: str) -> str:
        if self.provider in {"api", "openai"}:
            caption = self._generate_with_openai_compatible_api(prompt)
        else:
            caption = self._generate_with_ollama(prompt)

        return self._clean_caption(caption)

    def health(self) -> dict:
        if self.provider in {"api", "openai"}:
            return self._api_health()

        return self._ollama_health()

    def _generate_with_ollama(self, prompt: str) -> str:
        base_url = os.getenv("OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL).rstrip("/")
        model = os.getenv("OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL)

        payload = {
            "model": model,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": 0.8,
                "num_predict": 80,
            },
        }

        response = self._post_ollama_generate(base_url, payload)

        data = response.json()
        caption = data.get("response")
        if not caption:
            raise LLMServiceError("Ollama returned an empty caption.")

        return caption

    def _post_ollama_generate(self, base_url: str, payload: dict) -> httpx.Response:
        model = payload["model"]
        try:
            response = httpx.post(
                f"{base_url}/api/generate",
                json=payload,
                timeout=self.timeout,
            )
            response.raise_for_status()
            return response
        except httpx.ConnectError as exc:
            raise LLMServiceError(
                f"Ollama is not reachable at {base_url}. Start Ollama and try again."
            ) from exc
        except httpx.TimeoutException as exc:
            raise LLMServiceError(
                f"Ollama timed out after {self.timeout:g} seconds while using model '{model}'."
            ) from exc
        except httpx.HTTPStatusError as exc:
            detail = exc.response.text.strip()
            if "not found" in detail.lower() or exc.response.status_code == 404:
                raise LLMServiceError(
                    f"Ollama model '{model}' is not available. Run: ollama pull {model}"
                ) from exc
            raise LLMServiceError(
                f"Ollama returned HTTP {exc.response.status_code}: {detail or exc.response.reason_phrase}"
            ) from exc
        except httpx.HTTPError as exc:
            raise LLMServiceError(f"Ollama request failed: {exc}") from exc

    def _generate_with_openai_compatible_api(self, prompt: str) -> str:
        api_key = os.getenv("LLM_API_KEY") or os.getenv("OPENAI_API_KEY")
        model = os.getenv("LLM_API_MODEL") or os.getenv("OPENAI_MODEL")
        base_url = (
            os.getenv("LLM_API_BASE_URL")
            or os.getenv("OPENAI_BASE_URL")
            or "https://api.openai.com/v1"
        ).rstrip("/")

        if not api_key:
            raise LLMServiceError("LLM_API_KEY or OPENAI_API_KEY is required when using the API provider.")
        if not model:
            raise LLMServiceError("LLM_API_MODEL or OPENAI_MODEL is required when using the API provider.")

        payload = {
            "model": model,
            "messages": [
                {
                    "role": "system",
                    "content": "You write short Instagram captions. Return only the caption.",
                },
                {"role": "user", "content": prompt},
            ],
            "temperature": 0.8,
            "max_tokens": 80,
        }
        headers = {"Authorization": f"Bearer {api_key}"}

        try:
            response = httpx.post(
                f"{base_url}/chat/completions",
                json=payload,
                headers=headers,
                timeout=self.timeout,
            )
            response.raise_for_status()
        except httpx.TimeoutException as exc:
            raise LLMServiceError(
                f"API provider timed out after {self.timeout:g} seconds."
            ) from exc
        except httpx.HTTPStatusError as exc:
            detail = exc.response.text.strip()
            raise LLMServiceError(
                f"API provider returned HTTP {exc.response.status_code}: {detail or exc.response.reason_phrase}"
            ) from exc
        except httpx.HTTPError as exc:
            raise LLMServiceError(f"API provider request failed: {exc}") from exc

        data = response.json()
        try:
            return data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise LLMServiceError("API response did not include a caption.") from exc

    def _ollama_health(self) -> dict:
        base_url = os.getenv("OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL).rstrip("/")
        model = os.getenv("OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL)

        try:
            response = httpx.get(f"{base_url}/api/tags", timeout=5)
            response.raise_for_status()
        except httpx.ConnectError:
            return {
                "provider": "ollama",
                "ok": False,
                "message": f"Ollama is not reachable at {base_url}.",
                "model": model,
                "base_url": base_url,
            }
        except httpx.TimeoutException:
            return {
                "provider": "ollama",
                "ok": False,
                "message": "Ollama health check timed out.",
                "model": model,
                "base_url": base_url,
            }
        except httpx.HTTPError as exc:
            return {
                "provider": "ollama",
                "ok": False,
                "message": f"Ollama health check failed: {exc}",
                "model": model,
                "base_url": base_url,
            }

        models = response.json().get("models", [])
        names = {item.get("name") for item in models}
        model_is_available = model in names or f"{model}:latest" in names
        if not model_is_available:
            return {
                "provider": "ollama",
                "ok": False,
                "message": f"Ollama is running, but model '{model}' is not pulled.",
                "model": model,
                "base_url": base_url,
            }

        return {
            "provider": "ollama",
            "ok": True,
            "message": "Ollama is reachable and the configured model is available.",
            "model": model,
            "base_url": base_url,
        }

    def _api_health(self) -> dict:
        api_key = os.getenv("LLM_API_KEY") or os.getenv("OPENAI_API_KEY")
        model = os.getenv("LLM_API_MODEL") or os.getenv("OPENAI_MODEL")
        base_url = (
            os.getenv("LLM_API_BASE_URL")
            or os.getenv("OPENAI_BASE_URL")
            or "https://api.openai.com/v1"
        ).rstrip("/")

        if not api_key or not model:
            return {
                "provider": "api",
                "ok": False,
                "message": "API provider is selected, but API key or model is missing.",
                "model": model,
                "base_url": base_url,
            }

        return {
            "provider": "api",
            "ok": True,
            "message": "API provider configuration is present.",
            "model": model,
            "base_url": base_url,
        }

    def _clean_caption(self, caption: str) -> str:
        return caption.strip().strip('"').strip("'")


llm_service = LLMService()
