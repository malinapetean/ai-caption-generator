import { useEffect, useState } from "react";
import CaptionCard from "../components/CaptionCard.jsx";
import {
  buildImageUrl,
  generateCaptions,
  getUserImages,
  selectCaption,
} from "../services/api.js";

const STYLE_OPTIONS = ["casual", "poetic", "luxury", "minimalist"];

function DashboardImagePreview({ src, alt, fallbackLabel, className }) {
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    setHidden(false);
  }, [src]);

  if (!src || hidden) {
    return (
      <div
        className={`flex items-center justify-center bg-stone-100 px-6 text-center text-sm font-medium text-stone-400 ${className}`}
      >
        {fallbackLabel}
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      className={className}
      onError={() => setHidden(true)}
    />
  );
}

function Dashboard({ currentUser, onUserChange, onNotify }) {
  const [style, setStyle] = useState(currentUser?.preferredStyle || "casual");
  const [selectedImageId, setSelectedImageId] = useState("");
  const [file, setFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState("");
  const [existingImages, setExistingImages] = useState([]);
  const [imagesLoading, setImagesLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [selectingId, setSelectingId] = useState(null);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);

  useEffect(() => {
    setStyle(currentUser?.preferredStyle || "casual");
  }, [currentUser?.preferredStyle]);

  useEffect(() => {
    if (!file) {
      setPreviewUrl("");
      return undefined;
    }

    const objectUrl = URL.createObjectURL(file);
    setPreviewUrl(objectUrl);

    return () => URL.revokeObjectURL(objectUrl);
  }, [file]);

  useEffect(() => {
    if (!currentUser?.id) {
      return;
    }

    let cancelled = false;

    async function loadImages() {
      setImagesLoading(true);

      try {
        const images = await getUserImages(currentUser.id);
        if (!cancelled) {
          setExistingImages(images);
        }
      } catch (requestError) {
        if (!cancelled) {
          setError(requestError.message);
        }
      } finally {
        if (!cancelled) {
          setImagesLoading(false);
        }
      }
    }

    loadImages();

    return () => {
      cancelled = true;
    };
  }, [currentUser?.id]);

  const selectedExistingImage = existingImages.find(
    (image) => String(image.id) === String(selectedImageId)
  );

  async function refreshImages() {
    if (!currentUser?.id) {
      return;
    }

    const images = await getUserImages(currentUser.id);
    setExistingImages(images);
  }

  function handleFileChange(event) {
    const nextFile = event.target.files?.[0] || null;
    setFile(nextFile);
    setSelectedImageId("");
    setError("");
  }

  function handleExistingImageSelect(imageId) {
    setSelectedImageId(String(imageId));
    setFile(null);
    setError("");
  }

  async function handleGenerate(event) {
    event.preventDefault();
    setError("");
    setResult(null);

    if (!file && !selectedImageId) {
      setError("Choose a new image or reuse one from your library.");
      return;
    }

    setSubmitting(true);

    try {
      const response = await generateCaptions({
        file,
        imageId: selectedImageId || null,
        style: style || "casual",
      });

      setResult(response);
      setSelectedImageId(String(response.imageId));
      onUserChange((previousUser) => ({
        ...(previousUser || {}),
        preferredStyle: response.style || "casual",
      }));
      onNotify("Captions generated.", "success");

      if (file) {
        try {
          await refreshImages();
        } catch {
          onNotify("Captions were generated, but the image library did not refresh.", "info");
        }
      }
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSelectCaption(captionId) {
    setSelectingId(captionId);
    setError("");

    try {
      const selectedCaption = await selectCaption(captionId);
      setResult((previousResult) => {
        if (!previousResult) {
          return previousResult;
        }

        return {
          ...previousResult,
          captions: previousResult.captions.map((caption) => ({
            ...caption,
            selected: caption.id === selectedCaption.id,
          })),
        };
      });
      onNotify("Caption selected.", "success");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSelectingId(null);
    }
  }

  return (
    <section className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
      <form
        onSubmit={handleGenerate}
        className="rounded-[2rem] border border-white/70 bg-white/80 p-6 shadow-[0_28px_90px_rgba(28,25,23,0.08)] backdrop-blur sm:p-8"
      >
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.24em] text-stone-500">
              Dashboard
            </p>
            <h1 className="font-display mt-3 text-3xl font-bold tracking-[-0.04em] text-stone-900 sm:text-4xl">
              Generate captions from a fresh upload or your existing library.
            </h1>
          </div>

          <div className="rounded-[1.5rem] bg-stone-900 px-4 py-3 text-white">
            <p className="text-xs uppercase tracking-[0.24em] text-stone-300">
              Active style
            </p>
            <p className="mt-1 text-lg font-semibold capitalize">{style || "casual"}</p>
          </div>
        </div>

        <div className="grid gap-6">
          <section className="rounded-[1.75rem] border border-stone-200 bg-stone-50/80 p-5">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-stone-900">Upload a new image</h2>
                <p className="text-sm text-stone-500">
                  New uploads are automatically saved to your library.
                </p>
              </div>
              <span className="rounded-full bg-white px-3 py-1 text-xs font-bold uppercase tracking-[0.22em] text-stone-500">
                Option 1
              </span>
            </div>

            <label className="block cursor-pointer rounded-[1.5rem] border border-dashed border-stone-300 bg-white p-5 transition hover:border-stone-900">
              <input
                type="file"
                accept="image/*"
                onChange={handleFileChange}
                className="hidden"
              />
              <div className="grid gap-4 md:grid-cols-[180px_1fr] md:items-center">
                <div className="overflow-hidden rounded-[1.25rem] bg-stone-100">
                  {previewUrl ? (
                    <img
                      src={previewUrl}
                      alt="Selected upload preview"
                      className="aspect-square h-full w-full object-cover"
                    />
                  ) : (
                    <div className="flex aspect-square items-center justify-center px-6 text-center text-sm font-medium text-stone-400">
                      Select an image to preview it here.
                    </div>
                  )}
                </div>

                <div>
                  <p className="text-sm font-semibold uppercase tracking-[0.22em] text-stone-500">
                    Click to browse
                  </p>
                  <p className="mt-2 text-lg font-semibold text-stone-900">
                    {file ? file.name : "Drag less, click once, generate faster."}
                  </p>
                  <p className="mt-2 text-sm text-stone-500">
                    Supported by the backend multipart upload flow.
                  </p>
                </div>
              </div>
            </label>
          </section>

          <section className="rounded-[1.75rem] border border-stone-200 bg-stone-50/80 p-5">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-stone-900">Reuse an existing image</h2>
                <p className="text-sm text-stone-500">
                  Pick a stored image instead of uploading again.
                </p>
              </div>
              <span className="rounded-full bg-white px-3 py-1 text-xs font-bold uppercase tracking-[0.22em] text-stone-500">
                Option 2
              </span>
            </div>

            {imagesLoading ? (
              <div className="flex items-center gap-3 rounded-[1.5rem] bg-white px-4 py-5 text-sm font-medium text-stone-500">
                <div className="h-5 w-5 animate-spin rounded-full border-2 border-stone-200 border-t-stone-900" />
                Loading your image library...
              </div>
            ) : existingImages.length ? (
              <div className="grid gap-3 sm:grid-cols-2">
                {existingImages.slice(0, 6).map((image) => {
                  const active = String(image.id) === String(selectedImageId);
                  const imageUrl = buildImageUrl(image.path);

                  return (
                    <button
                      key={image.id}
                      type="button"
                      onClick={() => handleExistingImageSelect(image.id)}
                      className={`overflow-hidden rounded-[1.5rem] border text-left transition ${
                        active
                          ? "border-stone-900 bg-stone-900 text-white shadow-lg"
                          : "border-stone-200 bg-white hover:-translate-y-0.5 hover:shadow-[0_18px_45px_rgba(28,25,23,0.08)]"
                      }`}
                    >
                      <div className="flex aspect-[4/3] items-center justify-center overflow-hidden bg-stone-100">
                        <DashboardImagePreview
                          src={imageUrl}
                          alt={`Saved upload ${image.id}`}
                          className="h-full w-full object-cover"
                          fallbackLabel={`Preview unavailable for image #${image.id}`}
                        />
                      </div>
                      <div className="p-4">
                        <p className="text-xs font-bold uppercase tracking-[0.22em] opacity-60">
                          Image #{image.id}
                        </p>
                        <p className="mt-2 truncate text-sm font-medium">
                          {image.path}
                        </p>
                      </div>
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="rounded-[1.5rem] border border-dashed border-stone-300 bg-white px-5 py-8 text-center text-sm text-stone-500">
                No saved images yet. Your first upload will appear here.
              </div>
            )}
          </section>

          <section className="grid gap-5 md:grid-cols-[1fr_auto] md:items-end">
            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-stone-700">
                Caption style
              </span>
              <select
                value={style}
                onChange={(event) => setStyle(event.target.value || "casual")}
                className="w-full rounded-2xl border border-stone-200 bg-white px-4 py-3 text-stone-900 outline-none transition focus:border-stone-900"
              >
                {STYLE_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>

            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center justify-center rounded-full bg-stone-900 px-6 py-3 text-sm font-bold uppercase tracking-[0.24em] text-white transition hover:bg-stone-700 disabled:cursor-wait disabled:bg-stone-400"
            >
              {submitting ? "Generating..." : "Generate Captions"}
            </button>
          </section>

          {error ? (
            <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">
              {error}
            </div>
          ) : null}

          {(previewUrl || selectedExistingImage) && !error ? (
            <div className="rounded-[1.5rem] border border-stone-200 bg-white p-4">
              <p className="mb-3 text-xs font-bold uppercase tracking-[0.24em] text-stone-500">
                Active image source
              </p>
              <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                <div className="overflow-hidden rounded-[1.25rem] bg-stone-100 sm:w-32">
                  <DashboardImagePreview
                    src={previewUrl || buildImageUrl(selectedExistingImage?.path)}
                    alt={
                      file
                        ? "Current upload"
                        : `Selected saved image ${selectedExistingImage?.id}`
                    }
                    className="aspect-square h-full w-full object-cover"
                    fallbackLabel="Preview unavailable"
                  />
                </div>
                <div>
                  <p className="text-lg font-semibold text-stone-900">
                    {file
                      ? "Using a fresh upload"
                      : `Using saved image #${selectedExistingImage?.id}`}
                  </p>
                  <p className="mt-2 text-sm text-stone-500">
                    {file ? file.name : selectedExistingImage?.path}
                  </p>
                </div>
              </div>
            </div>
          ) : null}
        </div>
      </form>

      <section className="rounded-[2rem] border border-white/70 bg-white/80 p-6 shadow-[0_28px_90px_rgba(28,25,23,0.08)] backdrop-blur sm:p-8">
        <div className="mb-6 flex items-center justify-between gap-4">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.24em] text-stone-500">
              Output
            </p>
            <h2 className="font-display mt-2 text-2xl font-bold tracking-[-0.04em] text-stone-900">
              Generated captions and concepts
            </h2>
          </div>
          {submitting ? (
            <div className="flex items-center gap-3 rounded-full bg-stone-100 px-4 py-2 text-sm font-medium text-stone-600">
              <div className="h-4 w-4 animate-spin rounded-full border-2 border-stone-300 border-t-stone-900" />
              Generating
            </div>
          ) : null}
        </div>

        {result ? (
          <div className="space-y-6">
            <div className="rounded-[1.75rem] bg-stone-950 p-5 text-white">
              <div className="flex flex-wrap gap-2">
                {result.concepts.map((concept) => (
                  <span
                    key={concept}
                    className="rounded-full bg-white/10 px-3 py-1 text-xs font-bold uppercase tracking-[0.18em] text-stone-100"
                  >
                    {concept}
                  </span>
                ))}
              </div>
              <p className="mt-4 text-sm leading-7 text-stone-300">{result.prompt}</p>
            </div>

            <div className="grid gap-4">
              {result.captions.map((caption) => (
                <CaptionCard
                  key={caption.id}
                  caption={caption}
                  selecting={selectingId === caption.id}
                  onSelect={handleSelectCaption}
                />
              ))}
            </div>
          </div>
        ) : (
          <div className="flex min-h-[420px] flex-col items-center justify-center rounded-[1.75rem] border border-dashed border-stone-300 bg-stone-50/70 px-8 text-center">
            <p className="font-display text-2xl font-bold tracking-[-0.04em] text-stone-900">
              Nothing generated yet
            </p>
            <p className="mt-3 max-w-md text-sm leading-7 text-stone-500">
              Upload a new image or pick one from your library, choose a style, then
              generate captions. Concepts and selectable captions will appear here.
            </p>
          </div>
        )}
      </section>
    </section>
  );
}

export default Dashboard;
