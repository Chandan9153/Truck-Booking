import { useEffect, useState } from "react";
import { fetchMyTrucks, addTruck, setTruckAvailability, fetchCategories } from "../../services/truckService";
import TruckCard from "../../components/TruckCard";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

const BODY_TYPES = ["OPEN", "CLOSED", "REFRIGERATED", "TANKER", "FLATBED"];
// ⚠️ Match these names to your Module 2 BodyType enum (see Home.jsx note).

export default function MyTrucks() {
  const [trucks, setTrucks] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ registrationNumber: "", categoryId: "", bodyType: "OPEN", payloadTons: "" });
  const [formError, setFormError] = useState("");

  function load() {
    setLoading(true);
    Promise.all([fetchMyTrucks(), fetchCategories().catch(() => [])])
      .then(([t, c]) => { setTrucks(t || []); setCategories(c || []); })
      .catch((err) => setError(err.response?.data?.message || "Could not load your trucks."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleAdd(e) {
    e.preventDefault();
    setFormError("");
    if (!form.registrationNumber.trim()) return setFormError("Enter the registration number (e.g. JH01AB1234).");
    if (!form.categoryId) return setFormError("Choose a truck category.");
    if (!form.payloadTons || Number(form.payloadTons) <= 0) return setFormError("Enter the payload in tons (the weight this truck can actually carry).");
    try {
      // Slim form (Module 2 correction): only 4 fields. No documents, no
      // insurance, no paperwork — the rate comes from the rate table.
      await addTruck(form);
      setForm({ registrationNumber: "", categoryId: "", bodyType: "OPEN", payloadTons: "" });
      load();
    } catch (err) {
      setFormError(err.response?.data?.message || "Could not add this truck.");
    }
  }

  async function handleToggle(truck) {
    const next = truck.availabilityStatus === "AVAILABLE" ? "UNAVAILABLE" : "AVAILABLE";
    try {
      await setTruckAvailability(truck.id, next);
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not change availability.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading your trucks..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">My Trucks</h1>
      <ErrorBox message={error} />

      <form onSubmit={handleAdd} className="mt-6 rounded border bg-white p-5">
        <h2 className="font-semibold">Add a truck</h2>
        {formError && <p className="mt-2 text-sm text-red-600">{formError}</p>}
        <div className="mt-4 grid gap-4 md:grid-cols-4">
          <input value={form.registrationNumber} onChange={(e) => setForm({ ...form, registrationNumber: e.target.value.toUpperCase() })} className="rounded border px-3 py-2" placeholder="Registration no. (JH01AB1234)" />
          <select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })} className="rounded border px-3 py-2">
            <option value="">Choose category</option>
            {categories.map((c) => (<option key={c.id} value={c.id}>{c.name} — {c.targetPayloadTons}t</option>))}
          </select>
          <select value={form.bodyType} onChange={(e) => setForm({ ...form, bodyType: e.target.value })} className="rounded border px-3 py-2">
            {BODY_TYPES.map((b) => (<option key={b} value={b}>{b}</option>))}
          </select>
          <input type="number" step="0.5" min="0.5" value={form.payloadTons} onChange={(e) => setForm({ ...form, payloadTons: e.target.value })} className="rounded border px-3 py-2" placeholder="Payload (tons)" />
        </div>
        <button className="mt-4 rounded bg-blue-600 px-4 py-2 text-white">Add truck</button>
        <p className="mt-2 text-xs text-gray-500">No documents needed. The price for your truck comes from the admin's rate table — you do not set a per-km rate.</p>
      </form>

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {trucks.length === 0 ? (
          <EmptyState title="No trucks yet" message="Add your first truck above to start receiving bookings." />
        ) : (
          trucks.map((t) => (
            <div key={t.id}>
              <TruckCard truck={t} />
              <button
                onClick={() => handleToggle(t)}
                disabled={t.availabilityStatus === "ON_TRIP"}
                className="mt-2 w-full rounded border px-3 py-2 text-sm disabled:cursor-not-allowed disabled:text-gray-400"
              >
                {t.availabilityStatus === "ON_TRIP"
                  ? "On a trip — cannot change now"
                  : t.availabilityStatus === "AVAILABLE" ? "Mark unavailable" : "Mark available"}
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
