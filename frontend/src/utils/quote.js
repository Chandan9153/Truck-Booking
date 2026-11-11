// A quote has a validUntil timestamp from the backend (Module 3).
// Returns true when the quote can no longer be booked.
export function isQuoteExpired(quote) {
  if (!quote || !quote.validUntil) return true;
  return new Date(quote.validUntil).getTime() < Date.now();
}
