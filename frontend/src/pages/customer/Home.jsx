import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchCategories } from "../../services/truckService";
import ErrorBox from "../../components/ErrorBox";

const BODY_TYPES = ["", "OPEN", "CLOSED", "REFRIGERATED", "TANKER", "FLATBED"];
// ⚠️ If your Module 2 BodyType enum uses different names, change this list
//    to match it exactly — the backend rejects unknown values with a 400.

export default function Home() {
  const navigate = useNavigate();
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState({
    pickup: "", destination: "", distanceKm: "", loadTons: "",
    goodsType: "", bodyType: "", categoryId: "", tollCharges: "", loadingCharges: "",
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");

  useEffect(() => {
    // Categories fill the dropdown. If this call fails, the form still works —
    // the customer can simply leave "Any category" selected.
    fetchCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
    setErrors((e) => ({ ...e, [field]: "" }));
  }

  function validate() {
    const e = {};
    if (!form.pickup.trim()) e.pickup = "Enter the pickup place.";
    if (!form.destination.trim()) e.destination = "Enter the destination.";
    if (!form.distanceKm || Number(form.distanceKm) < 1 || Number(form.distanceKm) > 5000)
      e.distanceKm = "Distance must be between 1 and 5000 km.";
    if (!form.loadTons || Number(form.loadTons) < 0.1 || Number(form.loadTons) > 40)
      e.loadTons = "Load must be between 0.1 and 40 tons.";
    if (form.tollCharges && Number(form.tollCharges) < 0) e.tollCharges = "Toll charges cannot be negative.";
    if (form.loadingCharges && Number(form.loadingCharges) < 0) e.loadingCharges = "Loading charges cannot be negative.";
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function handleSubmit(e) {
    e.preventDefault();
    setError("");
    if (!validate()) return;
    // The search inputs travel in router state — SearchResults reads them
    // and calls the API. No price is calculated here, ever.
    navigate("/search-results", { state: { search: form } });
  }

  const input = "mt-1 w-full rounded border px-3 py-2";
  const label = "text-sm font-medium";
  const errText = "mt-1 text-sm text-red-600";

  return (
    <div>
      <h1 className="text-3xl font-bold">Book a truck across India</h1>
      <p className="mt-1 text-gray-600">
        Tell us your load and route. We show the cheapest trucks first, with every rupee explained — no hidden charges.
      </p>

      <form onSubmit={handleSubmit} className="mt-6 rounded border bg-white p-6 shadow-sm">
        <ErrorBox message={error} />
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className={label}>Pickup place *</label>
            <input value={form.pickup} onChange={(e) => update("pickup", e.target.value)} className={input} placeholder="e.g. Bengaluru" />
            {errors.pickup && <p className={errText}>{errors.pickup}</p>}
          </div>
          <div>
            <label className={label}>Destination *</label>
            <input value={form.destination} onChange={(e) => update("destination", e.target.value)} className={input} placeholder="e.g. Hyderabad" />
            {errors.destination && <p className={errText}>{errors.destination}</p>}
          </div>
          <div>
            <label className={label}>Distance (km) *</label>
            <input type="number" min="1" max="5000" value={form.distanceKm} onChange={(e) => update("distanceKm", e.target.value)} className={input} placeholder="e.g. 570 — check Google Maps" />
            {errors.distanceKm && <p className={errText}>{errors.distanceKm}</p>}
            <p className="mt-1 text-xs text-gray-500">Tip: search the route on Google Maps and type the km here.</p>
          </div>
          <div>
            <label className={label}>Load (tons) *</label>
            <input type="number" step="0.1" min="0.1" max="40" value={form.loadTons} onChange={(e) => update("loadTons", e.target.value)} className={input} placeholder="e.g. 20" />
            {errors.loadTons && <p className={errText}>{errors.loadTons}</p>}
          </div>
          <div>
            <label className={label}>Goods type</label>
            <input value={form.goodsType} onChange={(e) => update("goodsType", e.target.value)} className={input} placeholder="e.g. Rice, Steel, Medicine" />
          </div>
          <div>
            <label className={label}>Body type</label>
            <select value={form.bodyType} onChange={(e) => update("bodyType", e.target.value)} className={input}>
              <option value="">Any body type</option>
              {BODY_TYPES.filter(Boolean).map((b) => (<option key={b} value={b}>{b}</option>))}
            </select>
          </div>
          <div>
            <label className={label}>Truck category</label>
            <select value={form.categoryId} onChange={(e) => update("categoryId", e.target.value)} className={input}>
              <option value="">Any category</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name} — {c.targetPayloadTons} tons</option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={label}>Toll (₹, optional)</label>
              <input type="number" min="0" value={form.tollCharges} onChange={(e) => update("tollCharges", e.target.value)} className={input} placeholder="0" />
              {errors.tollCharges && <p className={errText}>{errors.tollCharges}</p>}
            </div>
            <div>
              <label className={label}>Loading (₹, optional)</label>
              <input type="number" min="0" value={form.loadingCharges} onChange={(e) => update("loadingCharges", e.target.value)} className={input} placeholder="0" />
              {errors.loadingCharges && <p className={errText}>{errors.loadingCharges}</p>}
            </div>
          </div>
        </div>
        <button className="mt-6 w-full rounded bg-blue-600 py-3 font-medium text-white hover:bg-blue-700 md:w-auto md:px-8">
          Search trucks →
        </button>
      </form>

      <div className="mt-8 grid gap-4 md:grid-cols-3">
        <div className="rounded border bg-white p-4"><p className="font-semibold">Upfront fare</p><p className="text-sm text-gray-600">Every quote shows the full breakdown — base fare, toll, loading, platform fee.</p></div>
        <div className="rounded border bg-white p-4"><p className="font-semibold">Instant booking</p><p className="text-sm text-gray-600">Book straight from a quote. No waiting for a price call-back.</p></div>
        <div className="rounded border bg-white p-4"><p className="font-semibold">Talk to the owner</p><p className="text-sm text-gray-600">Call or chat with the truck owner after booking, from the tracking page.</p></div>
      </div>
    </div>
  );
}
