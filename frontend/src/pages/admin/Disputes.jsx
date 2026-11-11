import { useEffect, useState } from "react";
import { fetchAllBookings } from "../../services/adminService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDateTime } from "../../utils/format";

// Disputes placeholder (Module 7 scope): there is no separate disputes
// endpoint yet, so this page lists bookings whose status is DISPUTED.
// A full dispute workflow (raise dispute, evidence, resolution) is a
// later module — this page makes sure disputed bookings are at least
// visible to the admin instead of hidden.
export default function Disputes() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchAllBookings()
      .then((data) => setBookings((data || []).filter((b) => b.status === "DISPUTED")))
      .catch((err) => setError(err.response?.data?.message || "Could not load disputed bookings."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading disputes..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Disputes</h1>
      <p className="mt-1 text-sm text-gray-600">Bookings marked DISPUTED. Contact the customer and owner outside the app to resolve them — there is no in-app resolution flow yet.</p>
      <ErrorBox message={error} />
      {bookings.length === 0 ? (
        <div className="mt-6"><EmptyState title="No disputes" message="No booking is currently disputed. Good news." /></div>
      ) : (
        <div className="mt-6 space-y-4">
          {bookings.map((b) => (
            <div key={b.id} className="rounded border bg-white p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Booking #{b.id}</p>
                  <p className="text-sm text-gray-600">{b.pickup} → {b.destination} · {formatRupees(b.totalFare ?? b.totalAmount)}</p>
                  <p className="text-sm text-gray-500">{formatDateTime(b.createdAt)}</p>
                </div>
                <StatusBadge status={b.status} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
