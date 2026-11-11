// Format a number as Indian rupees: 33280 -> "₹33,280"
export function formatRupees(amount) {
  if (amount === null || amount === undefined || amount === "") return "—";
  return "₹" + Number(amount).toLocaleString("en-IN", {
    maximumFractionDigits: 2,
  });
}

// Format an ISO date string: "2026-11-12T10:30:00" -> "12 Nov 2026, 10:30 AM"
export function formatDateTime(isoString) {
  if (!isoString) return "—";
  return new Date(isoString).toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

// Short date only, for table rows
export function formatDate(isoString) {
  if (!isoString) return "—";
  return new Date(isoString).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}
