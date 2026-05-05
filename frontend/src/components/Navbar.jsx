import { NavLink } from "react-router-dom";

function Navbar({ currentUser, onLogout }) {
  const navClassName = ({ isActive }) =>
    `rounded-full px-4 py-2 text-sm font-semibold transition ${
      isActive
        ? "bg-stone-900 text-white shadow-lg shadow-stone-900/15"
        : "text-stone-600 hover:bg-white hover:text-stone-900"
    }`;

  return (
    <header className="sticky top-4 z-30 rounded-[2rem] border border-white/70 bg-white/70 px-4 py-4 shadow-[0_25px_80px_rgba(28,25,23,0.08)] backdrop-blur sm:px-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="font-display text-lg font-bold tracking-[-0.04em] text-stone-900">
            Caption Studio
          </p>
          <p className="text-sm text-stone-500">
            AI-assisted captions for polished social content.
          </p>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <nav className="flex items-center gap-2 rounded-full bg-stone-100/80 p-1">
            <NavLink to="/" end className={navClassName}>
              Dashboard
            </NavLink>
            <NavLink to="/history" className={navClassName}>
              History
            </NavLink>
          </nav>

          <div className="flex items-center justify-between gap-3 rounded-full border border-stone-200 bg-white px-4 py-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-stone-800">
                {currentUser?.email}
              </p>
              <p className="text-xs uppercase tracking-[0.18em] text-stone-500">
                {currentUser?.preferredStyle || "casual"} style
              </p>
            </div>
            <button
              type="button"
              onClick={onLogout}
              className="rounded-full bg-stone-900 px-4 py-2 text-xs font-bold uppercase tracking-[0.2em] text-white transition hover:bg-stone-700"
            >
              Logout
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}

export default Navbar;
