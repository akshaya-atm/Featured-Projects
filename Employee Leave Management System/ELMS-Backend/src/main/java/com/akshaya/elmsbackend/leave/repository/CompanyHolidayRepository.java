package com.akshaya.elmsbackend.leave.repository;

import com.akshaya.elmsbackend.leave.entity.CompanyHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Set;

public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {
    @Query("select h.holidayDate from CompanyHoliday h where h.holidayDate between :start and :end")
    Set<LocalDate> datesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
