import axios from "axios";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const TOKEN_KEY = "caption_app_token";
const USER_KEY = "caption_app_user";

const api = axios.create({
  baseURL: API_BASE_URL,
});

function normalizeUser(user) {
  if (!user) {
    return null;
  }

  return {
    id: user.id ?? user.userId ?? null,
    email: user.email ?? "",
    preferredStyle: user.preferredStyle || "casual",
  };
}

function toRequestError(error) {
  const message =
    error.response?.data?.message ||
    error.response?.data?.error ||
    error.message ||
    "Something went wrong.";

  const requestError = new Error(message);
  requestError.status = error.response?.status;
  return requestError;
}

api.interceptors.request.use((config) => {
  const token = getToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuth();
    }

    return Promise.reject(toRequestError(error));
  }
);

export function getToken() {
  return window.localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser() {
  const stored = window.localStorage.getItem(USER_KEY);

  if (!stored) {
    return null;
  }

  try {
    return normalizeUser(JSON.parse(stored));
  } catch {
    return null;
  }
}

export function persistUser(user) {
  const normalizedUser = normalizeUser(user);

  if (!normalizedUser) {
    window.localStorage.removeItem(USER_KEY);
    return null;
  }

  window.localStorage.setItem(USER_KEY, JSON.stringify(normalizedUser));
  return normalizedUser;
}

export function persistAuth(authResponse) {
  if (authResponse?.token) {
    window.localStorage.setItem(TOKEN_KEY, authResponse.token);
  }

  return persistUser(authResponse);
}

export function clearAuth() {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(USER_KEY);
}

export async function loginUser(credentials) {
  const { data } = await api.post("/api/auth/login", credentials);
  persistAuth(data);
  return normalizeUser(data);
}

export async function registerUser(payload) {
  const { data } = await api.post("/api/auth/register", payload);
  persistAuth(data);
  return normalizeUser(data);
}

export async function getProfile() {
  const { data } = await api.get("/api/users/me");
  return persistUser(data);
}

export async function getUserImages(userId) {
  const { data } = await api.get(`/api/images/user/${userId}`);
  return data;
}

export async function generateCaptions({ file, imageId, style }) {
  const formData = new FormData();

  if (file) {
    formData.append("image", file);
  }

  if (imageId) {
    formData.append("imageId", imageId);
  }

  formData.append("style", style || "casual");

  const { data } = await api.post("/api/captions/generate", formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });

  return data;
}

export async function selectCaption(captionId) {
  const { data } = await api.post("/api/captions/select", { captionId });
  return data;
}

export async function getCaptionHistory() {
  const { data } = await api.get("/api/captions/history");
  return data;
}

export function buildImageUrl(path) {
  if (!path) {
    return "";
  }

  if (path.startsWith("http://") || path.startsWith("https://")) {
    return path;
  }

  const encodedPath = path
    .replace(/^\/+/, "")
    .split("/")
    .filter(Boolean)
    .map((segment) => encodeURIComponent(segment))
    .join("/");

  return `${API_BASE_URL}/api/images/${encodedPath}`;
}

export default api;
