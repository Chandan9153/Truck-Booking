import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// Usage in App.jsx:
//   <Route element={<ProtectedRoute allowedRoles={["CUSTOMER"]} />}>
//     <Route path="/my-bookings" element={<MyBookings />} />
//   </Route>
//
// allowedRoles omitted (or empty) means "any logged-in user".
export default function ProtectedRoute({ allowedRoles }) {
  const { isLoggedIn, role, user } = useAuth();
  const location = useLocation();

  // 1. Not logged in at all -> login page. Remember where they were going,
  //    so the Login page can send them back after a successful login.
  if (!isLoggedIn) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  // 2. Logged in, but this page is not for their role -> their own home page.
  if (allowedRoles && allowedRoles.length > 0 && !allowedRoles.includes(role)) {
    const homeByRole = {
      CUSTOMER: "/",
      OWNER: "/owner",
      TRANSPORTER: "/owner",
      DRIVER: "/driver",
      ADMIN: "/admin",
    };
    return <Navigate to={homeByRole[role] || "/"} replace />;
  }

  // 3. Allowed -> render the page inside this route.
  return <Outlet />;
}
