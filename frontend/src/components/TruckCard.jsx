import StatusBadge from "./StatusBadge";

export default function TruckCard({ truck }) {
  return (
    <div className="rounded border bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-lg font-semibold">{truck.registrationNumber}</p>
          <p className="text-sm text-gray-600">
            {truck.categoryName || truck.category?.name} · {truck.tyreCount || truck.category?.tyreCount} tyres
          </p>
        </div>
        <StatusBadge status={truck.availabilityStatus} />
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
        <p><span className="text-gray-500">Body: </span>{truck.bodyType}</p>
        <p><span className="text-gray-500">Can carry: </span>{truck.payloadTons} tons</p>
      </div>
    </div>
  );
}
