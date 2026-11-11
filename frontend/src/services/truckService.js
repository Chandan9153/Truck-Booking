import api from "./api";

// ⚠️ ASSUMED endpoints — check against Module 2 and fix the URL here only.
export async function fetchMyTrucks() {
  const res = await api.get("/trucks/my");
  return res.data;
}

// Slim registration (Module 2 correction): only these 4 fields. No documents.
export async function addTruck({ registrationNumber, categoryId, bodyType, payloadTons }) {
  const res = await api.post("/trucks", {
    registrationNumber,
    categoryId: Number(categoryId),
    bodyType,
    payloadTons: Number(payloadTons),
  });
  return res.data;
}

// Toggle AVAILABLE / UNAVAILABLE. A truck ON_TRIP cannot be toggled by hand —
// the backend moves it to ON_TRIP when a booking is accepted (Module 4).
export async function setTruckAvailability(truckId, availabilityStatus) {
  const res = await api.patch(`/trucks/${truckId}/availability`, { availabilityStatus });
  return res.data;
}

// Categories for the search form and the add-truck form (12/20t, 14/25t, 18/30t, 22/36t).
export async function fetchCategories() {
  const res = await api.get("/trucks/categories");
  return res.data;
}
