package com.akshaya.elmsbackend.leave.repository;

import com.akshaya.elmsbackend.leave.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    List<LeaveBalance> findByEmployee_IdAndBalanceYear(Long employeeId, short year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LeaveBalance> findByEmployee_IdAndLeaveTypeAndBalanceYear(Long employeeId, String leaveType, short year);
}
