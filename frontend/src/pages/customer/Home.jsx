import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchCategories } from "../../services/truckService";
import ErrorBox from "../../components/ErrorBox";

// Must match the backend BodyType enum EXACTLY (Module 2). The old list
// had CLOSED and TANKER, which the backend does not have — choosing them
// made truck registration fail with a 500 error.
const BODY_TYPES = ["", "OPEN", "CLOSED_CONTAINER", "FLATBED", "TRAILER", "REFRIGERATED"];

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

  // Per-ton-per-km rates from the live rate table (Module 3, seeded).
  const RATE_BY_TYRES = { 12: "₹2.70", 14: "₹2.60", 18: "₹2.50", 22: "₹2.40" };
  const BODY_LABELS = {
    OPEN: "Open",
    CLOSED_CONTAINER: "Closed Container",
    FLATBED: "Flatbed",
    TRAILER: "Trailer",
    REFRIGERATED: "Refrigerated",
  };

  return (
    <div>
      {/* Hero */}
      <section className="rounded-2xl bg-gradient-to-r from-blue-800 via-blue-700 to-blue-600 px-6 py-10 text-white shadow md:px-10">
        <p className="text-sm font-semibold uppercase tracking-wider text-blue-200">
          Truck Booking • All India
        </p>
        <h1 className="mt-2 max-w-2xl text-3xl font-extrabold leading-tight md:text-4xl">
          Book a truck across India — in minutes, not days.
        </h1>
        <p className="mt-3 max-w-2xl text-blue-100">
          Full truck loads with upfront fares and every rupee explained.
          No hidden charges, no waiting for a price call-back.
        </p>
        <div className="mt-5 flex flex-wrap gap-2 text-sm">
          <span className="rounded-full bg-white/15 px-3 py-1">₹0 platform fee</span>
          <span className="rounded-full bg-white/15 px-3 py-1">Instant booking</span>
          <span className="rounded-full bg-white/15 px-3 py-1">Cheapest truck first</span>
          <span className="rounded-full bg-white/15 px-3 py-1">Call or chat the owner</span>
        </div>
      </section>

      <form onSubmit={handleSubmit} className="mt-6 rounded-xl border bg-white p-6 shadow">
        <h2 className="text-lg font-bold">Find your truck</h2>
        <p className="mt-1 text-sm text-gray-600">
          Tell us your load and route. We show the cheapest trucks first, with every rupee explained.
        </p>
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
              {BODY_TYPES.filter(Boolean).map((b) => (<option key={b} value={b}>{BODY_LABELS[b] || b}</option>))}
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
        <button className="mt-6 w-full rounded-lg bg-blue-600 py-3 font-semibold text-white hover:bg-blue-700 md:w-auto md:px-8">
          Search trucks →
        </button>
      </form>

      {/* Example fare — the standard worked example from the rate table */}
      <div className="mt-6 rounded-xl border border-blue-100 bg-blue-50 p-4 text-sm text-blue-900">
        <span className="font-semibold">Example fare: </span>
        Bengaluru → Hyderabad • 570 km • 20 tons, 12-tyre truck — <span className="font-bold">₹33,280</span> all-in
        (base fare + toll + loading, ₹0 platform fee). Your quote shows the same full breakdown.
      </div>

      {/* Truck categories, loaded live from the backend */}
      {categories.length > 0 && (
        <section className="mt-10">
          <h2 className="text-xl font-bold">Trucks for every load</h2>
          <p className="mt-1 text-sm text-gray-600">Four truck sizes, priced per ton per km. Pick a category or leave it on “Any”.</p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {categories.map((c) => (
              <div key={c.id} className="rounded-xl border bg-white p-4 shadow-sm">
                <p className="text-3xl font-extrabold text-blue-700">{c.tyreCount} <span className="text-base font-semibold">tyre</span></p>
                <p className="mt-1 font-semibold">{c.name}</p>
                <p className="text-sm text-gray-600">Up to {c.targetPayloadTons} tons</p>
                <p className="mt-2 text-sm font-semibold text-green-700">
                  {RATE_BY_TYRES[c.tyreCount] || ""} / ton / km
                </p>
                {c.description && <p className="mt-1 text-xs text-gray-500">{c.description}</p>}
              </div>
            ))}
          </div>
        </section>
      )}

      {/* How it works */}
      <section className="mt-10">
        <h2 className="text-xl font-bold">How it works</h2>
        <div className="mt-4 grid gap-4 md:grid-cols-3">
          <div className="rounded-xl border bg-white p-5 shadow-sm"><p className="text-2xl font-extrabold text-blue-700">1</p><p className="mt-2 font-semibold">Search your route</p><p className="text-sm text-gray-600">Enter pickup, destination, distance and load. See the cheapest trucks first with the full fare breakdown.</p></div>
          <div className="rounded-xl border bg-white p-5 shadow-sm"><p className="text-2xl font-extrabold text-blue-700">2</p><p className="mt-2 font-semibold">Book instantly</p><p className="text-sm text-gray-600">Book straight from a quote at the shown price. Pay cash on delivery — no advance needed.</p></div>
          <div className="rounded-xl border bg-white p-5 shadow-sm"><p className="text-2xl font-extrabold text-blue-700">3</p><p className="mt-2 font-semibold">Track &amp; talk</p><p className="text-sm text-gray-600">Follow the trip from pickup to delivery, and call or chat with the truck owner any time.</p></div>
        </div>
      </section>

      {/* Popular routes — examples only, fares depend on load and distance */}
      <section className="mt-10">
        <h2 className="text-xl font-bold">Popular routes</h2>
        <p className="mt-1 text-sm text-gray-600">Search any route in India — here are a few to start with.</p>
        <div className="mt-4 flex flex-wrap gap-2">
          {["Bengaluru → Hyderabad", "Bengaluru → Chennai", "Mumbai → Pune", "Delhi → Jaipur", "Hyderabad → Vijayawada", "Chennai → Coimbatore"].map((r) => (
            <span key={r} className="rounded-full border bg-white px-4 py-2 text-sm shadow-sm">{r}</span>
          ))}
        </div>
      </section>

      <div className="mt-10 grid gap-4 md:grid-cols-3">
        <div className="rounded-xl border bg-white p-4 shadow-sm"><p className="font-semibold">Upfront fare</p><p className="text-sm text-gray-600">Every quote shows the full breakdown — base fare, toll, loading, platform fee.</p></div>
        <div className="rounded-xl border bg-white p-4 shadow-sm"><p className="font-semibold">Instant booking</p><p className="text-sm text-gray-600">Book straight from a quote. No waiting for a price call-back.</p></div>
        <div className="rounded-xl border bg-white p-4 shadow-sm"><p className="font-semibold">Talk to the owner</p><p className="text-sm text-gray-600">Call or chat with the truck owner after booking, from the tracking page.</p></div>
      </div>
    </div>
  );
}
