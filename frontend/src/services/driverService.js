import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 5 and fix the URL here only.
export async function fetchMyDrivers() {
  const res = await api.get("/drivers/my");
  return res.data;
}

export async function addDriver({ fullName, phone, licenceNumber }) {
  const res = await api.post("/drivers", { fullName, phone, licenceNumber });
  return res.data;
}

export async function updateDriver(driverId, { fullName, phone, licenceNumber }) {
  const res = await api.put(`/drivers/${driverId}`, { fullName, phone, licenceNumber });
  return res.data;
}

export async function deactivateDriver(driverId) {
  const res = await api.patch(`/drivers/${driverId}/deactivate`);
  return res.data;
}
