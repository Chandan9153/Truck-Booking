import { useEffect, useState } from "react";
import { fetchMySettlements } from "../../services/paymentService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDate } from "../../utils/format";

// ⚠️ ASSUMED endpoint — check against Module 6 (see Part 0).
// COD is the default: the customer pays cash on delivery, and the platform
// settles the owner's share afterwards. This page only SHOWS settlements;
// it never creates or edits money.
export default function Earnings() {
  const [settlements, setSettlements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchMySettlements()
      .then((data) => setSettlements(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load your earnings."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading your earnings..." />;

  const pending = settlements.filter((s) => s.status !== "PAID").reduce((sum, s) => sum + Number(s.amount || 0), 0);
  const paid = settlements.filter((s) => s.status === "PAID").reduce((sum, s) => sum + Number(s.amount || 0), 0);

  return (
    <div>
      <h1 className="text-2xl font-bold">Earnings</h1>
      <ErrorBox message={error} />
      <div className="mt-6 grid gap-4 md:grid-cols-2">
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Pending settlement</p><p className="text-2xl font-bold text-amber-600">{formatRupees(pending)}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Received</p><p className="text-2xl font-bold text-green-700">{formatRupees(paid)}</p></div>
      </div>
      {settlements.length === 0 ? (
        <div className="mt-6"><EmptyState title="No settlements yet" message="Settlements appear here after completed trips." /></div>
      ) : (
        <table className="mt-6 w-full rounded border bg-white text-sm">
          <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Booking</th><th className="p-3">Amount</th><th className="p-3">Status</th><th className="p-3">Date</th></tr></thead>
          <tbody>
            {settlements.map((s) => (
              <tr key={s.id} className="border-b">
                <td className="p-3">#{s.bookingId}</td>
                <td className="p-3">{formatRupees(s.amount)}</td>
                <td className="p-3">{s.status}</td>
                <td className="p-3">{formatDate(s.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
