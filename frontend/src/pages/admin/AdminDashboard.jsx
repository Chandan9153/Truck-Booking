import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchAdminStats } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";

// ⚠️ ASSUMED endpoint GET /api/admin/stats — check against the admin work
//    in Modules 2/4/6. If it does not exist yet, this page can count the
//    lists instead (users + bookings + settlements) — see the note below.
export default function AdminDashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchAdminStats()
      .then(setStats)
      .catch((err) => setError(err.response?.data?.message || "Could not load admin stats."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading admin dashboard..." />;

  const cards = [
    { label: "Total users", value: stats?.totalUsers, to: "/admin/users" },
    { label: "Total bookings", value: stats?.totalBookings, to: "/admin/bookings" },
    { label: "Active trucks", value: stats?.activeTrucks, to: "/admin/bookings" },
    { label: "Disputed bookings", value: stats?.disputedBookings, to: "/admin/disputes" },
    { label: "Pending settlements", value: stats?.pendingSettlements, to: "/admin/settlements" },
  ];

  return (
    <div>
      <h1 className="text-2xl font-bold">Admin Dashboard</h1>
      <ErrorBox message={error} />
      {stats ? (
        <div className="mt-6 grid gap-4 md:grid-cols-3">
          {cards.map((c) => (
            <Link key={c.label} to={c.to} className="rounded border bg-white p-4 hover:shadow">
              <p className="text-sm text-gray-500">{c.label}</p>
              <p className="text-2xl font-bold">{c.value ?? "—"}</p>
            </Link>
          ))}
        </div>
      ) : (
        <p className="mt-6 text-sm text-gray-600">
          Stats endpoint not available yet — use the pages in the top menu (Users, Bookings, Settlements, Disputes) until <code>GET /api/admin/stats</code> exists.
        </p>
      )}
    </div>
  );
}
