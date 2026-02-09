package com.example.demo.repos;

import com.example.demo.models.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignRepo extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findByRequestIDAndStatus(Integer requestID, String status);

    List<Assignment> findAllByStatus(String status);

    @Query("SELECT a FROM Assignment a WHERE a.status = 'WAITLIST' ORDER BY a.priority ASC, a.id ASC")
    List<Assignment> findWaitlistInOrder();

    // FIXED: New query to find next waiter by priority
    @Query("SELECT a FROM Assignment a WHERE a.status = 'WAITLIST' AND a.priority = :priority ORDER BY a.id ASC LIMIT 1")
    Optional<Assignment> findFirstWaitlistedByPriority(@Param("priority") int priority);
}