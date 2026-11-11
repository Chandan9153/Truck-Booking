import { useEffect, useState } from "react";
import { fetchMyProfile } from "../../services/authService";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";

export default function Profile() {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchMyProfile()
      .then(setProfile)
      .catch((err) => setError(err.response?.data?.message || "Could not load your profile."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingSpinner text="Loading your profile..." />;
  if (!profile) return <ErrorBox message={error} />;

  return (
    <div className="mx-auto max-w-xl rounded border bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold">My Profile</h1>
      <div className="mt-6 space-y-3 text-sm">
        <div className="flex justify-between border-b pb-2"><span className="text-gray-600">Full name</span><span className="font-medium">{profile.fullName}</span></div>
        <div className="flex justify-between border-b pb-2"><span className="text-gray-600">Email</span><span className="font-medium">{profile.email}</span></div>
        <div className="flex justify-between border-b pb-2"><span className="text-gray-600">Phone</span><span className="font-medium">{profile.phone}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Role</span><span className="font-medium">{profile.role}</span></div>
      </div>
      <p className="mt-4 text-xs text-gray-500">To change your name, phone or password, use a future profile-edit step — this page is read-only in Module 7.</p>
    </div>
  );
}
