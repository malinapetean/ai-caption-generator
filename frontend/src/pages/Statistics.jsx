import { Component, useEffect, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getStats } from "../services/api.js";

const CHART_COLORS = ["#1c1917", "#c2410c", "#0f766e", "#0369a1", "#7c2d12", "#57534e"];

const EMPTY_STATS = {
  totalCaptions: 0,
  totalSelectedCaptions: 0,
  totalUploadedImages: 0,
  mostUsedStyle: null,
  mostSelectedStyle: null,
  preferredStyle: null,
  generatedByStyle: [],
  selectedByStyle: [],
  selectionRateByStyle: [],
};

function toSafeNumber(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : 0;
}

function normalizeStyleName(value) {
  return typeof value === "string" && value.trim() ? value.trim() : "unknown";
}

function normalizeStyleCounts(data) {
  if (!Array.isArray(data)) {
    return [];
  }

  return data.map((entry) => ({
    styleName: normalizeStyleName(entry?.style),
    label: normalizeStyleName(entry?.style),
    count: toSafeNumber(entry?.count),
    percentage: toSafeNumber(entry?.percentage),
  }));
}

function normalizeSelectionRates(data) {
  if (!Array.isArray(data)) {
    return [];
  }

  return data.map((entry) => ({
    styleName: normalizeStyleName(entry?.style),
    label: normalizeStyleName(entry?.style),
    generatedCount: toSafeNumber(entry?.generatedCount),
    selectedCount: toSafeNumber(entry?.selectedCount),
    selectionRate: toSafeNumber(entry?.selectionRate),
  }));
}

function formatPercentage(value) {
  return `${Number(value || 0).toFixed(1)}%`;
}

function formatStyle(value) {
  if (!value) {
    return "N/A";
  }

  return value.charAt(0).toUpperCase() + value.slice(1);
}

function StatCard({ label, value, helper, tone = "light" }) {
  const toneClassName =
    tone === "dark"
      ? "bg-stone-950 text-white"
      : "border border-stone-200 bg-white/90 text-stone-900";

  return (
    <article
      className={`rounded-[1.75rem] p-5 shadow-[0_18px_45px_rgba(28,25,23,0.06)] ${toneClassName}`}
    >
      <p
        className={`text-xs font-bold uppercase tracking-[0.24em] ${
          tone === "dark" ? "text-stone-400" : "text-stone-500"
        }`}
      >
        {label}
      </p>
      <p className="mt-4 font-display text-3xl font-bold tracking-[-0.05em]">
        {value}
      </p>
      <p className={`mt-3 text-sm ${tone === "dark" ? "text-stone-300" : "text-stone-500"}`}>
        {helper}
      </p>
    </article>
  );
}

function ChartCard({ eyebrow, title, description, children }) {
  return (
    <section className="rounded-[1.9rem] border border-white/70 bg-white/85 p-6 shadow-[0_28px_90px_rgba(28,25,23,0.08)] backdrop-blur sm:p-7">
      <p className="text-xs font-bold uppercase tracking-[0.24em] text-stone-500">
        {eyebrow}
      </p>
      <h2 className="font-display mt-3 text-2xl font-bold tracking-[-0.04em] text-stone-900">
        {title}
      </h2>
      <p className="mt-2 text-sm leading-7 text-stone-500">{description}</p>
      <div className="mt-6">{children}</div>
    </section>
  );
}

class ChartErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error) {
    console.error("Statistics chart render failed.", error);
  }

  render() {
    if (this.state.hasError) {
      return (
        <section className="rounded-[1.9rem] border border-amber-200 bg-amber-50 p-6 shadow-[0_28px_90px_rgba(28,25,23,0.08)] sm:p-7">
          <p className="text-xs font-bold uppercase tracking-[0.24em] text-amber-700">
            Charts unavailable
          </p>
          <h2 className="font-display mt-3 text-2xl font-bold tracking-[-0.04em] text-amber-950">
            Statistics are loaded, but the chart view failed to render.
          </h2>
          <p className="mt-2 text-sm leading-7 text-amber-900/80">
            The KPI cards are still valid. Refresh once more after this patch is
            deployed, and if it still happens check the browser console for the exact
            chart error.
          </p>
        </section>
      );
    }

    return this.props.children;
  }
}

