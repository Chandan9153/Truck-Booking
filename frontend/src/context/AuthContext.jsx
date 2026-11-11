import { createContext, useContext, useState } from "react";
import { loginUser, registerUser, logoutUser } from "../services/authService";

const AuthContext = createContext(null);

// The role lives in the JWT token itself. A JWT is three Base64 parts
// joined by dots; the middle part (payload) is JSON. We decode it WITHOUT
// verifying the signature — verification is the backend's job, and the
// frontend only uses the role to decide which menu to show. The backend
// re-checks the role on every request, so a user who edits their token
// in DevTools gains nothing: the backend will still say 403.
function roleFromToken(token) {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.role || payload.roles?.[0] || null;
  } catch {
    return null;
  }
}

// If the backend does not put the role in the token, AuthContext falls back
// to the role saved at login time (loginUser saves the user object).
function savedUser() {
  try {
    return JSON.parse(localStorage.getItem("user"));
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => savedUser());
  const [token, setToken] = useState(() => localStorage.getItem("accessToken"));

  async function login(email, password) {
    const data = await loginUser({ email, password });
    const loggedIn = {
      userId: data.userId,
      fullName: data.fullName,
      email: data.email,
      role: data.role || roleFromToken(data.accessToken),
    };
    setUser(loggedIn);
    setToken(data.accessToken);
    return loggedIn; // the Login page uses the role to choose where to go
  }

  async function register(form) {
    // Register returns no token (Module 1) — the user logs in next.
    await registerUser(form);
  }

  function logout() {
    logoutUser();
    setUser(null);
    setToken(null);
  }

  const value = {
    user,
    token,
    isLoggedIn: !!token && !!user,
    role: user?.role || null,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
