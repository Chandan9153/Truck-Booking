import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const MENUS = {
  CUSTOMER: [
    { to: "/", label: "Search Trucks" },
    { to: "/my-bookings", label: "My Bookings" },
    { to: "/profile", label: "Profile" },
  ],
  OWNER: [
    { to: "/owner", label: "Dashboard" },
    { to: "/owner/trucks", label: "My Trucks" },
    { to: "/owner/bookings", label: "Incoming Bookings" },
    { to: "/owner/drivers", label: "My Drivers" },
    { to: "/owner/trips", label: "My Trips" },
    { to: "/owner/earnings", label: "Earnings" },
  ],
  TRANSPORTER: [
    { to: "/owner", label: "Dashboard" },
    { to: "/owner/trucks", label: "My Trucks" },
    { to: "/owner/bookings", label: "Incoming Bookings" },
    { to: "/owner/drivers", label: "My Drivers" },
    { to: "/owner/trips", label: "My Trips" },
    { to: "/owner/earnings", label: "Earnings" },
  ],
  DRIVER: [
    { to: "/driver", label: "Dashboard" },
    { to: "/driver/trips", label: "My Trips" },
  ],
  ADMIN: [
    { to: "/admin", label: "Dashboard" },
    { to: "/admin/users", label: "Users" },
    { to: "/admin/categories", label: "Categories" },
    { to: "/admin/rates", label: "Rates" },
    { to: "/admin/bookings", label: "Bookings" },
    { to: "/admin/settlements", label: "Settlements" },
    { to: "/admin/disputes", label: "Disputes" },
  ],
};

export default function Navbar() {
  const { user, isLoggedIn, logout } = useAuth();
  const navigate = useNavigate();
  const links = user?.role ? MENUS[user.role] || [] : [];

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <header className="bg-blue-700 text-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-3">
        <Link to="/" className="text-xl font-bold">Truck Booking</Link>
        <nav className="flex flex-wrap items-center gap-4 text-sm">
          {isLoggedIn ? (
            <>
              {links.map((l) => (
                <Link key={l.to} to={l.to} className="hover:underline">{l.label}</Link>
              ))}
              <span className="text-blue-200">
                {user.fullName} ({user.role})
              </span>
              <button onClick={handleLogout} className="rounded bg-blue-800 px-3 py-1 hover:bg-blue-900">
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="hover:underline">Login</Link>
              <Link to="/register" className="rounded bg-white px-3 py-1 font-medium text-blue-700">
                Register
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
