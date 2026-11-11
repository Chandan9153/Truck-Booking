import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 4 and fix the URL here only.

// POST /api/bookings { quoteId } — instant booking. The price is read from
// the saved quote on the server; the frontend sends NO price.
export async function createBooking(quoteId) {
  const res = await api.post("/bookings", { quoteId });
  return res.data;
}

// GET /api/bookings/my — bookings for whoever is logged in (customer sees
// their bookings, owner sees bookings for their trucks).
export async function fetchMyBookings() {
  const res = await api.get("/bookings/my");
  return res.data;
}

export async function fetchBookingById(bookingId) {
  const res = await api.get(`/bookings/${bookingId}`);
  return res.data;
}

// Owner accept / reject (Module 4). Rejecting should free the truck again.
export async function acceptBooking(bookingId) {
  const res = await api.post(`/bookings/${bookingId}/accept`);
  return res.data;
}

export async function rejectBooking(bookingId, reason) {
  const res = await api.post(`/bookings/${bookingId}/reject`, { reason: reason || null });
  return res.data;
}

// Customer cancel — the backend enforces the cancel rules (Module 4);
// the frontend only shows the button when the status allows it.
export async function cancelBooking(bookingId) {
  const res = await api.post(`/bookings/${bookingId}/cancel`);
  return res.data;
}

// --- Owner call / chat (Module 4) ---
// Chat: in-app messages on the booking. Call: NOT an API — the Tracking page
// renders a <a href="tel:..."> link with the owner's phone number, which the
// backend returns on the booking. No call API exists, and none is needed.
export async function fetchMessages(bookingId) {
  const res = await api.get(`/bookings/${bookingId}/messages`);
  return res.data;
}

export async function sendMessage(bookingId, text) {
  const res = await api.post(`/bookings/${bookingId}/messages`, { text });
  return res.data;
}
