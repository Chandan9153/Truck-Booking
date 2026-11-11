import { useEffect, useState } from "react";
import { fetchMyTrips, updateTripStatus } from "../../services/tripService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

// Same forward-only status rule as the owner page (Part 10). The driver
// can move their own trip to the next step — nothing else.
const NEXT_STATUS = {
  ASSIGNED: "PICKED_UP",
  PICKED_UP: "IN_TRANSIT",
  IN_TRANSIT: "DELIVERED",
  DELIVERED: "COMPLETED",
};

export default function DriverTrips() {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchMyTrips()
      .then((data) => setTrips(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load your trips."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleNext(trip) {
    const next = NEXT_STATUS[trip.status];
    if (!next) return;
    try {
      await updateTripStatus(trip.id, { status: next });
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
        <div className="mt-6"><EmptyState title="No trips assigned" message="Trips assigned to you will appear here." /></div>
      ) : (
        <div className="mt-6 space-y-4">
          {trips.map((t) => (
            <div key={t.id} className="rounded border bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Trip #{t.id} · Booking #{t.bookingId}</p>
                  <p className="text-sm text-gray-600">{t.pickup} → {t.destination}</p>
                </div>
                <StatusBadge status={t.status} />
              </div>
              {NEXT_STATUS[t.status] && (
                <button onClick={() => handleNext(t)} className="mt-3 rounded bg-blue-600 px-4 py-2 text-sm text-white">
                  Mark {NEXT_STATUS[t.status].replaceAll("_", " ").toLowerCase()}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
