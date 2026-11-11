import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import ErrorBox from "../components/ErrorBox";

const HOME_BY_ROLE = {
  CUSTOMER: "/",
  OWNER: "/owner",
  TRANSPORTER: "/owner",
  DRIVER: "/driver",
  ADMIN: "/admin",
};

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    if (!form.email.includes("@")) return setError("Enter a valid email address.");
    if (!form.password) return setError("Enter your password.");
    setLoading(true);
    try {
      const user = await login(form.email.trim().toLowerCase(), form.password);
      // Go back where they were headed, or to their role's home page.
      navigate(location.state?.from || HOME_BY_ROLE[user.role] || "/", { replace: true });
    } catch (err) {
      // Module 1 returns the same message for a wrong email and a wrong
      // password, on purpose. Show it exactly as the backend sent it.
      setError(err.response?.data?.message || "Invalid email or password.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-md rounded border bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold">Login</h1>
      <p className="mt-1 text-sm text-gray-600">Welcome back to Truck Booking.</p>
      <form onSubmit={handleSubmit} className="mt-6 space-y-4">
        <ErrorBox message={error} />
        <div>
          <label className="text-sm font-medium">Email</label>
          <input type="email" value={form.email} onChange={(e) => update("email", e.target.value)}
            className="mt-1 w-full rounded border px-3 py-2" placeholder="you@example.com" />
        </div>
        <div>
          <label className="text-sm font-medium">Password</label>
          <input type="password" value={form.password} onChange={(e) => update("password", e.target.value)}
            className="mt-1 w-full rounded border px-3 py-2" placeholder="Your password" />
        </div>
        <button disabled={loading} className="w-full rounded bg-blue-600 py-2 font-medium text-white hover:bg-blue-700 disabled:bg-gray-300">
          {loading ? "Logging in..." : "Login"}
        </button>
      </form>
      <p className="mt-4 text-sm text-gray-600">
        No account? <Link to="/register" className="font-medium text-blue-600 hover:underline">Register</Link>
      </p>
    </div>
  );
}
