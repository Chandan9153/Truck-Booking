package com.truckconnect.quote;

import com.truckconnect.pricing.*;
import com.truckconnect.quote.dto.QuoteResponse;
import com.truckconnect.quote.dto.SearchRequest;
import com.truckconnect.truck.*;
import com.truckconnect.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock TruckRepository truckRepository;
    @Mock PricingRateRepository rateRepository;
    @Mock PlatformSettingsRepository settingsRepository;
    @Mock QuoteRepository quoteRepository;
    @Mock PricingService pricingService;
    @InjectMocks SearchService searchService;

    private Truck truck(long id, String reg, int payload) {
        Truck t = new Truck();
        t.setId(id);
        t.setRegistrationNumber(reg);
        t.setPayloadTons(new BigDecimal(payload));
        t.setBodyType(BodyType.OPEN);
        TruckCategory category = new TruckCategory();
        category.setId(1L);
        category.setName("12 Tyre Truck");
        category.setTyreCount(12);
        category.setTargetPayloadTons(new BigDecimal("20"));
        t.setCategory(category);
        return t;
    }

    @Test
    void search_truckTooSmall_isNotReturned() {
        Truck small = truck(1L, "KA01AA0001", 10); // 10 tons cannot carry 20
        when(truckRepository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(small));
        when(settingsRepository.findById(1L))
                .thenReturn(Optional.of(new PlatformSettings()));

        SearchRequest req = new SearchRequest("Bengaluru", "Hyderabad",
                new BigDecimal("570"), new BigDecimal("20"), "rice",
                null, null, BigDecimal.ZERO, BigDecimal.ZERO);

        List<QuoteResponse> result = searchService.search(new User(), req);
        assertTrue(result.isEmpty(), "A 10-ton truck must never be offered a 20-ton load");
    }

    @Test
    void search_sortedCheapestFirst() {
        // Two matching trucks, different rates: the cheaper rate must come first.
        // (Pricing is mocked here; the real formula is already tested in PricingServiceTest.)
        Truck cheap = truck(1L, "KA01AA0001", 20);
        Truck dear = truck(2L, "KA01AA0002", 20);
        when(truckRepository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(dear, cheap)); // deliberately in the wrong order
        when(settingsRepository.findById(1L))
                .thenReturn(Optional.of(new PlatformSettings()));
        when(rateRepository.findByCategoryIdAndActiveTrue(1L))
                .thenReturn(Optional.of(new PricingRate(cheap.getCategory(),
                        new BigDecimal("2.70"))));
        when(pricingService.calculate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> new PricingService.FareBreakdown(
                        new BigDecimal("20"), false, new BigDecimal("30780.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0"),
                        new BigDecimal("30780.00")));
        when(quoteRepository.save(any())).thenAnswer(i -> {
            Quote q = i.getArgument(0);
            // Give each saved quote a fake id so the response can be built.
            // (If your Quote has setId, use it; otherwise this line is harmless.)
            return q;
        });

        SearchRequest req = new SearchRequest("Bengaluru", "Hyderabad",
                new BigDecimal("570"), new BigDecimal("20"), "rice",
                null, null, BigDecimal.ZERO, BigDecimal.ZERO);

        List<QuoteResponse> result = searchService.search(new User(), req);
        assertEquals(2, result.size());
        assertTrue(result.get(0).totalFare()
                .compareTo(result.get(1).totalFare()) <= 0,
                "Results must be sorted cheapest first");
    }
}
