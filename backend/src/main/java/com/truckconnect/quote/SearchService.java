package com.truckconnect.quote;

import com.truckconnect.pricing.*;
import com.truckconnect.quote.dto.QuoteResponse;
import com.truckconnect.quote.dto.SearchRequest;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.Truck;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class SearchService {

    private final TruckRepository truckRepository;
    private final PricingRateRepository rateRepository;
    private final PlatformSettingsRepository settingsRepository;
    private final QuoteRepository quoteRepository;
    private final PricingService pricingService;

    public SearchService(TruckRepository truckRepository,
                         PricingRateRepository rateRepository,
                         PlatformSettingsRepository settingsRepository,
                         QuoteRepository quoteRepository,
                         PricingService pricingService) {
        this.truckRepository = truckRepository;
        this.rateRepository = rateRepository;
        this.settingsRepository = settingsRepository;
        this.quoteRepository = quoteRepository;
        this.pricingService = pricingService;
    }

    @Transactional
    public List<QuoteResponse> search(User customer, SearchRequest req) {
        PlatformSettings settings = settingsRepository.findById(1L)
                .orElseGet(() -> settingsRepository.save(new PlatformSettings()));
        BigDecimal toll = req.tollCharges() == null ? BigDecimal.ZERO : req.tollCharges();
        BigDecimal loading = req.loadingCharges() == null ? BigDecimal.ZERO : req.loadingCharges();

        List<Truck> candidates = truckRepository
                .findByAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        return candidates.stream()
                // Rule 1: can it carry the load? (truck's own payload, never tyre count)
                .filter(t -> t.getPayloadTons().compareTo(req.loadTons()) >= 0)
                // Rule 2: right body, if the customer asked for one
                .filter(t -> req.bodyType() == null || t.getBodyType() == req.bodyType())
                // Rule 3: right category, if the customer picked one
                .filter(t -> req.categoryId() == null
                        || t.getCategory().getId().equals(req.categoryId()))
                // Price it — skip trucks whose category has no live rate
                .flatMap(t -> rateRepository
                        .findByCategoryIdAndActiveTrue(t.getCategory().getId())
                        .stream().map(rate -> buildQuote(customer, req, t,
                                rate.getRatePerTonKm(), toll, loading, settings)))
                .sorted(Comparator
                        .comparing(QuoteResponse::totalFare)
                        .thenComparing(q -> q.payloadTons()
                                .subtract(req.loadTons()))) // closest fit first on a tie
                .toList();
    }

    private QuoteResponse buildQuote(User customer, SearchRequest req, Truck truck,
                                     BigDecimal rate, BigDecimal toll, BigDecimal loading,
                                     PlatformSettings settings) {
        PricingService.FareBreakdown fare = pricingService.calculate(
                req.distanceKm(), req.loadTons(), truck.getPayloadTons(),
                rate, toll, loading,
                settings.getPlatformFee(), settings.getMinimumLoadFraction());

        Quote quote = new Quote();
        quote.setCustomer(customer);
        quote.setTruck(truck);
        quote.setPickup(req.pickup());
        quote.setDestination(req.destination());
        quote.setDistanceKm(req.distanceKm());
        quote.setLoadTons(req.loadTons());
        quote.setGoodsType(req.goodsType());
        quote.setRatePerTonKm(rate);
        quote.setChargeableTons(fare.chargeableTons());
        quote.setMinimumChargeApplied(fare.minimumChargeApplied());
        quote.setBaseFare(fare.baseFare());
        quote.setTollCharges(toll);
        quote.setLoadingCharges(loading);
        quote.setPlatformFee(fare.platformFee());
        quote.setTotalFare(fare.totalFare());
        quote.setValidUntil(LocalDateTime.now().plusHours(24));
        quote = quoteRepository.save(quote);

        return new QuoteResponse(
                quote.getId(), truck.getId(), truck.getRegistrationNumber(),
                truck.getCategory().getName(), truck.getCategory().getTyreCount(),
                truck.getPayloadTons(), truck.getBodyType().name(),
                rate, fare.chargeableTons(), fare.minimumChargeApplied(),
                fare.baseFare(), toll, loading, fare.platformFee(),
                fare.totalFare(), quote.getValidUntil());
    }
}
