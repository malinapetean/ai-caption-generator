function CaptionCard({ caption, onSelect, selecting }) {
  return (
    <article
      className={`rounded-[1.75rem] border p-5 transition ${
        caption.selected
          ? "border-amber-300 bg-amber-50 shadow-[0_18px_45px_rgba(245,158,11,0.16)]"
          : "border-stone-200 bg-white hover:-translate-y-0.5 hover:shadow-[0_18px_45px_rgba(28,25,23,0.08)]"
      }`}
    >
      <div className="mb-4 flex items-start justify-between gap-3">
        <span className="rounded-full bg-stone-100 px-3 py-1 text-xs font-bold uppercase tracking-[0.24em] text-stone-500">
          {caption.style}
        </span>
        {caption.selected ? (
          <span className="rounded-full bg-amber-200/70 px-3 py-1 text-xs font-bold uppercase tracking-[0.2em] text-amber-900">
            Selected
          </span>
        ) : null}
      </div>

      <p className="text-base leading-7 text-stone-800">{caption.text}</p>

      <div className="mt-5 flex items-center justify-between gap-3">
        <p className="text-xs uppercase tracking-[0.18em] text-stone-400">
          Caption #{caption.id}
        </p>
        <button
          type="button"
          onClick={() => onSelect(caption.id)}
          disabled={caption.selected || selecting}
          className="rounded-full bg-stone-900 px-4 py-2 text-xs font-bold uppercase tracking-[0.22em] text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:bg-stone-300"
        >
          {caption.selected ? "Selected" : selecting ? "Saving..." : "Select"}
        </button>
      </div>
    </article>
  );
}

export default CaptionCard;
