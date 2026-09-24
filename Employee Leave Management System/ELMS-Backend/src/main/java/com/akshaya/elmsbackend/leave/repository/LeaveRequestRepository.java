package com.akshaya.elmsbackend.leave.repository;

import com.akshaya.elmsbackend.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployee_IdOrderByCreatedAtDescIdDesc(Long employeeId);

    @Query("""
            select count(r) from LeaveRequest r
            where r.employee.id = :employeeId
              and r.status in ('PENDING', 'APPROVED')
              and r.startDate <= :endDate
              and r.endDate >= :startDate
            """)
    long countOverlappingActiveRequests(
            @Param("employeeId") Long employeeId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findForDecision(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from LeaveRequest r
            where r.employee.id = :employeeId
              and r.leaveType = 'SICK'
              and r.workingDays >= 3
              and r.status in ('PENDING', 'APPROVED')
              and r.certificateReminderShownAt is null
              and not exists (
                  select a.id from LeaveAttachment a where a.leaveRequest = r
              )
            order by r.endDate, r.id
            """)
    List<LeaveRequest> findCertificateReminderCandidates(
            @Param("employeeId") Long employeeId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findForCertificateReminder(@Param("id") Long id);
}
