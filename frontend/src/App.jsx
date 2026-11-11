import { Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import ProtectedRoute from "./routes/ProtectedRoute";
import Navbar from "./components/Navbar";
import Footer from "./components/Footer";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Home from "./pages/customer/Home";
import SearchResults from "./pages/customer/SearchResults";
import BookingConfirm from "./pages/customer/BookingConfirm";
import MyBookings from "./pages/customer/MyBookings";
import Tracking from "./pages/customer/Tracking";
import Profile from "./pages/customer/Profile";
import OwnerDashboard from "./pages/owner/OwnerDashboard";
import MyTrucks from "./pages/owner/MyTrucks";
import IncomingBookings from "./pages/owner/IncomingBookings";
import MyDrivers from "./pages/owner/MyDrivers";
import MyTrips from "./pages/owner/MyTrips";
import Earnings from "./pages/owner/Earnings";
import DriverDashboard from "./pages/driver/DriverDashboard";
import DriverTrips from "./pages/driver/DriverTrips";
import AdminDashboard from "./pages/admin/AdminDashboard";
import UserManagement from "./pages/admin/UserManagement";
import CategoryManagement from "./pages/admin/CategoryManagement";
import RateManagement from "./pages/admin/RateManagement";
import BookingManagement from "./pages/admin/BookingManagement";
import SettlementManagement from "./pages/admin/SettlementManagement";
import Disputes from "./pages/admin/Disputes";

// After login, send each role to its own home page.
function HomeByRole() {
  const { role } = useAuth();
  if (role === "OWNER" || role === "TRANSPORTER") return <Navigate to="/owner" replace />;
  if (role === "ADMIN") return <Navigate to="/admin" replace />;
  if (role === "DRIVER") return <Navigate to="/driver" replace />;
  return <Home />; // CUSTOMER (and fallback) sees the search page
}

export default function App() {
  return (
    <AuthProvider>
      <div className="flex min-h-screen flex-col">
        <Navbar />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/" element={<HomeByRole />} />

            {/* Customer */}
            <Route element={<ProtectedRoute allowedRoles={["CUSTOMER"]} />}>
              <Route path="/search-results" element={<SearchResults />} />
              <Route path="/book/:quoteId" element={<BookingConfirm />} />
              <Route path="/my-bookings" element={<MyBookings />} />
              <Route path="/track/:bookingId" element={<Tracking />} />
              <Route path="/profile" element={<Profile />} />
            </Route>

            {/* Owner + Transporter */}
            <Route element={<ProtectedRoute allowedRoles={["OWNER", "TRANSPORTER"]} />}>
              <Route path="/owner" element={<OwnerDashboard />} />
              <Route path="/owner/trucks" element={<MyTrucks />} />
              <Route path="/owner/bookings" element={<IncomingBookings />} />
              <Route path="/owner/drivers" element={<MyDrivers />} />
              <Route path="/owner/trips" element={<MyTrips />} />
              <Route path="/owner/earnings" element={<Earnings />} />
            </Route>

            {/* Driver pages: OWNER can also open them in this MVP (Part 9).
                DRIVER role is listed so driver login works the day Module 5 adds it. */}
            <Route element={<ProtectedRoute allowedRoles={["DRIVER", "OWNER"]} />}>
              <Route path="/driver" element={<DriverDashboard />} />
              <Route path="/driver/trips" element={<DriverTrips />} />
            </Route>

            {/* Admin */}
            <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
              <Route path="/admin" element={<AdminDashboard />} />
              <Route path="/admin/users" element={<UserManagement />} />
              <Route path="/admin/categories" element={<CategoryManagement />} />
              <Route path="/admin/rates" element={<RateManagement />} />
              <Route path="/admin/bookings" element={<BookingManagement />} />
              <Route path="/admin/settlements" element={<SettlementManagement />} />
              <Route path="/admin/disputes" element={<Disputes />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <Footer />
      </div>
    </AuthProvider>
  );
}
