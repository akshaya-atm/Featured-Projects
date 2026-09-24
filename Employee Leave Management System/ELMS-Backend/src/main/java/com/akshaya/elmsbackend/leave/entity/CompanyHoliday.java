package com.akshaya.elmsbackend.leave.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "company_holidays")
public class CompanyHoliday {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;
    @Column(nullable = false, length = 100)
    private String name;
    protected CompanyHoliday() {}
}
