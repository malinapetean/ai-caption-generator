import { useEffect, useState } from "react";
import { buildImageUrl, getCaptionHistory, getUserImages } from "../services/api.js";

const IMAGES_PER_PAGE = 4;

function HistoryImage({ imageId, path }) {
  const [hidden, setHidden] = useState(false);
  const imageUrl = buildImageUrl(path);

  if (!imageUrl || hidden) {
    return (
      <div className="flex aspect-[4/3] items-center justify-center rounded-[1.5rem] bg-stone-100 px-5 text-center text-sm font-medium text-stone-400">
        Preview unavailable for image #{imageId}
      </div>
    );
  }

  return (
    <img
      src={imageUrl}
      alt={`History preview for image ${imageId}`}
      className="aspect-[4/3] w-full rounded-[1.5rem] object-cover"
      onError={() => setHidden(true)}
    />
  );
}

function HistoryThumbnail({ entry, image, active, onSelect }) {
  const [hidden, setHidden] = useState(false);
  const imageUrl = buildImageUrl(image?.path);

  useEffect(() => {
    setHidden(false);
  }, [image?.path]);

  return (
    <button
      type="button"
      onClick={() => onSelect(entry.imageId)}
      className={`overflow-hidden rounded-[1.5rem] border text-left transition ${
        active
          ? "border-stone-900 bg-stone-900 text-white shadow-lg"
          : "border-stone-200 bg-white hover:-translate-y-0.5 hover:shadow-[0_18px_45px_rgba(28,25,23,0.08)]"
      }`}
    >
      <div className="flex aspect-[4/3] items-center justify-center overflow-hidden bg-stone-100">
        {imageUrl && !hidden ? (
          <img
            src={imageUrl}
            alt={`History thumbnail for image ${entry.imageId}`}
            className="h-full w-full object-cover"
            onError={() => setHidden(true)}
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center px-4 text-center text-xs font-medium text-stone-400">
            Image #{entry.imageId}
          </div>
        )}
      </div>
      <div className="p-4">
        <div className="flex items-center justify-between gap-3">
          <p className="text-xs font-bold uppercase tracking-[0.22em] opacity-70">
            Image #{entry.imageId}
          </p>
          <span className="text-xs font-semibold opacity-70">
            {entry.generations.length} sessions
          </span>
        </div>
        <p className="mt-2 truncate text-sm font-medium">
          {image?.path || "Stored path unavailable"}
        </p>
      </div>
    </button>
  );
}

function formatDate(isoString) {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(isoString));
}

function getGenerationKey(caption) {
  return caption.generationBatchId || `legacy-${caption.imageId}-${caption.style}`;
}

function groupHistory(captions) {
  const groupedByImage = new Map();

  for (const caption of captions) {
    const imageEntry = groupedByImage.get(caption.imageId) || {
      imageId: caption.imageId,
      createdAt: caption.createdAt,
      generations: new Map(),
    };

    const generationKey = getGenerationKey(caption);
    const generationEntry = imageEntry.generations.get(generationKey) || {
      key: generationKey,
      style: caption.style,
      createdAt: caption.createdAt,
      captions: [],
      selectedCaption: null,
    };

    if (caption.selected) {
      generationEntry.selectedCaption = caption;
    }

    generationEntry.captions.push(caption);

    if (new Date(caption.createdAt) > new Date(generationEntry.createdAt)) {
      generationEntry.createdAt = caption.createdAt;
    }

    if (new Date(caption.createdAt) > new Date(imageEntry.createdAt)) {
      imageEntry.createdAt = caption.createdAt;
    }

    imageEntry.generations.set(generationKey, generationEntry);
    groupedByImage.set(caption.imageId, imageEntry);
  }

  return [...groupedByImage.values()]
    .map((entry) => ({
      ...entry,
      generations: [...entry.generations.values()]
        .map((generation) => ({
          ...generation,
          captions: [...generation.captions].sort(
            (left, right) => new Date(right.createdAt) - new Date(left.createdAt)
          ),
        }))
        .sort((left, right) => new Date(right.createdAt) - new Date(left.createdAt)),
    }))
    .sort((left, right) => new Date(right.createdAt) - new Date(left.createdAt));
}

