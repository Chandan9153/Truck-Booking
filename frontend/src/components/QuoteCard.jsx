import { formatRupees, formatDateTime } from "../utils/format";
import { isQuoteExpired } from "../utils/quote";
import TruckCard from "./TruckCard";

export default function QuoteCard({ quote, onBook }) {
  const expired = isQuoteExpired(quote);

  return (
    <div className="rounded border bg-white p-5 shadow-sm">
      <TruckCard truck={quote} />

      {/* --- Fare breakdown: every rupee explained (Module 3) --- */}
      <div className="mt-4 space-y-1 border-t pt-3 text-sm">
        <div className="flex justify-between">
          <span className="text-gray-600">
            Base fare ({quote.distanceKm} km × {formatRupees(quote.ratePerTonKm)}/ton/km × {quote.chargeableTons} tons)
          </span>
          <span>{formatRupees(quote.baseFare)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-600">Toll charges</span>
          <span>{formatRupees(quote.tollCharges)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-600">Loading charges</span>
          <span>{formatRupees(quote.loadingCharges)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-600">Platform fee</span>
          <span>{formatRupees(quote.platformFee)}</span>
        </div>
        <div className="flex justify-between border-t pt-2 text-base font-semibold">
          <span>Total fare</span>
          <span>{formatRupees(quote.totalFare)}</span>
        </div>
      </div>

      {/* --- Minimum-charge note: shown ONLY when it applied --- */}
      {quote.minimumChargeApplied && (
        <p className="mt-3 rounded bg-amber-50 p-3 text-sm text-amber-800">
          Minimum charge applied: your load is smaller than half this truck's
          capacity, so you are charged for {quote.chargeableTons} tons (half the
          truck) instead of your actual load. A full truck load never pays this.
        </p>
      )}

      <p className="mt-3 text-xs text-gray-500">
        Quote valid until {formatDateTime(quote.validUntil)}
      </p>

      <button
        onClick={() => onBook(quote)}
        disabled={expired}
        className="mt-4 w-full rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300"
      >
        {expired ? "Quote expired — search again" : `Book now — ${formatRupees(quote.totalFare)}`}
      </button>
    </div>
  );
}
