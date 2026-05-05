import { useEffect, useState } from "react";
import { buildImageUrl, getCaptionHistory, getUserImages } from "../services/api.js";

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

function formatDate(isoString) {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(isoString));
}

function groupHistory(captions) {
  const grouped = new Map();

  for (const caption of captions) {
    const currentGroup = grouped.get(caption.imageId) || {
      imageId: caption.imageId,
      style: caption.style,
      createdAt: caption.createdAt,
      captions: [],
      selectedCaption: null,
    };

    currentGroup.captions.push(caption);

    if (caption.selected) {
      currentGroup.selectedCaption = caption;
    }

    if (new Date(caption.createdAt) > new Date(currentGroup.createdAt)) {
      currentGroup.createdAt = caption.createdAt;
    }

    grouped.set(caption.imageId, currentGroup);
  }

  return [...grouped.values()].sort(
    (left, right) => new Date(right.createdAt) - new Date(left.createdAt)
  );
}

function History({ currentUser }) {
  const [entries, setEntries] = useState([]);
  const [imagesById, setImagesById] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

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

        setEntries(groupHistory(captions));
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
          <p className="mt-1 text-lg font-semibold text-stone-900">{entries.length}</p>
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
          {entries.map((entry) => {
            const selectedCaption =
              entry.selectedCaption || entry.captions.find((caption) => caption.selected);
            const image = imagesById[entry.imageId];

            return (
              <article
                key={entry.imageId}
                className="grid gap-6 rounded-[1.9rem] border border-stone-200 bg-stone-50/70 p-5 lg:grid-cols-[320px_1fr]"
              >
                <div className="space-y-4">
                  <HistoryImage imageId={entry.imageId} path={image?.path} />
                  <div className="rounded-[1.5rem] bg-white p-4">
                    <p className="text-xs font-bold uppercase tracking-[0.24em] text-stone-500">
                      Image #{entry.imageId}
                    </p>
                    <p className="mt-2 text-sm text-stone-500">
                      {image?.path || "Stored path unavailable"}
                    </p>
                    <p className="mt-4 text-xs font-bold uppercase tracking-[0.22em] text-stone-400">
                      Last generated
                    </p>
                    <p className="mt-2 text-sm font-medium text-stone-800">
                      {formatDate(entry.createdAt)}
                    </p>
                  </div>
                </div>

                <div>
                  <div className="mb-4 flex flex-wrap items-center gap-3">
                    <span className="rounded-full bg-stone-900 px-4 py-2 text-xs font-bold uppercase tracking-[0.24em] text-white">
                      {entry.style}
                    </span>
                    <span className="rounded-full bg-white px-4 py-2 text-xs font-bold uppercase tracking-[0.22em] text-stone-500">
                      {entry.captions.length} captions
                    </span>
                  </div>

                  {selectedCaption ? (
                    <div className="mb-5 rounded-[1.5rem] border border-amber-300 bg-amber-50 p-5">
                      <p className="text-xs font-bold uppercase tracking-[0.24em] text-amber-700">
                        Selected caption
                      </p>
                      <p className="mt-3 text-base leading-7 text-amber-950">
                        {selectedCaption.text}
                      </p>
                    </div>
                  ) : (
                    <div className="mb-5 rounded-[1.5rem] border border-dashed border-stone-300 bg-white p-5 text-sm text-stone-500">
                      No selected caption stored for this image yet.
                    </div>
                  )}

                  <div className="grid gap-3">
                    {entry.captions.map((caption) => (
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
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

export default History;
