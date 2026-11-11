// Colours: green = good / moving forward, amber = waiting, red = stopped,
// grey = finished or cancelled, purple = needs attention (payment / dispute).
const COLOURS = {
  PENDING: "bg-amber-100 text-amber-800",
  CONFIRMED: "bg-blue-100 text-blue-800",
  DRIVER_ASSIGNED: "bg-blue-100 text-blue-800",
  ASSIGNED: "bg-blue-100 text-blue-800",
  PICKED_UP: "bg-indigo-100 text-indigo-800",
  IN_TRANSIT: "bg-indigo-100 text-indigo-800",
  DELIVERED: "bg-green-100 text-green-800",
  COMPLETED: "bg-green-100 text-green-800",
  REJECTED: "bg-red-100 text-red-800",
  CANCELLED: "bg-gray-200 text-gray-700",
  PAYMENT_PENDING: "bg-purple-100 text-purple-800",
  PAYMENT_FAILED: "bg-red-100 text-red-800",
  DISPUTED: "bg-red-100 text-red-800",
  AVAILABLE: "bg-green-100 text-green-800",
  UNAVAILABLE: "bg-gray-200 text-gray-700",
  ON_TRIP: "bg-indigo-100 text-indigo-800",
  ACTIVE: "bg-green-100 text-green-800",
  INACTIVE: "bg-gray-200 text-gray-700",
};

const LABELS = {
  DRIVER_ASSIGNED: "Driver assigned",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  PAYMENT_PENDING: "Payment pending",
  PAYMENT_FAILED: "Payment failed",
  ON_TRIP: "On trip",
};

export default function StatusBadge({ status }) {
  if (!status) return null;
  const colour = COLOURS[status] || "bg-gray-100 text-gray-700";
  const label = LABELS[status] || status.replaceAll("_", " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  return (
    <span className={`inline-block rounded-full px-3 py-1 text-xs font-medium ${colour}`}>
      {label}
    </span>
  );
}
