import { useEffect, useState } from "react";
import { fetchMyDrivers, addDriver, updateDriver, deactivateDriver } from "../../services/driverService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

// ⚠️ ASSUMED endpoints — check against Module 5 (see Part 0).
export default function MyDrivers() {
  const [drivers, setDrivers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ fullName: "", phone: "", licenceNumber: "" });
  const [editingId, setEditingId] = useState(null);
  const [formError, setFormError] = useState("");

  function load() {
    setLoading(true);
    fetchMyDrivers()
      .then((data) => setDrivers(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load your drivers."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError("");
    if (form.fullName.trim().length < 2) return setFormError("Enter the driver's full name.");
    if (!/^[6-9][0-9]{9}$/.test(form.phone.trim())) return setFormError("Phone must be a 10-digit Indian mobile number.");
    try {
      if (editingId) await updateDriver(editingId, form);
      else await addDriver(form);
      setForm({ fullName: "", phone: "", licenceNumber: "" });
      setEditingId(null);
      load();
    } catch (err) {
      setFormError(err.response?.data?.message || "Could not save this driver.");
    }
  }

  async function handleDeactivate(id) {
    if (!window.confirm("Deactivate this driver? They will no longer receive new trips.")) return;
    try { await deactivateDriver(id); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not deactivate this driver."); }
  }

  if (loading) return <LoadingSpinner text="Loading your drivers..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">My Drivers</h1>
      <ErrorBox message={error} />
      <form onSubmit={handleSubmit} className="mt-6 rounded border bg-white p-5">
        <h2 className="font-semibold">{editingId ? "Edit driver" : "Add a driver"}</h2>
        {formError && <p className="mt-2 text-sm text-red-600">{formError}</p>}
        <div className="mt-4 grid gap-4 md:grid-cols-3">
          <input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} className="rounded border px-3 py-2" placeholder="Full name" />
          <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className="rounded border px-3 py-2" placeholder="10-digit phone" />
          <input value={form.licenceNumber} onChange={(e) => setForm({ ...form, licenceNumber: e.target.value })} className="rounded border px-3 py-2" placeholder="Driving licence number" />
        </div>
        <div className="mt-4 flex gap-2">
          <button className="rounded bg-blue-600 px-4 py-2 text-white">{editingId ? "Save changes" : "Add driver"}</button>
          {editingId && (<button type="button" onClick={() => { setEditingId(null); setForm({ fullName: "", phone: "", licenceNumber: "" }); }} className="rounded border px-4 py-2">Cancel edit</button>)}
        </div>
      </form>
      <div className="mt-6 space-y-3">
        {drivers.length === 0 ? (
          <EmptyState title="No drivers yet" message="Add a driver above so you can assign trips to them." />
        ) : (
          drivers.map((d) => (
            <div key={d.id} className="flex flex-wrap items-center justify-between gap-2 rounded border bg-white p-4">
              <div>
                <p className="font-semibold">{d.fullName}</p>
                <p className="text-sm text-gray-600">{d.phone} · Licence: {d.licenceNumber || "—"} · {d.active === false ? "Deactivated" : "Active"}</p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => { setEditingId(d.id); setForm({ fullName: d.fullName, phone: d.phone, licenceNumber: d.licenceNumber || "" }); }} className="rounded border px-3 py-1 text-sm">Edit</button>
                {d.active !== false && (<button onClick={() => handleDeactivate(d.id)} className="rounded border border-red-300 px-3 py-1 text-sm text-red-700">Deactivate</button>)}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
