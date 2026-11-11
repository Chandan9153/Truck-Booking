package com.truckconnect.loadpost;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoadPostRepository extends JpaRepository<LoadPost, Long> {

    /** The board: open posts, newest first. */
    List<LoadPost> findByStatusInOrderByCreatedAtDesc(
            java.util.Collection<LoadPostStatus> statuses);

    List<LoadPost> findByPostedByIdOrderByCreatedAtDesc(Long postedById);

    /** Posts whose pickup date has passed while trucks were still needed. */
    List<LoadPost> findByStatusInAndPickupDateBefore(
            java.util.Collection<LoadPostStatus> statuses, LocalDate date);

    /** Lock the post row until the transaction ends — the over-booking guard. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LoadPost p where p.id = :id")
    Optional<LoadPost> findByIdForUpdate(@Param("id") Long id);
}
