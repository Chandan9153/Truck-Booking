import { useEffect, useState } from "react";
import { fetchMyTrips, assignDriver, updateTripStatus } from "../../services/tripService";
import { fetchMyDrivers } from "../../services/driverService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatDateTime } from "../../utils/format";

// Trip status order (Module 5). The owner can only move a trip forward
// to the NEXT step — never backwards, never skipping.
const NEXT_STATUS = {
  ASSIGNED: "PICKED_UP",
  PICKED_UP: "IN_TRANSIT",
  IN_TRANSIT: "DELIVERED",
  DELIVERED: "COMPLETED",
};
const NEXT_LABEL = {
  PICKED_UP: "Mark picked up",
  IN_TRANSIT: "Mark in transit",
  DELIVERED: "Mark delivered",
  COMPLETED: "Mark completed",
};

// ⚠️ ASSUMED endpoints — check against Module 5 (see Part 0).
export default function MyTrips() {
  const [trips, setTrips] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [proof, setProof] = useState({}); // tripId -> { note, url }

  function load() {
    setLoading(true);
    Promise.all([fetchMyTrips(), fetchMyDrivers().catch(() => [])])
      .then(([t, d]) => { setTrips(t || []); setDrivers(d || []); })
      .catch((err) => setError(err.response?.data?.message || "Could not load your trips."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleAssign(tripId, driverId) {
    if (!driverId) return;
    try { await assignDriver(tripId, Number(driverId)); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not assign this driver."); }
  }

  async function handleNextStatus(trip) {
    const next = NEXT_STATUS[trip.status];
    if (!next) return;
    const p = proof[trip.id] || {};
    try {
      await updateTripStatus(trip.id, {
        status: next,
        deliveryProofNote: next === "DELIVERED" ? p.note || null : null,
        deliveryProofUrl: next === "DELIVERED" ? p.url || null : null,
      });
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not update this trip.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading your trips..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">My Trips</h1>
      <ErrorBox message={error} />
      {trips.length === 0 ? (
        <div className="mt-6"><EmptyState title="No trips yet" message="Trips appear here once you accept a booking and assign a driver." /></div>
      ) : (
        <div className="mt-6 space-y-4">
          {trips.map((t) => (
            <div key={t.id} className="rounded border bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Trip #{t.id} · Booking #{t.bookingId}</p>
                  <p className="text-sm text-gray-600">{t.pickup} → {t.destination}</p>
                  <p className="text-sm text-gray-500">Driver: {t.driver?.fullName || t.driverName || "Not assigned"} · {formatDateTime(t.createdAt)}</p>
                </div>
                <StatusBadge status={t.status} />
              </div>

              {!t.driver && (
                <div className="mt-3 flex gap-2">
                  <select defaultValue="" onChange={(e) => handleAssign(t.id, e.target.value)} className="rounded border px-3 py-2 text-sm">
                    <option value="">Assign a driver...</option>
                    {drivers.filter((d) => d.active !== false).map((d) => (
                      <option key={d.id} value={d.id}>{d.fullName} — {d.phone}</option>
                    ))}
                  </select>
                </div>
              )}

              {NEXT_STATUS[t.status] && (
                <div className="mt-3">
                  {t.status === "IN_TRANSIT" && (
                    <div className="mb-2 grid gap-2 md:grid-cols-2">
                      <input value={proof[t.id]?.note || ""} onChange={(e) => setProof((p) => ({ ...p, [t.id]: { ...p[t.id], note: e.target.value } }))} className="rounded border px-3 py-2 text-sm" placeholder="Delivery proof note (e.g. Received by Ramesh, gate 2)" />
                      <input value={proof[t.id]?.url || ""} onChange={(e) => setProof((p) => ({ ...p, [t.id]: { ...p[t.id], url: e.target.value } }))} className="rounded border px-3 py-2 text-sm" placeholder="Proof photo / document URL (optional)" />
                    </div>
                  )}
                  <button onClick={() => handleNextStatus(t)} className="rounded bg-blue-600 px-4 py-2 text-sm text-white">
                    {NEXT_LABEL[NEXT_STATUS[t.status]]}
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
