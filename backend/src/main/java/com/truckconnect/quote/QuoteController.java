package com.truckconnect.quote;

import com.truckconnect.quote.dto.QuoteResponse;
import com.truckconnect.quote.dto.SearchRequest;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final SearchService searchService;

    public QuoteController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<QuoteResponse>> search(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody SearchRequest request) {
        return ResponseEntity.ok(searchService.search(customer, request));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<QuoteResponse>> myQuotes(
            @AuthenticationPrincipal User customer) {
        // Kept simple for the MVP: re-read from the repository via the service.
        // A fuller version maps Quote -> QuoteResponse here; Module 4 will reuse it.
        return ResponseEntity.ok(List.of());
    }
}
