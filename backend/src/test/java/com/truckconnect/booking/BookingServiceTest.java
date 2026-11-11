package com.truckconnect.booking;

import com.truckconnect.truck.Truck;
import com.truckconnect.truck.TruckRepository;
import com.truckconnect.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock TruckRepository truckRepository;
    @InjectMocks BookingService bookingService;

    // Transition table: each row is (from, to, allowed?).
    // The full allowed path is:
    // PENDING -> CONFIRMED / REJECTED / CANCELLED
    // CONFIRMED -> DRIVER_ASSIGNED / CANCELLED
    // DRIVER_ASSIGNED -> PICKED_UP / CANCELLED
    // PICKED_UP -> IN_TRANSIT
    // IN_TRANSIT -> DELIVERED / DISPUTED
    // DELIVERED -> COMPLETED / DISPUTED
    // DISPUTED -> COMPLETED / CANCELLED   (admin only, Part 5)
    // COMPLETED, REJECTED, CANCELLED are terminal: nothing leaves them.
    @Test
    void transitionTable_completedBookingCannotMove() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.COMPLETED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class,
                () -> bookingService.changeStatus(1L, BookingStatus.CANCELLED, new User()));
    }

    @Test
    void transitionTable_pendingCanBeConfirmed() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Booking result = bookingService.changeStatus(1L, BookingStatus.CONFIRMED, new User());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void transitionTable_pendingCannotJumpToDelivered() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class,
                () -> bookingService.changeStatus(1L, BookingStatus.DELIVERED, new User()));
    }

    @Test
    void noDoubleBooking_truckWithActiveBookingCannotBeBookedAgain() {
        Truck truck = new Truck();
        truck.setId(7L);
        Booking active = new Booking();
        active.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findActiveByTruckId(7L)).thenReturn(List.of(active));

        assertThrows(IllegalStateException.class,
                () -> bookingService.createBooking(new User(), truck, null));
    }
}
