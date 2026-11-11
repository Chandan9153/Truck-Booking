import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchMyBookings, acceptBooking, rejectBooking } from "../../services/bookingService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDateTime } from "../../utils/format";

export default function IncomingBookings() {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchMyBookings()
      .then((data) => setBookings(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load bookings."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleAccept(id) {
    try { await acceptBooking(id); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not accept this booking."); }
  }

  async function handleReject(id) {
    const reason = window.prompt("Why are you rejecting this booking? (optional)") || "";
    try { await rejectBooking(id, reason); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not reject this booking."); }
  }

  if (loading) return <LoadingSpinner text="Loading bookings..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Incoming Bookings</h1>
      <p className="mt-1 text-sm text-gray-600">Accept a booking to confirm it. Rejecting frees the truck for other customers.</p>
      <ErrorBox message={error} />
      {bookings.length === 0 ? (
        <div className="mt-6"><EmptyState title="No bookings yet" message="New bookings for your trucks will appear here." /></div>
      ) : (
        <div className="mt-6 space-y-4">
          {bookings.map((b) => (
            <div key={b.id} className="rounded border bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">Booking #{b.id} · {b.truck?.registrationNumber || b.registrationNumber || ""}</p>
                  <p className="text-sm text-gray-600">{b.pickup} → {b.destination} · {b.loadTons} tons</p>
                  <p className="text-sm text-gray-500">{formatDateTime(b.createdAt)} · {formatRupees(b.totalFare ?? b.totalAmount)} · {b.customerName || b.customer?.fullName || "Customer"}</p>
                </div>
                <StatusBadge status={b.status} />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <Link to={`/track/${b.id}`} className="rounded border px-3 py-1 text-sm">View &amp; chat</Link>
                {b.status === "PENDING" && (
                  <>
                    <button onClick={() => handleAccept(b.id)} className="rounded bg-green-600 px-3 py-1 text-sm text-white">Accept</button>
                    <button onClick={() => handleReject(b.id)} className="rounded border border-red-300 px-3 py-1 text-sm text-red-700">Reject</button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
