import api from "./api";

// ⚠️ ASSUMED endpoints (except rates, which are Module 3 — confirmed).
// Check against Modules 2/4/6 and fix the URL here only.
export async function fetchAdminStats() {
  const res = await api.get("/admin/stats");
  return res.data;
}

export async function fetchAllUsers() {
  const res = await api.get("/admin/users");
  return res.data;
}

export async function deactivateUser(userId) {
  const res = await api.patch(`/admin/users/${userId}/deactivate`);
  return res.data;
}

export async function fetchAdminCategories() {
  const res = await api.get("/admin/categories");
  return res.data;
}

export async function addCategory({ name, tyreCount, targetPayloadTons }) {
  const res = await api.post("/admin/categories", {
    name,
    tyreCount: Number(tyreCount),
    targetPayloadTons: Number(targetPayloadTons),
  });
  return res.data;
}

// Rate list — Module 3, confirmed (GET /api/admin/rates returns
// id, categoryName, tyreCount, targetPayloadTons, ratePerTonKm, active).
// This was missing: RateManagement.jsx imports it, so the build failed.
export async function fetchRates() {
  const res = await api.get("/admin/rates");
  return res.data;
}

// Rate edit — Module 3, confirmed. Changing a rate affects NEW quotes only;
// old quotes keep the rate they were calculated with.
export async function updateRate(rateId, { ratePerTonKm, active }) {
  const res = await api.put(`/admin/rates/${rateId}`, { ratePerTonKm, active });
  return res.data;
}

export async function fetchAllBookings() {
  const res = await api.get("/admin/bookings");
  return res.data;
}

export async function fetchAllSettlements() {
  const res = await api.get("/admin/settlements");
  return res.data;
}

export async function markSettlementPaid(settlementId) {
  const res = await api.post(`/admin/settlements/${settlementId}/mark-paid`);
  return res.data;
}
