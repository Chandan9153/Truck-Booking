import { useEffect, useState } from "react";
import { fetchAllUsers, deactivateUser } from "../../services/adminService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

// ⚠️ ASSUMED endpoints — check against the admin work in Modules 1/6.
export default function UserManagement() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function load() {
    setLoading(true);
    fetchAllUsers()
      .then((data) => setUsers(data || []))
      .catch((err) => setError(err.response?.data?.message || "Could not load users."))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleDeactivate(id) {
    if (!window.confirm("Deactivate this user? They will not be able to log in.")) return;
    try { await deactivateUser(id); load(); }
    catch (err) { setError(err.response?.data?.message || "Could not deactivate this user."); }
  }

  if (loading) return <LoadingSpinner text="Loading users..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">User Management</h1>
      <ErrorBox message={error} />
      {users.length === 0 ? (
        <div className="mt-6"><EmptyState title="No users found" message="Registered users will appear here." /></div>
      ) : (
        <table className="mt-6 w-full rounded border bg-white text-sm">
          <thead><tr className="border-b bg-gray-50 text-left"><th className="p-3">Name</th><th className="p-3">Email</th><th className="p-3">Role</th><th className="p-3">Status</th><th className="p-3">Action</th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-b">
                <td className="p-3">{u.fullName}</td>
                <td className="p-3">{u.email}</td>
                <td className="p-3">{u.role}</td>
                <td className="p-3">{u.enabled === false ? "Deactivated" : "Active"}</td>
                <td className="p-3">
                  {u.enabled !== false && u.role !== "ADMIN" && (
                    <button onClick={() => handleDeactivate(u.id)} className="rounded border border-red-300 px-3 py-1 text-red-700">Deactivate</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
