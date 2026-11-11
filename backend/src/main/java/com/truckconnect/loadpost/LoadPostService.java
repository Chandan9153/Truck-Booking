package com.truckconnect.loadpost;

import com.truckconnect.booking.*;
import com.truckconnect.booking.dto.BookingResponse;
import com.truckconnect.loadpost.dto.*;
import com.truckconnect.quote.Quote;
import com.truckconnect.quote.QuoteRepository;
import com.truckconnect.quote.SearchService;
import com.truckconnect.quote.dto.QuoteResponse;
import com.truckconnect.quote.dto.SearchRequest;
import com.truckconnect.truck.TruckCategory;
import com.truckconnect.truck.TruckCategoryRepository;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class LoadPostService {

    private final LoadPostRepository postRepository;
    private final BookingRepository bookingRepository;
    private final QuoteRepository quoteRepository;
    private final TruckCategoryRepository categoryRepository;
    private final TruckRepository truckRepository;
    private final SearchService searchService;
    private final BookingService bookingService;

    public LoadPostService(LoadPostRepository postRepository,
                           BookingRepository bookingRepository,
                           QuoteRepository quoteRepository,
                           TruckCategoryRepository categoryRepository,
                           TruckRepository truckRepository,
                           SearchService searchService,
                           BookingService bookingService) {
        this.postRepository = postRepository;
        this.bookingRepository = bookingRepository;
        this.quoteRepository = quoteRepository;
        this.categoryRepository = categoryRepository;
        this.truckRepository = truckRepository;
        this.searchService = searchService;
        this.bookingService = bookingService;
    }

    // ---------- Post a load ----------

    @Transactional
    public LoadPostResponse createPost(User poster, CreateLoadPostRequest req) {
        TruckCategory category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found: " + req.categoryId()));

        // Rule: total tons must divide evenly across the trucks needed.
        // 60 / 3 = 20.00 is fine; 50 / 3 = 16.67 is refused, not rounded.
        BigDecimal tonsPerTruck = req.totalTons().divide(
                BigDecimal.valueOf(req.trucksNeeded()), 2, RoundingMode.HALF_UP);
        BigDecimal check = tonsPerTruck.multiply(
                BigDecimal.valueOf(req.trucksNeeded()));
        if (check.compareTo(req.totalTons()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total tons (" + req.totalTons() + ") must divide evenly across "
                    + req.trucksNeeded() + " trucks. Try "
                    + suggestEvenSplit(req.totalTons(), req.trucksNeeded()));
        }
        // Rule: one truck's share must fit inside the category it asked for.
        // A 12-tyre category targets 20 tons; a 25-ton share cannot go in it.
        if (tonsPerTruck.compareTo(category.getTargetPayloadTons()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Each truck would carry " + tonsPerTruck + " tons, but the "
                    + category.getName() + " category targets "
                    + category.getTargetPayloadTons() + " tons. "
                    + "Pick a bigger category or more trucks.");
        }

        LoadPost post = new LoadPost();
        post.setPostedBy(poster);
        post.setGoodsDescription(req.goodsDescription().trim());
        post.setTotalTons(req.totalTons());
        post.setTrucksNeeded(req.trucksNeeded());
        post.setTonsPerTruck(tonsPerTruck);
        post.setCategory(category);
        post.setBodyType(req.bodyType());
        post.setPickup(req.pickup().trim());
        post.setDestination(req.destination().trim());
        post.setDistanceKm(req.distanceKm());
        post.setPickupDate(req.pickupDate());
        post.setTollCharges(req.tollCharges() == null
                ? BigDecimal.ZERO : req.tollCharges());
        post.setLoadingCharges(req.loadingCharges() == null
                ? BigDecimal.ZERO : req.loadingCharges());
        // No phone typed in the form? Use the poster's account phone.
        post.setContactPhone(req.contactPhone() == null
                ? poster.getPhone() : req.contactPhone());
        post.setStatus(LoadPostStatus.OPEN);
        post = postRepository.save(post);

        return LoadPostMapper.toResponse(post, 0);
    }

    /** Small helper: suggest the nearest even splits, e.g. "48 tons (16 each), 51 tons (17 each)". */
    private String suggestEvenSplit(BigDecimal total, int trucks) {
        BigDecimal perTruck = total.divide(
                BigDecimal.valueOf(trucks), 0, RoundingMode.DOWN);
        BigDecimal lower = perTruck.multiply(BigDecimal.valueOf(trucks));
        BigDecimal upper = perTruck.add(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(trucks));
        return lower + " tons (" + perTruck + " each) or "
                + upper + " tons (" + perTruck.add(BigDecimal.ONE) + " each)";
    }

    // ---------- Read: the board, my posts, one post ----------

    @Transactional
    public List<LoadPostResponse> openPosts() {
        expireOverduePosts();
        return postRepository.findByStatusInOrderByCreatedAtDesc(
                        List.of(LoadPostStatus.OPEN, LoadPostStatus.PARTIALLY_BOOKED))
                .stream().map(p -> LoadPostMapper.toResponse(p, countLiveBookings(p)))
                .toList();
    }

    @Transactional
    public List<LoadPostResponse> myPosts(User poster) {
        expireOverduePosts();
        return postRepository.findByPostedByIdOrderByCreatedAtDesc(poster.getId())
                .stream().map(p -> LoadPostMapper.toResponse(p, countLiveBookings(p)))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoadPostResponse getPost(User user, Long postId) {
        LoadPost post = loadPost(postId);
        return LoadPostMapper.toResponse(post, countLiveBookings(post));
    }

    // ---------- Match: Module 3 search, once, for one truck's share ----------

    /**
     * Runs the Module 3 search with load = tonsPerTruck, then tags each
     * returned quote with this post so bookFromPost can prove the quote
     * came from here. The price formula is Module 3's — nothing is
     * recalculated here.
     */
    @Transactional
    public LoadPostMatchResponse matches(User user, Long postId) {
        LoadPost post = loadPost(postId);
        if (!LoadPostStatus.isBookable(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post is " + post.getStatus()
                    + " — no more trucks can be booked from it");
        }
        expireOverduePosts();
        post = loadPost(postId); // re-read: expiry may have changed the status
        if (!LoadPostStatus.isBookable(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post is " + post.getStatus()
                    + " — the pickup date has passed");
        }

        SearchRequest search = new SearchRequest(
                post.getPickup(), post.getDestination(), post.getDistanceKm(),
                post.getTonsPerTruck(), post.getGoodsDescription(),
                post.getBodyType(), post.getCategory().getId(),
                post.getTollCharges(), post.getLoadingCharges());

        // The poster searches as themselves; an owner browsing matches
        // searches as themselves too — SearchService only needs a User
        // to stamp the quotes with. Any logged-in user may call this.
        List<QuoteResponse> quotes = searchService.search(user, search);

        // Tag the quotes with this post (Edit A) and build the match list.
        int remaining = post.getTrucksNeeded() - countLiveBookings(post);
        final LoadPost postForQuotes = post; // 'post' was re-read above, so it
        // is not effectively final and cannot be used inside the lambda below.
        List<LoadPostMatchResponse.Match> matchList = quotes.stream().map(q -> {
            Quote quote = quoteRepository.findById(q.quoteId()).orElseThrow();
            quote.setLoadPost(postForQuotes);
            quoteRepository.save(quote);
            return new LoadPostMatchResponse.Match(
                    q.truckId(), q.registrationNumber(), q.categoryName(),
                    q.tyreCount(), q.payloadTons(), q.bodyType(),
                    ownerNameOf(q.truckId()), q);
        }).toList();

        BigDecimal cheapest = matchList.stream()
                .map(m -> m.quote().totalFare())
                .min(BigDecimal::compareTo).orElse(null);
        BigDecimal estimatedTotal = cheapest == null ? null
                : cheapest.multiply(BigDecimal.valueOf(remaining));
        String note = cheapest == null
                ? "No available truck can carry " + post.getTonsPerTruck()
                        + " tons right now — try again later or change the category."
                : "Estimate only: " + remaining + " truck(s) still needed x cheapest "
                        + "per-truck fare " + cheapest
                        + ". The final total is the sum of the bookings actually created.";

        return new LoadPostMatchResponse(post.getId(), post.getTonsPerTruck(),
                post.getTrucksNeeded(), countLiveBookings(post), remaining,
                matchList, cheapest, estimatedTotal, note);
    }

    // ---------- Book: one Module 4 booking per truck, in one transaction ----------

    /**
     * Books 1..N trucks from a post. The whole method is ONE transaction:
     * the post row is locked first, the live count is checked, then each
     * quote is booked through BookingService.createBooking — the exact
     * Module 4 path, so the snapshot, the truck lock, and the
     * no-double-booking guard all apply unchanged. If any quote fails,
     * nothing is booked (the transaction rolls back).
     */
    @Transactional
    public List<BookingResponse> bookFromPost(User poster, Long postId,
                                              BookFromLoadPostRequest req) {
        LoadPost post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Load post not found: " + postId));
        requirePoster(poster, post);
        if (!LoadPostStatus.isBookable(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post is " + post.getStatus()
                    + " — no more trucks can be booked from it");
        }
        if (post.getPickupDate().isBefore(LocalDate.now())) {
            moveStatus(post, LoadPostStatus.EXPIRED);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post expired — the pickup date has passed");
        }

        // Over-booking guard: live bookings + this request must not exceed need.
        int live = countLiveBookings(post);
        int remaining = post.getTrucksNeeded() - live;
        if (req.quoteIds().size() > remaining) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This load needs " + post.getTrucksNeeded() + " trucks, "
                    + live + " are already booked — you can book at most "
                    + remaining + " more");
        }
        // No quote twice in one request — booking the same truck twice
        // would fail in Module 4 anyway, but fail fast with a clear message.
        if (req.quoteIds().stream().distinct().count() != req.quoteIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The same quote appears twice — each truck can be booked once");
        }

        List<BookingResponse> created = new java.util.ArrayList<>();
        for (Long quoteId : req.quoteIds()) {
            Quote quote = quoteRepository.findById(quoteId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Quote not found: " + quoteId));
            // The quote must come from THIS post's matches, belong to the
            // poster, and still be usable — Module 4 checks the last two,
            // this module adds the first.
            if (quote.getLoadPost() == null
                    || !quote.getLoadPost().getId().equals(post.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Quote " + quoteId + " was not created from load post "
                        + post.getId() + " — get fresh matches first");
            }
            if (!quote.getCustomer().getId().equals(poster.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "This quote does not belong to you");
            }
            // The poster may not book their own truck (Part 4, Rule 4).
            if (quote.getTruck().getOwner().getId().equals(poster.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You cannot book your own truck");
            }
            BookingResponse booking = bookingService.createBooking(poster,
                    new com.truckconnect.booking.dto.CreateBookingRequest(quoteId));
            // Link the new booking to this post (Edit B) so the live count
            // and the factory's "2 of 3 booked" view work.
            Booking saved = bookingRepository.findById(booking.bookingId())
                    .orElseThrow();
            saved.setLoadPost(post);
            bookingRepository.save(saved);
            created.add(booking);
        }

        // Move the post status from the fresh count, through the state machine.
        refreshPostStatus(post);
        return created;
    }

    // ---------- Cancel a post ----------

    @Transactional
    public LoadPostResponse cancelPost(User user, Long postId) {
        LoadPost post = loadPost(postId);
        if (user.getRole() != Role.ADMIN) {
            requirePoster(user, post);
        }
        if (post.getStatus() == LoadPostStatus.FULLY_BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This load is fully booked — cancel its bookings one by one "
                    + "through Module 4; the post itself has nothing left to cancel");
        }
        if (LoadPostStatus.isFinal(post.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Load post is already " + post.getStatus());
        }
        // Bookings already made stay booked — cancelling the post only stops
        // further bookings. Their owners have already committed.
        moveStatus(post, LoadPostStatus.CANCELLED);
        return LoadPostMapper.toResponse(post, countLiveBookings(post));
    }

    // ---------- Owner view: posts my trucks can carry ----------

    @Transactional(readOnly = true)
    public List<LoadPostResponse> matchingForOwner(User owner) {
        expireOverduePosts();
        // An owner sees an open post if ANY of their AVAILABLE trucks could
        // carry one share of it: same category, payload >= tonsPerTruck,
        // body matches if the post asked for one. The check runs in Java
        // over the owner's trucks — simple and fast enough for an MVP.
        List<com.truckconnect.truck.Truck> myTrucks =
                truckRepository.findByOwnerIdAndAvailabilityStatus(
                        owner.getId(),
                        com.truckconnect.truck.AvailabilityStatus.AVAILABLE);
        return postRepository.findByStatusInOrderByCreatedAtDesc(
                        List.of(LoadPostStatus.OPEN, LoadPostStatus.PARTIALLY_BOOKED))
                .stream()
                .filter(p -> myTrucks.stream().anyMatch(t ->
                        t.getCategory().getId().equals(p.getCategory().getId())
                        && t.getPayloadTons().compareTo(p.getTonsPerTruck()) >= 0
                        && (p.getBodyType() == null
                                || t.getBodyType() == p.getBodyType())))
                .map(p -> LoadPostMapper.toResponse(p, countLiveBookings(p)))
                .toList();
    }

    // ---------- Helpers: counting, status, expiry, ownership ----------

    /** Live bookings linked to this post (Module 4 ACTIVE set). */
    public int countLiveBookings(LoadPost post) {
        return bookingRepository.countByLoadPostIdAndStatusIn(
                post.getId(), BookingStatus.ACTIVE);
    }

    /** Recompute the post status from the live count — the ONLY place
     *  the status moves because of a booking. Called after every booking,
     *  rejection and cancellation that touches this post (see Edit C). */
    @Transactional
    public void refreshPostStatus(LoadPost post) {
        if (LoadPostStatus.isFinal(post.getStatus())) return;
        int live = countLiveBookings(post);
        LoadPostStatus target = live == 0 ? LoadPostStatus.OPEN
                : live >= post.getTrucksNeeded() ? LoadPostStatus.FULLY_BOOKED
                : LoadPostStatus.PARTIALLY_BOOKED;
        moveStatus(post, target);
    }

    /** The ONLY place a post's status changes. Every move is checked here. */
    private void moveStatus(LoadPost post, LoadPostStatus to) {
        if (post.getStatus() == to) return; // already there — nothing to do
        if (!LoadPostStatus.canMoveTo(post.getStatus(), to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid load-post status change from "
                    + post.getStatus() + " to " + to);
        }
        post.setStatus(to);
        postRepository.save(post);
    }

    /** Any post whose pickup date passed while trucks were still needed. */
    private void expireOverduePosts() {
        List<LoadPost> overdue = postRepository.findByStatusInAndPickupDateBefore(
                List.of(LoadPostStatus.OPEN, LoadPostStatus.PARTIALLY_BOOKED),
                LocalDate.now());
        for (LoadPost p : overdue) {
            moveStatus(p, LoadPostStatus.EXPIRED);
        }
    }

    private LoadPost loadPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Load post not found: " + postId));
    }

    private void requirePoster(User user, LoadPost post) {
        if (!post.getPostedBy().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This load post is not yours");
        }
    }

    private String ownerNameOf(Long truckId) {
        return truckRepository.findById(truckId)
                .map(t -> t.getOwner().getFullName()).orElse("Unknown owner");
    }
}
