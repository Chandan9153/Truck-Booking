import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchMyTrips } from "../../services/tripService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";

export default function DriverDashboard() {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchMyTrips()
      .then((data) => setTrips(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load trips."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading your dashboard..." />;

  const today = trips.filter((t) => ["ASSIGNED", "PICKED_UP", "IN_TRANSIT"].includes(t.status));

  return (
    <div>
      <h1 className="text-2xl font-bold">Driver Dashboard</h1>
      <p className="mt-1 text-sm text-gray-600">
        Read-only in this MVP — the owner manages your trips from their account (see Part 11 note). Once driver login is added, this page will show only your own trips.
      </p>
      <ErrorBox message={error} />
      <div className="mt-6 grid gap-4 md:grid-cols-2">
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Trips in progress</p><p className="text-2xl font-bold">{today.length}</p></div>
        <div className="rounded border bg-white p-4"><p className="text-sm text-gray-500">Total trips</p><p className="text-2xl font-bold">{trips.length}</p></div>
      </div>
      <Link to="/driver/trips" className="mt-6 inline-block rounded bg-blue-600 px-4 py-2 text-white">View my trips →</Link>
    </div>
  );
}