function Statistics() {
  const [stats, setStats] = useState(EMPTY_STATS);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [chartsReady, setChartsReady] = useState(false);
  const generatedByStyle = normalizeStyleCounts(stats.generatedByStyle);
  const selectedByStyle = normalizeStyleCounts(stats.selectedByStyle);
  const selectionRateByStyle = normalizeSelectionRates(stats.selectionRateByStyle);
  const bestConvertingStyle = [...selectionRateByStyle].sort(
    (left, right) =>
      right.selectionRate - left.selectionRate ||
      right.generatedCount - left.generatedCount ||
      left.styleName.localeCompare(right.styleName)
  )[0];

  useEffect(() => {
    let cancelled = false;

    async function loadStats() {
      setLoading(true);
      setError("");

      try {
        const response = await getStats();
        if (!cancelled) {
          setStats({ ...EMPTY_STATS, ...response });
        }
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

    loadStats();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (loading || stats.totalCaptions === 0) {
      setChartsReady(false);
      return undefined;
    }

    let cancelled = false;
    let firstFrameId = 0;
    let secondFrameId = 0;

    firstFrameId = window.requestAnimationFrame(() => {
      secondFrameId = window.requestAnimationFrame(() => {
        if (!cancelled) {
          setChartsReady(true);
        }
      });
    });

    return () => {
      cancelled = true;
      window.cancelAnimationFrame(firstFrameId);
      window.cancelAnimationFrame(secondFrameId);
    };
  }, [loading, stats.totalCaptions]);

  const kpis = [
    {
      label: "Total Captions Generated",
      value: stats.totalCaptions,
      helper: "Every caption generated across your saved sessions.",
    },
    {
      label: "Total Selected Captions",
      value: stats.totalSelectedCaptions,
      helper: "Captions marked as the final pick from each generation set.",
    },
    {
      label: "Total Uploaded Images",
      value: stats.totalUploadedImages,
      helper: "Images stored in your personal library.",
    },
    {
      label: "Preferred Style",
      value: formatStyle(stats.preferredStyle),
      helper: "Derived from your selections first, then overall generation habits.",
      tone: "dark",
    },
    {
      label: "Most Used Style",
      value: formatStyle(stats.mostUsedStyle),
      helper: "The style you generate with most often.",
    },
    {
      label: "Most Selected Style",
      value: formatStyle(stats.mostSelectedStyle),
      helper: "The style that wins the most caption selections.",
    },
  ];

  return (
    <section className="grid gap-6">
      <div className="rounded-[2rem] border border-white/70 bg-white/80 p-6 shadow-[0_28px_90px_rgba(28,25,23,0.08)] backdrop-blur sm:p-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.24em] text-stone-500">
              Statistics
            </p>
            <h1 className="font-display mt-3 text-3xl font-bold tracking-[-0.04em] text-stone-900 sm:text-4xl">
              Track how your caption styles perform over time.
            </h1>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-stone-500">
              This dashboard is calculated from your authenticated account only,
              combining generated captions, selected winners, and your image library.
            </p>
          </div>

          <div className="rounded-[1.5rem] bg-stone-100 px-4 py-3">
            <p className="text-xs font-bold uppercase tracking-[0.24em] text-stone-500">
              Selection rate
            </p>
            <p className="mt-1 text-lg font-semibold text-stone-900">
              {formatPercentage(
                stats.totalCaptions
                  ? (stats.totalSelectedCaptions / stats.totalCaptions) * 100
                  : 0
              )}
            </p>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="flex min-h-[320px] items-center justify-center gap-3 rounded-[2rem] border border-dashed border-stone-300 bg-stone-50/80 px-6 text-sm font-medium text-stone-500">
          <div className="h-5 w-5 animate-spin rounded-full border-2 border-stone-200 border-t-stone-900" />
          Loading statistics...
        </div>
      ) : error ? (
        <div className="rounded-[1.75rem] border border-rose-200 bg-rose-50 px-4 py-4 text-sm font-medium text-rose-700">
          Could not load statistics. {error}
        </div>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {kpis.map((item) => (
              <StatCard
                key={item.label}
                label={item.label}
                value={item.value}
                helper={item.helper}
                tone={item.tone}
              />
            ))}
          </div>

          {stats.totalCaptions === 0 ? (
            <div className="flex min-h-[300px] flex-col items-center justify-center rounded-[2rem] border border-dashed border-stone-300 bg-stone-50/80 px-8 text-center">
              <p className="font-display text-2xl font-bold tracking-[-0.04em] text-stone-900">
                No statistics available yet.
              </p>
              <p className="mt-3 max-w-xl text-sm leading-7 text-stone-500">
                Generate and select captions to see insights.
              </p>
            </div>
          ) : (
            <div className="grid gap-6 xl:grid-cols-2">
              {chartsReady ? (
                <ChartErrorBoundary>
                  <>
                    <ChartCard
                      eyebrow="Composition"
                      title="Generated captions by style"
                      description="Share of every generated caption grouped by normalized style."
                    >
                      <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
                        <div className="h-[280px]">
                          <ResponsiveContainer
                            width="100%"
                            height="100%"
                            minWidth={240}
                            minHeight={280}
                            initialDimension={{ width: 320, height: 280 }}
                          >
                            <PieChart>
                              <Pie
                                data={generatedByStyle}
                                dataKey="count"
                                nameKey="label"
                                innerRadius={72}
                                outerRadius={108}
                                paddingAngle={3}
                              >
                                {generatedByStyle.map((entry, index) => (
                                  <Cell
                                    key={entry.styleName}
                                    fill={CHART_COLORS[index % CHART_COLORS.length]}
                                  />
                                ))}
                              </Pie>
                              <Tooltip
                                formatter={(value, _name, payload) => [
                                  `${value} captions (${formatPercentage(payload?.payload?.percentage)})`,
                                  formatStyle(payload?.payload?.styleName),
                                ]}
                              />
                            </PieChart>
                          </ResponsiveContainer>
                        </div>

                        <div className="grid gap-3">
                          {generatedByStyle.map((entry, index) => (
                            <div
                              key={entry.styleName}
                              className="rounded-[1.4rem] border border-stone-200 bg-stone-50/80 px-4 py-3"
                            >
                              <div className="flex items-center justify-between gap-3">
                                <div className="flex items-center gap-3">
                                  <span
                                    className="h-3.5 w-3.5 rounded-full"
                                    style={{
                                      backgroundColor:
                                        CHART_COLORS[index % CHART_COLORS.length],
                                    }}
                                  />
                                  <p className="font-semibold text-stone-900">
                                    {formatStyle(entry.styleName)}
                                  </p>
                                </div>
                                <p className="text-sm font-medium text-stone-500">
                                  {entry.count}
                                </p>
                              </div>
                              <p className="mt-2 text-sm text-stone-500">
                                {formatPercentage(entry.percentage)} of generated captions
                              </p>
                            </div>
                          ))}
                        </div>
                      </div>
                    </ChartCard>

                    <ChartCard
                      eyebrow="Winners"
                      title="Selected captions by style"
                      description="How often each style produced the final chosen caption."
                    >
                      {selectedByStyle.length ? (
                        <div className="h-[340px]">
                          <ResponsiveContainer
                            width="100%"
                            height="100%"
                            minWidth={280}
                            minHeight={340}
                            initialDimension={{ width: 420, height: 340 }}
                          >
                            <BarChart
                              data={selectedByStyle}
                              margin={{ top: 12, right: 12, left: 0, bottom: 8 }}
                            >
                              <CartesianGrid
                                stroke="#e7e5e4"
                                strokeDasharray="3 3"
                                vertical={false}
                              />
                              <XAxis
                                dataKey="label"
                                tickFormatter={formatStyle}
                                tickLine={false}
                                axisLine={false}
                              />
                              <YAxis allowDecimals={false} tickLine={false} axisLine={false} />
                              <Tooltip
                                formatter={(value, _name, payload) => [
                                  `${value} selections (${formatPercentage(payload?.payload?.percentage)})`,
                                  formatStyle(payload?.payload?.styleName),
                                ]}
                              />
                              <Bar dataKey="count" radius={[16, 16, 0, 0]} fill="#c2410c" />
                            </BarChart>
                          </ResponsiveContainer>
                        </div>
                      ) : (
                        <div className="flex min-h-[340px] items-center justify-center rounded-[1.5rem] border border-dashed border-stone-300 bg-stone-50/80 px-6 text-center text-sm text-stone-500">
                          No selected captions yet. Pick winning captions to populate this chart.
                        </div>
                      )}
                    </ChartCard>

                    <ChartCard
                      eyebrow="Efficiency"
                      title="Selection rate by style"
                      description="Selected captions divided by generated captions for each style."
                    >
                      <div className="grid gap-4">
                        {selectionRateByStyle.map((entry, index) => (
                          <div
                            key={entry.styleName}
                            className="rounded-[1.5rem] border border-stone-200 bg-stone-50/80 p-4"
                          >
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                              <div>
                                <div className="flex items-center gap-3">
                                  <span
                                    className="h-3.5 w-3.5 rounded-full"
                                    style={{
                                      backgroundColor:
                                        CHART_COLORS[index % CHART_COLORS.length],
                                    }}
                                  />
                                  <p className="text-base font-semibold text-stone-900">
                                    {formatStyle(entry.styleName)}
                                  </p>
                                </div>
                                <p className="mt-2 text-sm text-stone-500">
                                  {entry.selectedCount} selected from {entry.generatedCount} generated
                                </p>
                              </div>
                              <p className="text-lg font-semibold text-stone-900">
                                {formatPercentage(entry.selectionRate)}
                              </p>
                            </div>
                            <div className="mt-4 h-3 overflow-hidden rounded-full bg-stone-200">
                              <div
                                className="h-full rounded-full transition-[width]"
                                style={{
                                  width: `${Math.max(
                                    0,
                                    Math.min(100, entry.selectionRate || 0)
                                  )}%`,
                                  backgroundColor:
                                    CHART_COLORS[index % CHART_COLORS.length],
                                }}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    </ChartCard>

                    <ChartCard
                      eyebrow="Snapshot"
                      title="Quick reading"
                      description="A compact summary of your current generation and selection habits."
                    >
                      <div className="grid gap-4 sm:grid-cols-2">
                        <div className="rounded-[1.5rem] bg-stone-950 p-5 text-white">
                          <p className="text-xs font-bold uppercase tracking-[0.24em] text-stone-400">
                            Best converting style
                          </p>
                          <p className="mt-3 font-display text-2xl font-bold tracking-[-0.04em]">
                            {formatStyle(bestConvertingStyle?.styleName)}
                          </p>
                          <p className="mt-2 text-sm text-stone-300">
                            {formatPercentage(bestConvertingStyle?.selectionRate)}
                          </p>
                        </div>

                        <div className="rounded-[1.5rem] border border-stone-200 bg-stone-50/80 p-5">
                          <p className="text-xs font-bold uppercase tracking-[0.24em] text-stone-500">
                            Caption volume
                          </p>
                          <p className="mt-3 font-display text-2xl font-bold tracking-[-0.04em] text-stone-900">
                            {generatedByStyle.length} active styles
                          </p>
                          <p className="mt-2 text-sm text-stone-500">
                            Distinct styles recorded in your generation history.
                          </p>
                        </div>
                      </div>
                    </ChartCard>
                  </>
                </ChartErrorBoundary>
              ) : (
                <div className="flex min-h-[260px] items-center justify-center rounded-[2rem] border border-dashed border-stone-300 bg-stone-50/80 px-8 text-center text-sm font-medium text-stone-500 xl:col-span-2">
                  Preparing the chart view...
                </div>
              )}
            </div>
          )}
        </>
      )}
    </section>
  );
}

export default Statistics;
