import api from "./api";

// POST /api/quotes/search — Module 3. Returns quotes cheapest first,
// each with the full fare breakdown (see QuoteCard, Part 7).
export async function searchQuotes(search) {
  const res = await api.post("/quotes/search", {
    pickup: search.pickup,
    destination: search.destination,
    distanceKm: Number(search.distanceKm),
    loadTons: Number(search.loadTons),
    goodsType: search.goodsType || null,
    bodyType: search.bodyType || null,
    categoryId: search.categoryId ? Number(search.categoryId) : null,
    tollCharges: search.tollCharges ? Number(search.tollCharges) : 0,
    loadingCharges: search.loadingCharges ? Number(search.loadingCharges) : 0,
  });
  return res.data; // QuoteResponse[]
}

// GET /api/admin/rates — Module 3. Admin only.
export async function fetchRates() {
  const res = await api.get("/admin/rates");
  return res.data;
}
