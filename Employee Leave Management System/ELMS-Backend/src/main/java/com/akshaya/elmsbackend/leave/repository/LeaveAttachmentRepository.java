package com.akshaya.elmsbackend.leave.repository;

import com.akshaya.elmsbackend.leave.entity.LeaveAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeaveAttachmentRepository extends JpaRepository<LeaveAttachment, Long> {
    
    Optional<LeaveAttachment> findByLeaveRequest_Id(Long leaveRequestId);
    
    boolean existsByLeaveRequest_Id(Long leaveRequestId);
}
