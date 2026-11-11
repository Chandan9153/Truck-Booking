import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import ErrorBox from "../components/ErrorBox";

// ADMIN is deliberately NOT in this list (Module 1 rejects it at the backend).
const ROLES = ["CUSTOMER", "OWNER", "DRIVER", "TRANSPORTER"];

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: "", email: "", phone: "", password: "", role: "CUSTOMER" });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
    setErrors((e) => ({ ...e, [field]: "" }));
  }

  function validate() {
    const e = {};
    if (form.fullName.trim().length < 2) e.fullName = "Full name must be at least 2 characters.";
    if (!form.email.includes("@")) e.email = "Enter a valid email address.";
    if (!/^[6-9][0-9]{9}$/.test(form.phone.trim())) e.phone = "Phone must be a 10-digit Indian mobile number starting with 6–9.";
    if (form.password.length < 8) e.password = "Password must be at least 8 characters.";
    if (!ROLES.includes(form.role)) e.role = "Choose a role.";
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    if (!validate()) return;
    setLoading(true);
    try {
      await register({ ...form, email: form.email.trim().toLowerCase(), phone: form.phone.trim() });
      // Register returns no token (Module 1) — send them to log in.
      navigate("/login", { state: { registered: true } });
    } catch (err) {
      // Backend field errors arrive as { fieldErrors: { email: "..." } }.
      const data = err.response?.data;
      if (data?.fieldErrors) setErrors(data.fieldErrors);
      else setError(data?.message || "Registration failed. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  const field = "mt-1 w-full rounded border px-3 py-2";
  const errText = "mt-1 text-sm text-red-600";

  return (
    <div className="mx-auto max-w-md rounded border bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold">Create your account</h1>
      <p className="mt-1 text-sm text-gray-600">Book trucks, or list your own — choose your role below.</p>
      <form onSubmit={handleSubmit} className="mt-6 space-y-4">
        <ErrorBox message={error} />
        <div>
          <label className="text-sm font-medium">Full name</label>
          <input value={form.fullName} onChange={(e) => update("fullName", e.target.value)} className={field} />
          {errors.fullName && <p className={errText}>{errors.fullName}</p>}
        </div>
        <div>
          <label className="text-sm font-medium">Email</label>
          <input type="email" value={form.email} onChange={(e) => update("email", e.target.value)} className={field} />
          {errors.email && <p className={errText}>{errors.email}</p>}
        </div>
        <div>
          <label className="text-sm font-medium">Phone</label>
          <input value={form.phone} onChange={(e) => update("phone", e.target.value)} className={field} placeholder="10-digit mobile number" />
          {errors.phone && <p className={errText}>{errors.phone}</p>}
        </div>
        <div>
          <label className="text-sm font-medium">Password</label>
          <input type="password" value={form.password} onChange={(e) => update("password", e.target.value)} className={field} />
          {errors.password && <p className={errText}>{errors.password}</p>}
        </div>
        <div>
          <label className="text-sm font-medium">I am a...</label>
          <select value={form.role} onChange={(e) => update("role", e.target.value)} className={field}>
            <option value="CUSTOMER">Customer — I need goods moved</option>
            <option value="OWNER">Truck Owner — I own trucks</option>
            <option value="DRIVER">Driver — I drive trucks</option>
            <option value="TRANSPORTER">Transporter — I run a transport company</option>
          </select>
          {errors.role && <p className={errText}>{errors.role}</p>}
        </div>
        <button disabled={loading} className="w-full rounded bg-blue-600 py-2 font-medium text-white hover:bg-blue-700 disabled:bg-gray-300">
          {loading ? "Creating account..." : "Register"}
        </button>
      </form>
      <p className="mt-4 text-sm text-gray-600">
        Already have an account? <Link to="/login" className="font-medium text-blue-600 hover:underline">Login</Link>
      </p>
    </div>
  );
}
