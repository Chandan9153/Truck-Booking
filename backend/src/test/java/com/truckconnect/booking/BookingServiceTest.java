package com.truckconnect.booking;

import com.truckconnect.booking.dto.CreateBookingRequest;
import com.truckconnect.loadpost.LoadPostService;
import com.truckconnect.quote.Quote;
import com.truckconnect.quote.QuoteRepository;
import com.truckconnect.truck.AvailabilityStatus;
import com.truckconnect.truck.Truck;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingMessageRepository messageRepository;
    @Mock QuoteRepository quoteRepository;
    @Mock TruckRepository truckRepository;
    @Mock LoadPostService loadPostService;
    @InjectMocks BookingService bookingService;

    // Transition table (Module 4): each row is (from, to, allowed?).
    // PENDING -> CONFIRMED / REJECTED / CANCELLED
    // CONFIRMED -> DRIVER_ASSIGNED / CANCELLED
    // DRIVER_ASSIGNED -> PICKED_UP / CANCELLED
    // PICKED_UP -> IN_TRANSIT
    // IN_TRANSIT -> DELIVERED / DISPUTED
    // DELIVERED -> COMPLETED / DISPUTED
    // DISPUTED -> COMPLETED / CANCELLED (admin only)
    // COMPLETED, REJECTED, CANCELLED are terminal: nothing leaves them.
    // The service reports an invalid move as 409 CONFLICT
    // (ResponseStatusException), not IllegalStateException.
    @Test
    void transitionTable_completedBookingCannotMove() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.COMPLETED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(ResponseStatusException.class,
                () -> bookingService.updateBookingStatus(1L, BookingStatus.CANCELLED));
    }

    @Test
    void transitionTable_pendingCanBeConfirmed() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        bookingService.updateBookingStatus(1L, BookingStatus.CONFIRMED);
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    void transitionTable_pendingCannotJumpToDelivered() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(ResponseStatusException.class,
                () -> bookingService.updateBookingStatus(1L, BookingStatus.DELIVERED));
    }

    @Test
    void noDoubleBooking_truckWithActiveBookingCannotBeBookedAgain() {
        User customer = new User();
        customer.setId(1L);
        Truck truck = new Truck();
        truck.setId(7L);
        truck.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        Quote quote = new Quote();
        quote.setId(1L);
        quote.setCustomer(customer);
        quote.setTruck(truck);
        quote.setStatus("ACTIVE");
        quote.setValidUntil(LocalDateTime.now().plusHours(1));

        when(quoteRepository.findById(1L)).thenReturn(Optional.of(quote));
        when(truckRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(truck));
        when(bookingRepository.existsByTruckIdAndStatusIn(eq(7L), any()))
                .thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> bookingService.createBooking(customer, new CreateBookingRequest(1L)));
    }
}
