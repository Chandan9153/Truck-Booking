import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 6 and fix the URL here only.
// COD is the default in Module 6; there is no online-payment screen here.
export async function fetchPaymentForBooking(bookingId) {
  const res = await api.get(`/payments/booking/${bookingId}`);
  return res.data; // { bookingId, method: "COD", status, amount, ... }
}

// Owner settlement view: what the platform owes the owner, and what is paid.
export async function fetchMySettlements() {
  const res = await api.get("/payments/settlements/my");
  return res.data;
}
