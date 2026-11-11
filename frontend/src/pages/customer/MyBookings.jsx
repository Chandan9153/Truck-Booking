import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchMyBookings, cancelBooking } from "../../services/bookingService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDateTime } from "../../utils/format";

// Only these statuses can be cancelled by the customer in the UI.
// The backend enforces the same rule (Module 4) — this only hides the button.
const CANCELLABLE = ["PENDING", "CONFIRMED"];

export default function MyBookings() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchMyBookings()
      .then((data) => setBookings(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load your bookings."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCancel(id) {
    if (!window.confirm("Cancel this booking? The truck will become available again.")) return;
    try {
      await cancelBooking(id);
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not cancel this booking.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading your bookings..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">My Bookings</h1>
      <ErrorBox message={error} />
      {bookings.length === 0 ? (
        <div className="mt-6">
          <EmptyState title="No bookings yet" message="Search for a truck and book your first trip." actionLabel="Search trucks" onAction={() => (window.location.href = "/")} />
        </div>
      ) : (
        <div className="mt-6 space-y-4">
          {bookings.map((b) => (
            <div key={b.id} className="rounded border bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Booking #{b.id} · {b.truck?.registrationNumber || b.registrationNumber || ""}</p>
                  <p className="text-sm text-gray-600">{b.pickup} → {b.destination}</p>
                  <p className="text-sm text-gray-500">{formatDateTime(b.createdAt)} · {formatRupees(b.totalFare ?? b.totalAmount)}</p>
                </div>
                <StatusBadge status={b.status} />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <Link to={`/track/${b.id}`} className="rounded bg-blue-600 px-3 py-1 text-sm text-white">Track &amp; chat</Link>
                {CANCELLABLE.includes(b.status) && (
                  <button onClick={() => handleCancel(b.id)} className="rounded border border-red-300 px-3 py-1 text-sm text-red-700">Cancel booking</button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
