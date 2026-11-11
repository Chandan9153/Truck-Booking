import axios from "axios";

// Base URL comes from .env. Local default: http://localhost:8080/api
// ⚠️ If your backend uses /api/v1/... instead of /api/..., change ONLY this
//    value in .env (VITE_API_BASE_URL) — no other file needs to change.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api",
  headers: { "Content-Type": "application/json" },
});

// --- Token storage (localStorage, Module 1 learning-project choice) ---
// Module 1 notes this honestly: any script on the page could read
// localStorage. A production app would use an HttpOnly cookie for the
// refresh token instead. For this learning project, localStorage is fine
// and it is what AuthContext (Part 6) reads on page reload.
export function getAccessToken() {
  return localStorage.getItem("accessToken");
}
export function getRefreshToken() {
  return localStorage.getItem("refreshToken");
}
export function saveTokens(accessToken, refreshToken) {
  localStorage.setItem("accessToken", accessToken);
  localStorage.setItem("refreshToken", refreshToken);
}
export function clearTokens() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("user");
}

// Attach the access token to every request (except auth calls, which have none yet)
api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Refresh-on-401-once: the heart of the token flow (Module 1, Part 4)
let isRefreshing = false; // only one refresh at a time, even if 3 calls fail together
let waitingRequests = []; // requests that failed with 401 while a refresh was running

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Not a 401, or this IS the refresh call itself, or we already retried once:
    // give up and pass the error to the page that made the call.
    if (
      error.response?.status !== 401 ||
      originalRequest.url?.includes("/auth/refresh") ||
      originalRequest._retry
    ) {
      return Promise.reject(error);
    }

    // If a refresh is already running, wait for it instead of starting another.
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        waitingRequests.push({ resolve, reject, originalRequest });
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const refreshToken = getRefreshToken();
      if (!refreshToken) throw new Error("No refresh token");

      // NOTE: use plain axios here, not `api` — otherwise this call would
      // go through this same interceptor and loop forever.
      const res = await axios.post(
        `${api.defaults.baseURL}/auth/refresh`,
        { refreshToken }
      );
      saveTokens(res.data.accessToken, res.data.refreshToken);

      // Retry the requests that were waiting, with the new token.
      waitingRequests.forEach(({ resolve, originalRequest: req }) => {
        req.headers.Authorization = `Bearer ${res.data.accessToken}`;
        resolve(api(req));
      });
      waitingRequests = [];

      originalRequest.headers.Authorization = `Bearer ${res.data.accessToken}`;
      return api(originalRequest);
    } catch (refreshError) {
      // Refresh failed (expired, revoked, or invalid): log out everywhere.
      waitingRequests.forEach(({ reject }) => reject(refreshError));
      waitingRequests = [];
      clearTokens();
      window.location.href = "/login";
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  }
);

export default api;
