package com.truckconnect.loadpost;

import com.truckconnect.booking.dto.BookingResponse;
import com.truckconnect.loadpost.dto.*;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/load-posts")
public class LoadPostController {

    private final LoadPostService loadPostService;

    public LoadPostController(LoadPostService loadPostService) {
        this.loadPostService = loadPostService;
    }

    /** Pin a notice on the board. CUSTOMER (person or business) or TRANSPORTER. */
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER')")
    public ResponseEntity<LoadPostResponse> create(
            @AuthenticationPrincipal User poster,
            @Valid @RequestBody CreateLoadPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loadPostService.createPost(poster, request));
    }

    /** The board: open posts, newest first. Any logged-in user. */
    @GetMapping("/open")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LoadPostResponse>> openPosts() {
        return ResponseEntity.ok(loadPostService.openPosts());
    }

    /** My own posts, newest first. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER')")
    public ResponseEntity<List<LoadPostResponse>> myPosts(
            @AuthenticationPrincipal User poster) {
        return ResponseEntity.ok(loadPostService.myPosts(poster));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoadPostResponse> getOne(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(loadPostService.getPost(user, id));
    }

    /** Matching trucks + instant prices for one post, cheapest first. */
    @GetMapping("/{id}/matches")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER', 'OWNER', 'ADMIN')")
    public ResponseEntity<LoadPostMatchResponse> matches(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(loadPostService.matches(user, id));
    }

    /** Book 1..N trucks from this post, instantly. Poster only. */
    @PostMapping("/{id}/book")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER')")
    public ResponseEntity<List<BookingResponse>> book(
            @AuthenticationPrincipal User poster,
            @PathVariable Long id,
            @Valid @RequestBody BookFromLoadPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loadPostService.bookFromPost(poster, id, request));
    }

    /** Take the notice down. Poster or admin. Bookings already made stay booked. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TRANSPORTER', 'ADMIN')")
    public ResponseEntity<LoadPostResponse> cancel(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(loadPostService.cancelPost(user, id));
    }
}
