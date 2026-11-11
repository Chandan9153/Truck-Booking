import { useEffect, useState } from "react";
import { fetchAdminCategories, addCategory } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";

// ⚠️ ASSUMED endpoints — check against Module 2.
// The 4 seed categories (12/20t, 14/25t, 18/30t, 22/36t) already exist;
// this page adds a new one only if the business ever needs it.
export default function CategoryManagement() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ name: "", tyreCount: "", targetPayloadTons: "" });

  function load() {
    setLoading(true);
    fetchAdminCategories()
      .then((data) => setCategories(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load categories."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleAdd(e) {
    e.preventDefault();
    try {
      await addCategory(form);
      setForm({ name: "", tyreCount: "", targetPayloadTons: "" });
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not add this category.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading categories..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Category Management</h1>
      <ErrorBox message={error} />
      <form onSubmit={handleAdd} className="mt-6 flex flex-wrap gap-2 rounded border bg-white p-4">
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="rounded border px-3 py-2" placeholder="Name (e.g. 10 Tyre Truck)" />
        <input type="number" value={form.tyreCount} onChange={(e) => setForm({ ...form, tyreCount: e.target.value })} className="rounded border px-3 py-2" placeholder="Tyres" />
        <input type="number" step="0.5" value={form.targetPayloadTons} onChange={(e) => setForm({ ...form, targetPayloadTons: e.target.value })} className="rounded border px-3 py-2" placeholder="Target payload (tons)" />
        <button className="rounded bg-blue-600 px-4 py-2 text-white">Add category</button>
      </form>
      <table className="mt-6 w-full rounded border bg-white text-sm">
        <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Name</th><th className="p-3">Tyres</th><th className="p-3">Target payload</th></tr></thead>
        <tbody>
          {categories.map((c) => (
            <tr key={c.id} className="border-b"><td className="p-3">{c.name}</td><td className="p-3">{c.tyreCount}</td><td className="p-3">{c.targetPayloadTons} tons</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
