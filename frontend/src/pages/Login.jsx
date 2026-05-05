import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { loginUser } from "../services/api.js";

const INITIAL_FORM = {
  email: "",
  password: "",
};

function Login({ onAuthSuccess, onNotify }) {
  const navigate = useNavigate();
  const [form, setForm] = useState(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((currentForm) => ({
      ...currentForm,
      [name]: value,
    }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setSubmitting(true);

    try {
      const auth = await loginUser(form);
      onAuthSuccess(auth);
      onNotify("Welcome back.", "success");
      navigate("/", { replace: true });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10 sm:px-6">
      <div className="grid w-full max-w-5xl gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="overflow-hidden rounded-[2.25rem] bg-stone-900 p-8 text-white shadow-[0_35px_110px_rgba(28,25,23,0.2)] sm:p-10">
          <div className="flex h-full flex-col justify-between gap-10">
            <div>
              <p className="mb-4 inline-flex rounded-full border border-white/15 px-4 py-2 text-xs font-bold uppercase tracking-[0.26em] text-stone-300">
                AI caption workspace
              </p>
              <h1 className="font-display max-w-md text-4xl font-bold tracking-[-0.05em] text-white sm:text-5xl">
                Write sharper captions without slowing down your workflow.
              </h1>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              {[
                "Upload new or existing images",
                "Generate multiple style-aware captions",
                "Keep a clean caption history",
              ].map((item) => (
                <div
                  key={item}
                  className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4 text-sm leading-6 text-stone-300"
                >
                  {item}
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="rounded-[2.25rem] border border-white/60 bg-white/85 p-8 shadow-[0_30px_100px_rgba(28,25,23,0.1)] backdrop-blur sm:p-10">
          <div className="mb-8">
            <p className="text-sm font-semibold uppercase tracking-[0.24em] text-stone-500">
              Sign in
            </p>
            <h2 className="font-display mt-3 text-3xl font-bold tracking-[-0.04em] text-stone-900">
              Continue to your dashboard
            </h2>
          </div>

          <form className="space-y-5" onSubmit={handleSubmit}>
            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-stone-700">
                Email
              </span>
              <input
                type="email"
                name="email"
                value={form.email}
                onChange={handleChange}
                required
                className="w-full rounded-2xl border border-stone-200 bg-stone-50 px-4 py-3 text-stone-900 outline-none transition focus:border-stone-900 focus:bg-white"
                placeholder="you@example.com"
              />
            </label>

            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-stone-700">
                Password
              </span>
              <input
                type="password"
                name="password"
                value={form.password}
                onChange={handleChange}
                required
                className="w-full rounded-2xl border border-stone-200 bg-stone-50 px-4 py-3 text-stone-900 outline-none transition focus:border-stone-900 focus:bg-white"
                placeholder="Enter your password"
              />
            </label>

            {error ? (
              <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">
                {error}
              </div>
            ) : null}

            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center rounded-full bg-stone-900 px-5 py-3 text-sm font-bold uppercase tracking-[0.24em] text-white transition hover:bg-stone-700 disabled:cursor-wait disabled:bg-stone-400"
            >
              {submitting ? "Signing in..." : "Login"}
            </button>
          </form>

          <p className="mt-6 text-sm text-stone-600">
            Need an account?{" "}
            <Link className="font-semibold text-stone-900 underline" to="/register">
              Create one
            </Link>
          </p>
        </section>
      </div>
    </div>
  );
}

export default Login;
