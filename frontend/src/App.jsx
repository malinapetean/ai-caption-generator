import { useEffect, useState } from "react";
import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import Navbar from "./components/Navbar.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import History from "./pages/History.jsx";
import Login from "./pages/Login.jsx";
import Register from "./pages/Register.jsx";
import {
  clearAuth,
  getProfile,
  getStoredUser,
  getToken,
  persistUser,
} from "./services/api.js";

function LoadingScreen() {
  return (
    <div className="flex min-h-screen items-center justify-center px-6">
      <div className="w-full max-w-sm rounded-[2rem] border border-white/60 bg-white/80 p-8 text-center shadow-[0_30px_120px_rgba(28,25,23,0.12)] backdrop-blur">
        <div className="mx-auto mb-4 h-12 w-12 animate-spin rounded-full border-4 border-stone-200 border-t-stone-900" />
        <p className="font-medium text-stone-600">Loading your workspace...</p>
      </div>
    </div>
  );
}

function Toast({ toast, onDismiss }) {
  if (!toast) {
    return null;
  }

  const tone = {
    success: "border-emerald-200 bg-emerald-50 text-emerald-900",
    error: "border-rose-200 bg-rose-50 text-rose-900",
    info: "border-sky-200 bg-sky-50 text-sky-900",
  }[toast.type || "info"];

  return (
    <div className="pointer-events-none fixed inset-x-0 top-5 z-50 flex justify-center px-4">
      <div
        className={`pointer-events-auto flex max-w-xl items-center justify-between gap-4 rounded-2xl border px-4 py-3 shadow-lg backdrop-blur ${tone}`}
      >
        <p className="text-sm font-semibold">{toast.message}</p>
        <button
          type="button"
          onClick={onDismiss}
          className="shrink-0 rounded-full border border-current/20 px-2 py-1 text-xs font-bold uppercase tracking-[0.24em]"
        >
          Close
        </button>
      </div>
    </div>
  );
}

function PublicRoute({ ready, isAuthenticated, children }) {
  if (!ready) {
    return <LoadingScreen />;
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return children;
}

function ProtectedLayout({ ready, isAuthenticated, currentUser, onLogout }) {
  if (!ready) {
    return <LoadingScreen />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="min-h-screen">
      <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col px-4 pb-10 pt-4 sm:px-6 lg:px-8">
        <Navbar currentUser={currentUser} onLogout={onLogout} />
        <main className="flex-1 pt-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function App() {
  const [currentUser, setCurrentUser] = useState(getStoredUser());
  const [authReady, setAuthReady] = useState(!getToken());
  const [toast, setToast] = useState(null);

  useEffect(() => {
    const token = getToken();

    if (!token) {
      setAuthReady(true);
      return;
    }

    let cancelled = false;

    async function bootstrapUser() {
      try {
        const profile = await getProfile();
        if (!cancelled) {
          setCurrentUser(profile);
        }
      } catch {
        clearAuth();
        if (!cancelled) {
          setCurrentUser(null);
        }
      } finally {
        if (!cancelled) {
          setAuthReady(true);
        }
      }
    }

    bootstrapUser();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!toast) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => {
      setToast(null);
    }, 3500);

    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  function notify(message, type = "info") {
    setToast({ message, type });
  }

  function updateCurrentUser(nextUser) {
    setCurrentUser((previousUser) => {
      const resolvedUser =
        typeof nextUser === "function" ? nextUser(previousUser) : nextUser;
      persistUser(resolvedUser);
      return resolvedUser;
    });
  }

  function handleAuthSuccess(user) {
    updateCurrentUser(user);
  }

  function handleLogout() {
    clearAuth();
    setCurrentUser(null);
    notify("Signed out.", "info");
  }

  const isAuthenticated = Boolean(getToken());

  return (
    <>
      <Routes>
        <Route
          path="/login"
          element={
            <PublicRoute ready={authReady} isAuthenticated={isAuthenticated}>
              <Login onAuthSuccess={handleAuthSuccess} onNotify={notify} />
            </PublicRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicRoute ready={authReady} isAuthenticated={isAuthenticated}>
              <Register onAuthSuccess={handleAuthSuccess} onNotify={notify} />
            </PublicRoute>
          }
        />
        <Route
          element={
            <ProtectedLayout
              ready={authReady}
              isAuthenticated={isAuthenticated}
              currentUser={currentUser}
              onLogout={handleLogout}
            />
          }
        >
          <Route
            path="/"
            element={
              <Dashboard
                currentUser={currentUser}
                onUserChange={updateCurrentUser}
                onNotify={notify}
              />
            }
          />
          <Route path="/history" element={<History currentUser={currentUser} />} />
        </Route>
        <Route
          path="*"
          element={<Navigate to={isAuthenticated ? "/" : "/login"} replace />}
        />
      </Routes>
      <Toast toast={toast} onDismiss={() => setToast(null)} />
    </>
  );
}

export default App;
