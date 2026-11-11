import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 5 and fix the URL here only.
export async function fetchMyTrips() {
  const res = await api.get("/trips/my");
  return res.data;
}

export async function assignDriver(tripId, driverId) {
  const res = await api.post(`/trips/${tripId}/assign`, { driverId });
  return res.data;
}

// status: ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, COMPLETED (Module 5).
// deliveryProofNote / deliveryProofUrl are sent only on DELIVERED.
export async function updateTripStatus(tripId, { status, deliveryProofNote, deliveryProofUrl }) {
  const res = await api.post(`/trips/${tripId}/status`, {
    status,
    deliveryProofNote: deliveryProofNote || null,
    deliveryProofUrl: deliveryProofUrl || null,
  });
  return res.data;
}
