import { useEffect, useState } from "react";
import { fetchAllBookings } from "../../services/adminService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDateTime } from "../../utils/format";

// ⚠️ ASSUMED endpoint — check against Module 4. Read-only: the admin
// watches bookings here; they do not accept, reject or cancel them.
export default function BookingManagement() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchAllBookings()
      .then((data) => setBookings(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load bookings."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading bookings..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Booking Management</h1>
      <ErrorBox message={error} />
      {bookings.length === 0 ? (
        <div className="mt-6"><EmptyState title="No bookings yet" message="All bookings on the platform will appear here." /></div>
      ) : (
        <table className="mt-6 w-full rounded border bg-white text-sm">
          <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">ID</th><th className="p-3">Route</th><th className="p-3">Fare</th><th className="p-3">Status</th><th className="p-3">Created</th></tr></thead>
          <tbody>
            {bookings.map((b) => (
              <tr key={b.id} className="border-b">
                <td className="p-3">#{b.id}</td>
                <td className="p-3">{b.pickup} → {b.destination}</td>
                <td className="p-3">{formatRupees(b.totalFare ?? b.totalAmount)}</td>
                <td className="p-3"><StatusBadge status={b.status} /></td>
                <td className="p-3">{formatDateTime(b.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
