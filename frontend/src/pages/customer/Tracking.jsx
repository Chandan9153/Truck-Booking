import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { fetchBookingById, fetchMessages, sendMessage } from "../../services/bookingService";
import { fetchPaymentForBooking } from "../../services/paymentService";
import StatusBadge from "../../components/StatusBadge";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import { formatRupees, formatDateTime } from "../../utils/format";
import { useAuth } from "../../context/AuthContext";

// The trip timeline, in order. The backend sends the current status;
// every step before it is shown as done, every step after it as waiting.
// ⚠️ ASSUMED: the booking response carries `status` and (for trips) a
//    `tripEvents` / `statusHistory` list. If Module 5 names it differently,
//    change the mapping in the timeline section below, not the layout.
const TIMELINE = ["CONFIRMED", "DRIVER_ASSIGNED", "PICKED_UP", "IN_TRANSIT", "DELIVERED", "COMPLETED"];
const TIMELINE_LABELS = {
  CONFIRMED: "Booking confirmed",
  DRIVER_ASSIGNED: "Driver assigned",
  PICKED_UP: "Goods picked up",
  IN_TRANSIT: "On the road",
  DELIVERED: "Delivered",
  COMPLETED: "Trip completed",
};

export default function Tracking() {
  const { bookingId } = useParams();
  const { user } = useAuth();
  const [booking, setBooking] = useState(null);
  const [messages, setMessages] = useState([]);
  const [payment, setPayment] = useState(null);
  const [newMessage, setNewMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        const [b, m] = await Promise.all([
          fetchBookingById(bookingId),
          fetchMessages(bookingId),
        ]);
        setBooking(b);
        setMessages(m || []);
        // Payment status is a nice-to-have here — if Module 6 is not ready
        // yet, the page still works without it.
        fetchPaymentForBooking(bookingId).then(setPayment).catch(() => setPayment(null));
      } catch (err) {
        setError(err.response?.data?.message || "Could not load this booking.");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [bookingId]);

  async function handleSend(e) {
    e.preventDefault();
    const text = newMessage.trim();
    if (!text) return;
    setSending(true);
    try {
      const saved = await sendMessage(bookingId, text);
      setMessages((list) => [...list, saved]);
      setNewMessage("");
    } catch (err) {
      setError(err.response?.data?.message || "Message could not be sent.");
    } finally {
      setSending(false);
    }
  }

  if (loading) return <LoadingSpinner text="Loading tracking..." />;
  if (!booking) return <ErrorBox message={error || "Booking not found."} />;

  const currentIndex = TIMELINE.indexOf(booking.status);
  const ownerPhone = booking.ownerPhone || booking.owner?.phone;
  const ownerName = booking.ownerName || booking.owner?.fullName || "Owner";

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold">Booking #{booking.id}</h1>
          <p className="text-gray-600">{booking.pickup} → {booking.destination}</p>
          <p className="text-sm text-gray-500">
            {booking.truck?.registrationNumber || booking.registrationNumber || ""} · {formatRupees(booking.totalFare ?? booking.totalAmount)}
            {payment ? ` · Payment: ${payment.method || "COD"} — ${payment.status}` : ""}
          </p>
        </div>
        <StatusBadge status={booking.status} />
      </div>
      <ErrorBox message={error} />

      {/* --- Timeline --- */}
      <div className="mt-6 rounded border bg-white p-5">
        <h2 className="font-semibold">Trip progress</h2>
        <ol className="mt-4 space-y-3">
          {TIMELINE.map((step, i) => {
            const done = currentIndex >= 0 && i <= currentIndex;
            const isCurrent = i === currentIndex;
            return (
              <li key={step} className="flex items-center gap-3">
                <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${done ? "bg-green-600 text-white" : "bg-gray-200 text-gray-600"}`}>
                  {done ? "✓" : i + 1}
                </span>
                <span className={isCurrent ? "font-semibold" : done ? "" : "text-gray-400"}>
                  {TIMELINE_LABELS[step]}{isCurrent ? " — current step" : ""}
                </span>
              </li>
            );
          })}
          {["REJECTED", "CANCELLED"].includes(booking.status) && (
            <li className="text-red-700">This booking was {booking.status.toLowerCase()}.</li>
          )}
        </ol>
        {booking.deliveryProofNote && (
          <p className="mt-4 rounded bg-green-50 p-3 text-sm text-green-800">
            Delivery proof: {booking.deliveryProofNote}
            {booking.deliveryProofUrl && (<> — <a href={booking.deliveryProofUrl} target="_blank" rel="noreferrer" className="underline">View proof</a></>)}
          </p>
        )}
      </div>

      {/* --- Call + Chat with the owner --- */}
      <div className="mt-6 grid gap-6 md:grid-cols-2">
        <div className="rounded border bg-white p-5">
          <h2 className="font-semibold">Contact the owner</h2>
          <p className="mt-1 text-sm text-gray-600">{ownerName}</p>
          {ownerPhone ? (
            // A tel: link is a real phone call on a mobile — no API, no cost,
            // and it works even if the chat backend is down.
            <a href={`tel:${ownerPhone}`} className="mt-4 inline-block rounded bg-green-600 px-4 py-2 font-medium text-white hover:bg-green-700">
              📞 Call Owner — {ownerPhone}
            </a>
          ) : (
            <p className="mt-4 text-sm text-gray-500">The owner's phone number will appear here once the booking is confirmed.</p>
          )}
        </div>

        <div className="rounded border bg-white p-5">
          <h2 className="font-semibold">Chat with the owner</h2>
          <div className="mt-3 max-h-64 space-y-2 overflow-y-auto rounded border bg-gray-50 p-3">
            {messages.length === 0 ? (
              <p className="text-sm text-gray-500">No messages yet. Say hello to the owner here.</p>
            ) : (
              messages.map((m) => (
                <div key={m.id} className={`max-w-[85%] rounded p-2 text-sm ${m.senderId === user?.userId || m.sender?.id === user?.userId ? "ml-auto bg-blue-100" : "bg-white border"}`}>
                  <p>{m.text || m.message}</p>
                  <p className="mt-1 text-xs text-gray-500">{formatDateTime(m.createdAt)}</p>
                </div>
              ))
            )}
          </div>
          <form onSubmit={handleSend} className="mt-3 flex gap-2">
            <input value={newMessage} onChange={(e) => setNewMessage(e.target.value)} className="flex-1 rounded border px-3 py-2" placeholder="Type a message..." />
            <button disabled={sending} className="rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-300">
              {sending ? "..." : "Send"}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
