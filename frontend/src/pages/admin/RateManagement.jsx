import { useEffect, useState } from "react";
import { fetchRates, updateRate } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import { formatRupees } from "../../utils/format";

export default function RateManagement() {
  const [rates, setRates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState({}); // rateId -> new rate string

  function load() {
    setLoading(true);
    fetchRates()
      .then((data) => setRates(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load rates."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleSave(rate) {
    const value = Number(editing[rate.id]);
    // Same guard as the backend (Module 3): ₹0.10–₹20.00 per ton per km.
    // A typo like 270 would price a trip at lakhs — catch it here AND there.
    if (!value || value < 0.1 || value > 20) {
      setError("Rate must be between ₹0.10 and ₹20.00 per ton per km.");
      return;
    }
    try {
      await updateRate(rate.id, { ratePerTonKm: value, active: rate.active });
      setEditing((e) => ({ ...e, [rate.id]: "" }));
      load();
    } catch (err) {
      setError(err.response?.data?.message || "Could not update this rate.");
    }
  }

  if (loading) return <LoadingSpinner text="Loading rates..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Rate Management</h1>
      <p className="mt-1 text-sm text-gray-600">Changing a rate affects new quotes only. Old quotes keep their original rate.</p>
      <ErrorBox message={error} />
      <table className="mt-6 w-full rounded border bg-white text-sm">
        <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Category</th><th className="p-3">Tyres</th><th className="p-3">Rate / ton / km</th><th className="p-3">New rate</th><th className="p-3">Action</th></tr></thead>
        <tbody>
          {rates.map((r) => (
            <tr key={r.id} className="border-b">
              <td className="p-3">{r.categoryName}</td>
              <td className="p-3">{r.tyreCount}</td>
              <td className="p-3">{formatRupees(r.ratePerTonKm)}</td>
              <td className="p-3">
                <input type="number" step="0.01" min="0.1" max="20" value={editing[r.id] ?? ""} onChange={(e) => setEditing((ed) => ({ ...ed, [r.id]: e.target.value }))} className="w-24 rounded border px-2 py-1" placeholder={String(r.ratePerTonKm)} />
              </td>
              <td className="p-3"><button onClick={() => handleSave(r)} className="rounded bg-blue-600 px-3 py-1 text-white">Save</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
