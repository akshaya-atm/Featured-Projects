package com.akshaya.elmsbackend.leave.repository;

import com.akshaya.elmsbackend.leave.entity.LeaveApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface LeaveApprovalRepository extends JpaRepository<LeaveApproval, Long> {
    List<LeaveApproval> findByRequest_IdOrderBySequenceNo(Long requestId);
    boolean existsByRequest_IdAndApprover_Id(Long requestId, Long approverId);

    @EntityGraph(attributePaths = {"request", "request.employee"})
    @Query("""
            select approval from LeaveApproval approval
            where approval.approver.id = :approverId
              and approval.request.status = 'PENDING'
              and approval.status = 'PENDING'
              and not exists (
                  select earlier.id from LeaveApproval earlier
                  where earlier.request = approval.request
                    and earlier.sequenceNo < approval.sequenceNo
                    and earlier.status <> 'APPROVED'
              )
            order by approval.request.createdAt desc, approval.request.id desc
            """)
    List<LeaveApproval> findActionableForApprover(
            @Param("approverId") Long approverId);
}
