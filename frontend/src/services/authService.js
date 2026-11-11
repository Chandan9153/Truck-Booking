import api, { saveTokens, clearTokens } from "./api";

// POST /api/auth/register — Module 1. Returns the new user, NO token.
// Role ADMIN is rejected by the backend; the form must not offer it.
export async function registerUser({ fullName, email, phone, password, role }) {
  const res = await api.post("/auth/register", {
    fullName, email, phone, password, role,
  });
  return res.data; // { id, fullName, email, phone, role }
}

// POST /api/auth/login — Module 1. Returns user + both tokens.
export async function loginUser({ email, password }) {
  const res = await api.post("/auth/login", { email, password });
  const data = res.data; // { userId, fullName, email, role, accessToken, refreshToken, tokenType }
  saveTokens(data.accessToken, data.refreshToken);
  localStorage.setItem(
    "user",
    JSON.stringify({
      userId: data.userId,
      fullName: data.fullName,
      email: data.email,
      role: data.role,
    })
  );
  return data;
}

// Logout is frontend-only in Module 1 (JWTs are stateless — the server
// cannot cancel a token early). Deleting the tokens IS the logout.
export function logoutUser() {
  clearTokens();
}

// GET /api/auth/me — the logged-in user's own profile (Module 1).
export async function fetchMyProfile() {
  const res = await api.get("/auth/me");
  return res.data;
}
