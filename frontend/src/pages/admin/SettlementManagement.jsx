import { useEffect, useState } from "react";
import { fetchAllSettlements, markSettlementPaid } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";
import { formatRupees, formatDate } from "../../utils/format";

// ⚠️ ASSUMED endpoints — check against Module 6.
export default function SettlementManagement() {
  const [settlements, setSettlements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchAllSettlements()
      .then((data) => setSettlements(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load settlements."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleMarkPaid(id) {
    if (!window.confirm("Mark this settlement as PAID? Only do this after the money has actually been sent to the owner.")) return;
    try { await markSettlementPaid(id); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not mark this settlement as paid."); }
  }

  if (loading) return <LoadingSpinner text="Loading settlements..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Settlement Management</h1>
      <ErrorBox message={error} />
      {settlements.length === 0 ? (
        <div className="mt-6"><EmptyState title="No settlements yet" message="Settlements appear here after completed trips." /></div>
      ) : (
        <table className="mt-6 w-full rounded border bg-white text-sm">
          <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Booking</th><th className="p-3">Owner</th><th className="p-3">Amount</th><th className="p-3">Status</th><th className="p-3">Date</th><th className="p-3">Action</th></tr></thead>
          <tbody>
            {settlements.map((s) => (
              <tr key={s.id} className="border-b">
                <td className="p-3">#{s.bookingId}</td>
                <td className="p-3">{s.ownerName || s.owner?.fullName || "—"}</td>
                <td className="p-3">{formatRupees(s.amount)}</td>
                <td className="p-3">{s.status}</td>
                <td className="p-3">{formatDate(s.createdAt)}</td>
                <td className="p-3">
                  {s.status !== "PAID" && (<button onClick={() => handleMarkPaid(s.id)} className="rounded bg-green-600 px-3 py-1 text-white">Mark PAID</button>)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
