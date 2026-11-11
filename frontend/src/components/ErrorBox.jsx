export default function ErrorBox({ message, onRetry }) {
  if (!message) return null;
  return (
    <div className="mb-4 rounded border border-red-300 bg-red-50 p-4 text-red-800">
      <p className="font-medium">Something went wrong</p>
      <p className="text-sm">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="mt-2 text-sm font-medium underline">
          Try again
        </button>
      )}
    </div>
  );
}
