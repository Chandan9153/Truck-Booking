import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { createBooking } from "../../services/bookingService";
import { isQuoteExpired } from "../../utils/quote";
import { formatRupees, formatDateTime } from "../../utils/format";
import ErrorBox from "../../components/ErrorBox";

export default function BookingConfirm() {
  const { quoteId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const quote = location.state?.quote;
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // Refreshed or opened directly: the quote is not in router state.
  // Do NOT rebuild the quote from URL parameters — that would let anyone
  // type any price into the address bar. Send them back to search.
  if (!quote) {
    return (
      <div className="rounded border bg-white p-6">
        <h1 className="text-xl font-bold">Quote not found</h1>
        <p className="mt-2 text-gray-600">Please search again — a booking always starts from a fresh quote.</p>
        <button onClick={() => navigate("/")} className="mt-4 rounded bg-blue-600 px-4 py-2 text-white">
          Back to search
        </button>
      </div>
    );
  }

  const expired = isQuoteExpired(quote);

  async function handleBook() {
    if (expired) return;
    setError("");
    setLoading(true);
    try {
      // ONLY the quoteId is sent. The backend reads the price from the saved
      // quote row (Module 4) — the frontend never sends a fare.
      const booking = await createBooking(quote.quoteId || quoteId);
      navigate(`/track/${booking.id}`, { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || "Booking failed. The quote may have expired or the truck may already be booked.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl rounded border bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold">Confirm your booking</h1>
      <p className="mt-1 text-sm text-gray-600">Check the details. The price below is the quote price — it will not change at this step.</p>
      <ErrorBox message={error} />

      <div className="mt-6 space-y-2 text-sm">
        <div className="flex justify-between"><span className="text-gray-600">Truck</span><span className="font-medium">{quote.registrationNumber} · {quote.categoryName}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Route</span><span className="font-medium">{quote.pickup || "—"} → {quote.destination || "—"}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Load</span><span>{quote.loadTons} tons ({quote.chargeableTons} tons charged)</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Base fare</span><span>{formatRupees(quote.baseFare)}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Toll + loading</span><span>{formatRupees((quote.tollCharges || 0) + (quote.loadingCharges || 0))}</span></div>
        <div className="flex justify-between"><span className="text-gray-600">Platform fee</span><span>{formatRupees(quote.platformFee)}</span></div>
        <div className="flex justify-between border-t pt-2 text-base font-semibold"><span>Total fare</span><span>{formatRupees(quote.totalFare)}</span></div>
        <p className="text-xs text-gray-500">Payment: Cash on Delivery (COD) by default. Valid until {formatDateTime(quote.validUntil)}.</p>
        {quote.minimumChargeApplied && (
          <p className="rounded bg-amber-50 p-3 text-sm text-amber-800">
            Minimum charge applied — you are charged for {quote.chargeableTons} tons (half the truck) instead of your actual load.
          </p>
        )}
      </div>

      <button
        onClick={handleBook}
        disabled={loading || expired}
        className="mt-6 w-full rounded bg-blue-600 py-3 font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
      >
        {expired ? "This quote has expired — search again" : loading ? "Booking..." : `Confirm booking — ${formatRupees(quote.totalFare)}`}
      </button>
      <button onClick={() => navigate("/search-results", { state: { search: null } })} className="mt-2 w-full rounded border py-2 text-sm">
        Back to results
      </button>
    </div>
  );
}