function History({ currentUser }) {
  const [entries, setEntries] = useState([]);
  const [imagesById, setImagesById] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedImageId, setSelectedImageId] = useState(null);
  const [thumbnailPage, setThumbnailPage] = useState(0);
  const totalGenerations = entries.reduce(
    (count, entry) => count + entry.generations.length,
    0
  );
  const totalPages = Math.max(1, Math.ceil(entries.length / IMAGES_PER_PAGE));
  const selectedIndex = entries.findIndex((entry) => entry.imageId === selectedImageId);
  const safeSelectedIndex = selectedIndex >= 0 ? selectedIndex : 0;
  const selectedEntry = entries[safeSelectedIndex] || null;
  const selectedImage = selectedEntry ? imagesById[selectedEntry.imageId] : null;
  const visibleEntries = entries.slice(
    thumbnailPage * IMAGES_PER_PAGE,
    thumbnailPage * IMAGES_PER_PAGE + IMAGES_PER_PAGE
  );

  useEffect(() => {
    if (!currentUser?.id) {
      return;
    }

    let cancelled = false;

    async function loadHistory() {
      setLoading(true);
      setError("");

      try {
        const [captions, images] = await Promise.all([
          getCaptionHistory(),
          getUserImages(currentUser.id),
        ]);

        if (cancelled) {
          return;
        }

        const groupedEntries = groupHistory(captions);
        setEntries(groupedEntries);
        setSelectedImageId((previousImageId) =>
          groupedEntries.some((entry) => entry.imageId === previousImageId)
            ? previousImageId
            : groupedEntries[0]?.imageId ?? null
        );
        setThumbnailPage((previousPage) => {
          const maxPage = Math.max(0, Math.ceil(groupedEntries.length / IMAGES_PER_PAGE) - 1);
          return Math.min(previousPage, maxPage);
        });
        setImagesById(
          images.reduce((accumulator, image) => {
            accumulator[image.id] = image;
            return accumulator;
          }, {})
        );
      } catch (requestError) {
        if (!cancelled) {
          setError(requestError.message);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadHistory();

    return () => {
      cancelled = true;
    };
  }, [currentUser?.id]);

  useEffect(() => {
    if (!entries.length || selectedImageId == null) {
      return;
    }

    const nextIndex = entries.findIndex((entry) => entry.imageId === selectedImageId);
    if (nextIndex < 0) {
      return;
    }

    const nextPage = Math.floor(nextIndex / IMAGES_PER_PAGE);
    setThumbnailPage((previousPage) => (previousPage === nextPage ? previousPage : nextPage));
  }, [entries, selectedImageId]);

  function handleSelectImage(imageId) {
    setSelectedImageId(imageId);
  }

  function handlePreviousImage() {
    if (safeSelectedIndex <= 0) {
      return;
    }

    setSelectedImageId(entries[safeSelectedIndex - 1].imageId);
  }

  function handleNextImage() {
    if (safeSelectedIndex >= entries.length - 1) {
      return;
    }

    setSelectedImageId(entries[safeSelectedIndex + 1].imageId);
  }

  return (
    <section className="rounded-[2rem] border border-white/70 bg-white/80 p-6 shadow-[0_28px_90px_rgba(28,25,23,0.08)] backdrop-blur sm:p-8">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.24em] text-stone-500">
            History
          </p>
          <h1 className="font-display mt-3 text-3xl font-bold tracking-[-0.04em] text-stone-900 sm:text-4xl">
            Review previous generations and selected captions.
          </h1>
        </div>
        <div className="rounded-[1.5rem] bg-stone-100 px-4 py-3">
          <p className="text-xs uppercase tracking-[0.24em] text-stone-500">
            Stored sessions
          </p>
          <p className="mt-1 text-lg font-semibold text-stone-900">{totalGenerations}</p>
        </div>
      </div>

      {loading ? (
        <div className="flex min-h-[320px] items-center justify-center gap-3 rounded-[1.75rem] border border-dashed border-stone-300 bg-stone-50/80 text-sm font-medium text-stone-500">
          <div className="h-5 w-5 animate-spin rounded-full border-2 border-stone-200 border-t-stone-900" />
          Loading caption history...
        </div>
      ) : error ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">
          {error}
        </div>
      ) : entries.length === 0 ? (
        <div className="flex min-h-[320px] flex-col items-center justify-center rounded-[1.75rem] border border-dashed border-stone-300 bg-stone-50/80 px-8 text-center">
          <p className="font-display text-2xl font-bold tracking-[-0.04em] text-stone-900">
            No history yet
          </p>
          <p className="mt-3 max-w-xl text-sm leading-7 text-stone-500">
            Generate captions from the dashboard and your saved sessions will appear
            here with style, captions, and selected choices.
          </p>
        </div>
      ) : (
        <div className="grid gap-6">
          <section className="rounded-[1.9rem] border border-stone-200 bg-stone-50/70 p-5">
            <div className="mb-5 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.24em] text-stone-500">
                  Browse by image
                </p>
                <p className="mt-2 text-sm text-stone-500">
                  Open one saved picture at a time instead of scrolling through the full archive.
                </p>
              </div>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => setThumbnailPage((page) => Math.max(0, page - 1))}
                  disabled={thumbnailPage === 0}
                  className="rounded-full border border-stone-200 bg-white px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] text-stone-600 transition hover:border-stone-900 hover:text-stone-900 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Prev page
                </button>
                <p className="text-xs font-bold uppercase tracking-[0.2em] text-stone-400">
                  Page {thumbnailPage + 1} / {totalPages}
                </p>
                <button
                  type="button"
                  onClick={() =>
                    setThumbnailPage((page) => Math.min(totalPages - 1, page + 1))
                  }
                  disabled={thumbnailPage >= totalPages - 1}
                  className="rounded-full border border-stone-200 bg-white px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] text-stone-600 transition hover:border-stone-900 hover:text-stone-900 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Next page
                </button>
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {visibleEntries.map((entry) => (
                <HistoryThumbnail
                  key={entry.imageId}
                  entry={entry}
                  image={imagesById[entry.imageId]}
                  active={selectedEntry?.imageId === entry.imageId}
                  onSelect={handleSelectImage}
                />
              ))}
            </div>
          </section>

          {selectedEntry ? (
            <article className="grid gap-6 rounded-[1.9rem] border border-stone-200 bg-stone-50/70 p-5 lg:grid-cols-[320px_1fr]">
              <div className="space-y-4">
                <HistoryImage imageId={selectedEntry.imageId} path={selectedImage?.path} />
                <div className="rounded-[1.5rem] bg-white p-4">
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-xs font-bold uppercase tracking-[0.24em] text-stone-500">
                      Image #{selectedEntry.imageId}
                    </p>
                    <p className="text-xs font-bold uppercase tracking-[0.22em] text-stone-400">
                      {safeSelectedIndex + 1} of {entries.length}
                    </p>
                  </div>
                  <p className="mt-2 text-sm text-stone-500">
                    {selectedImage?.path || "Stored path unavailable"}
                  </p>
                  <p className="mt-4 text-xs font-bold uppercase tracking-[0.22em] text-stone-400">
                    Last generated
                  </p>
                  <p className="mt-2 text-sm font-medium text-stone-800">
                    {formatDate(selectedEntry.createdAt)}
                  </p>
                  <div className="mt-5 grid grid-cols-2 gap-3">
                    <button
                      type="button"
                      onClick={handlePreviousImage}
                      disabled={safeSelectedIndex === 0}
                      className="rounded-full border border-stone-200 bg-stone-50 px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] text-stone-600 transition hover:border-stone-900 hover:text-stone-900 disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      Previous
                    </button>
                    <button
                      type="button"
                      onClick={handleNextImage}
                      disabled={safeSelectedIndex >= entries.length - 1}
                      className="rounded-full border border-stone-200 bg-stone-900 px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:bg-stone-300"
                    >
                      Next
                    </button>
                  </div>
                </div>
              </div>

              <div>
                <div className="grid gap-5">
                  {selectedEntry.generations.map((generation) => (
                    <section
                      key={generation.key}
                      className="rounded-[1.6rem] border border-stone-200 bg-white/80 p-4"
                    >
                      <div className="mb-4 flex flex-wrap items-center gap-3">
                        <span className="rounded-full bg-stone-900 px-4 py-2 text-xs font-bold uppercase tracking-[0.24em] text-white">
                          {generation.style}
                        </span>
                        <span className="rounded-full bg-stone-100 px-4 py-2 text-xs font-bold uppercase tracking-[0.22em] text-stone-500">
                          {generation.captions.length} captions
                        </span>
                        <span className="text-xs font-semibold uppercase tracking-[0.2em] text-stone-400">
                          Generated {formatDate(generation.createdAt)}
                        </span>
                      </div>

                      {generation.selectedCaption ? (
                        <div className="mb-5 rounded-[1.5rem] border border-amber-300 bg-amber-50 p-5">
                          <p className="text-xs font-bold uppercase tracking-[0.24em] text-amber-700">
                            Selected caption
                          </p>
                          <p className="mt-3 text-base leading-7 text-amber-950">
                            {generation.selectedCaption.text}
                          </p>
                        </div>
                      ) : (
                        <div className="mb-5 rounded-[1.5rem] border border-dashed border-stone-300 bg-white p-5 text-sm text-stone-500">
                          No selected caption stored for this generation yet.
                        </div>
                      )}

                      <div className="grid gap-3">
                        {generation.captions.map((caption) => (
                          <div
                            key={caption.id}
                            className={`rounded-[1.4rem] border px-4 py-4 ${
                              caption.selected
                                ? "border-amber-300 bg-white"
                                : "border-stone-200 bg-white/70"
                            }`}
                          >
                            <div className="mb-2 flex items-center justify-between gap-3">
                              <p className="text-xs font-bold uppercase tracking-[0.22em] text-stone-400">
                                Caption #{caption.id}
                              </p>
                              {caption.selected ? (
                                <span className="rounded-full bg-amber-100 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.2em] text-amber-700">
                                  Selected
                                </span>
                              ) : null}
                            </div>
                            <p className="text-sm leading-7 text-stone-700">{caption.text}</p>
                          </div>
                        ))}
                      </div>
                    </section>
                  ))}
                </div>
              </div>
            </article>
          ) : null}
        </div>
      )}
    </section>
  );
}

export default History;
