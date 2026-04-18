import { useEffect, useMemo, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8000";
const STYLES = ["poetic", "travel", "casual", "luxury", "minimalist"];

function App() {
  const [file, setFile] = useState(null);
  const [style, setStyle] = useState("casual");
  const [previewUrl, setPreviewUrl] = useState("");
  const [result, setResult] = useState(null);
  const [health, setHealth] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function checkHealth() {
    try {
      const response = await fetch(`${API_BASE_URL}/api/health/llm`, {
        cache: "no-store",
      });
      const data = await response.json();
      setHealth(data);
    } catch {
      setHealth({
        ok: false,
        message: "Backend is not reachable.",
      });
    }
  }

  useEffect(() => {
    checkHealth();
    const intervalId = window.setInterval(() => {
      checkHealth();
    }, 10000);

    return () => window.clearInterval(intervalId);
  }, []);

  useEffect(() => {
    if (!file) {
      setPreviewUrl("");
      return;
    }

    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const statusText = useMemo(() => {
    if (!health) return "Checking LLM...";
    return health.ok ? "LLM ready" : health.message;
  }, [health]);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setResult(null);

    if (!file) {
      setError("Choose an image first.");
      return;
    }

    const formData = new FormData();
    formData.append("image", file);
    formData.append("style", style);

    setLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/generate-caption`, {
        method: "POST",
        body: formData,
      });
      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.detail || "Caption generation failed.");
      }

      setResult(data);
      setHealth({
        ok: true,
        message: "Backend and LLM are responding.",
        provider: "ollama",
      });
    } catch (err) {
      setError(err.message);
      checkHealth();
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="workspace">
        <form className="control-surface" onSubmit={handleSubmit}>
          <div className="topline">
            <span className={health?.ok ? "status ok" : "status"}>{statusText}</span>
          </div>

          <h1>Caption Generator</h1>

          <label className="upload-zone">
            <input
              type="file"
              accept="image/*"
              onChange={(event) => setFile(event.target.files?.[0] || null)}
            />
            {previewUrl ? (
              <img src={previewUrl} alt="Selected upload preview" />
            ) : (
              <img
                src="https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=80"
                alt="Landscape sample"
              />
            )}
            <span>{file ? file.name : "Choose an image"}</span>
          </label>

          <div className="style-row">
            {STYLES.map((item) => (
              <button
                type="button"
                className={item === style ? "selected" : ""}
                key={item}
                onClick={() => setStyle(item)}
              >
                {item}
              </button>
            ))}
          </div>

          <button className="submit-button" type="submit" disabled={loading}>
            {loading ? "Generating..." : "Generate caption"}
          </button>

          {error && <p className="error">{error}</p>}
        </form>

        <section className="result-panel" aria-live="polite">
          {result ? (
            <>
              <p className="caption">{result.caption}</p>
              <div className="concepts">
                {result.concepts.map((concept) => (
                  <span key={concept}>{concept}</span>
                ))}
              </div>
              <details>
                <summary>Prompt</summary>
                <pre>{result.prompt}</pre>
              </details>
            </>
          ) : (
            <p className="empty-state">Upload an image, choose a style, and generate a caption.</p>
          )}
        </section>
      </section>
    </main>
  );
}

export default App;
