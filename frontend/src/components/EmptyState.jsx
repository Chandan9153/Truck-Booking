export default function EmptyState({ title, message, actionLabel, onAction }) {
  return (
    <div className="rounded border border-dashed border-gray-300 bg-gray-50 p-8 text-center">
      <p className="text-lg font-medium text-gray-800">{title}</p>
      <p className="mt-1 text-sm text-gray-600">{message}</p>
      {actionLabel && (
        <button
          onClick={onAction}
          className="mt-4 rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}
