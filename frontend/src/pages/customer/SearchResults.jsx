import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { searchQuotes } from "../../services/quoteService";
import QuoteCard from "../../components/QuoteCard";
import LoadingSpinner from "../../components/LoadingSpinner";
import ErrorBox from "../../components/ErrorBox";
import EmptyState from "../../components/EmptyState";

export default function SearchResults() {
  const location = useLocation();
  const navigate = useNavigate();
  const search = location.state?.search;
  const [quotes, setQuotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!search) {
      // Opened directly without a search — send them to the form.
      navigate("/", { replace: true });
      return;
    }
    setLoading(true);
    searchQuotes(search)
      .then((data) => setQuotes(data || []))
      .catch((err) => setError(err.response?.data?.message || "Search failed. Please try again."))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // No search in state (e.g. page refreshed): the router state is gone.
  // The user searches again — quotes are not stored in localStorage, because
  // a quote is server data with a 24-hour expiry, not a page setting.
  if (!search) return null;

  if (loading) return <LoadingSpinner text="Finding trucks that can carry your load..." />;

  return (
    <div>
      <h1 className="text-2xl font-bold">Available trucks</h1>
      <p className="mt-1 text-gray-600">
        {search.pickup} → {search.destination} · {search.distanceKm} km · {search.loadTons} tons
        {search.goodsType ? ` · ${search.goodsType}` : ""}
      </p>
      <p className="mt-1 text-sm text-gray-500">Cheapest first. Prices include toll, loading and the ₹0 platform fee.</p>

      <ErrorBox message={error} onRetry={() => window.location.reload()} />

      {!error && quotes.length === 0 && (
        <div className="mt-6">
          <EmptyState
            title={`No truck can carry ${search.loadTons} tons`}
            message="No available truck matches this load right now. Try a smaller load, a different body type, or another date."
            actionLabel="Change search"
            onAction={() => navigate("/")}
          />
        </div>
      )}

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {quotes.map((q) => (
          <QuoteCard
            key={q.quoteId}
            quote={q}
            onBook={(quote) => navigate(`/book/${quote.quoteId}`, { state: { quote } })}
          />
        ))}
      </div>
    </div>
  );
}
