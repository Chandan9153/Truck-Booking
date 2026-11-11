export default function LoadingSpinner({ text = "Loading..." }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-gray-600">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
      <p className="mt-3 text-sm">{text}</p>
    </div>
  );
}
