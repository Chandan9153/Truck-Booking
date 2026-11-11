import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchMyBookings } from "../../services/bookingService";
import { fetchMySettlements } from "../../services/paymentService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import { formatRupees } from "../../utils/format";

export default function OwnerDashboard() {
  const [bookings, setBookings] = useState([]);
  const [settlements, setSettlements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function load() {
      try {
        const b = await fetchMyBookings();
        setBookings(b || []);
        // Settlements are a nice-to-have — if Module 6 is not ready, the
        // dashboard still works with bookings alone.
        fetchMySettlements().then((s) => setSettlements(s || [])).catch(() => setSettlements([]));
      } catch (err) {
        setError(err.response?.data?.message || "Could not load your dashboard.");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  if (loading) return <LoadingSpinner text="Loading your dashboard..." />;

  const active = bookings.filter((b) => ["PENDING", "CONFIRMED", "DRIVER_ASSIGNED", "PICKED_UP", "IN_TRANSIT"].includes(b.status));
  const pending = bookings.filter((b) => b.status === "PENDING");
  const pendingAmount = settlements.filter((s) => s.status !== "PAID").reduce((sum, s) => sum + Number(s.amount || 0), 0);
  const paidAmount = settlements.filter((s) => s.status === "PAID").reduce((sum, s) => sum + Number(s.amount || 0), 0);

  return (
    <div>
      <h1 className="text-2xl font-bold">Owner Dashboard</h1>
      <ErrorBox message={error} />
      <div className="mt-6 grid gap-4 md:grid-cols-4">
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Active bookings</p><p className="text-2xl font-bold">{active.length}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Waiting for your answer</p><p className="text-2xl font-bold text-amber-600">{pending.length}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Earnings pending</p><p className="text-2xl font-bold">{formatRupees(pendingAmount)}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Earnings received</p><p className="text-2xl font-bold text-green-700">{formatRupees(paidAmount)}</p></div>
      </div>
      <div className="mt-6 flex flex-wrap gap-3">
        <Link to="/owner/bookings" className="rounded bg-blue-600 px-4 py-2 text-white">Review incoming bookings ({pending.length})</Link>
        <Link to="/owner/trucks" className="rounded border px-4 py-2">Manage my trucks</Link>
        <Link to="/owner/trips" className="rounded border px-4 py-2">My trips</Link>
      </div>
    </div>
  );
}
