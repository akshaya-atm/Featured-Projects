-- =====================================================
-- Departments
-- =====================================================

INSERT INTO departments (name)
VALUES
    ('Management'),
    ('Engineering'),
    ('Human Resources');


-- =====================================================
-- Employees
-- =====================================================

-- Top-level director
INSERT INTO employees (
    employee_id,
    full_name,
    email,
    designation,
    department_id,
    manager_id,
    date_of_joining
)
VALUES (
           'EMP001',
           'Anita Sharma',
           'anita.sharma@abc.com',
           'Director',
           (SELECT id FROM departments WHERE name = 'Management'),
           NULL,
           '2018-04-10'
       );


-- Engineering Manager
INSERT INTO employees (
    employee_id,
    full_name,
    email,
    designation,
    department_id,
    manager_id,
    date_of_joining
)
VALUES (
           'EMP002',
           'Priya Nair',
           'priya.nair@abc.com',
           'Engineering Manager',
           (SELECT id FROM departments WHERE name = 'Engineering'),
           (SELECT id FROM employees WHERE employee_id = 'EMP001'),
           '2020-06-15'
       );


-- HR Manager
INSERT INTO employees (
    employee_id,
    full_name,
    email,
    designation,
    department_id,
    manager_id,
    date_of_joining
)
VALUES (
           'EMP003',
           'Kavya Menon',
           'kavya.menon@abc.com',
           'HR Manager',
           (SELECT id FROM departments WHERE name = 'Human Resources'),
           (SELECT id FROM employees WHERE employee_id = 'EMP001'),
           '2020-02-12'
       );


-- Employee under Priya
INSERT INTO employees (
    employee_id,
    full_name,
    email,
    designation,
    department_id,
    manager_id,
    date_of_joining
)
VALUES (
           'EMP004',
           'Arun Kumar',
           'arun.kumar@abc.com',
           'Software Engineer',
           (SELECT id FROM departments WHERE name = 'Engineering'),
           (SELECT id FROM employees WHERE employee_id = 'EMP002'),
           '2023-07-03'
       );


-- Second employee under Priya
INSERT INTO employees (
    employee_id,
    full_name,
    email,
    designation,
    department_id,
    manager_id,
    date_of_joining
)
VALUES (
           'EMP005',
           'Meena Rao',
           'meena.rao@abc.com',
           'Software Engineer',
           (SELECT id FROM departments WHERE name = 'Engineering'),
           (SELECT id FROM employees WHERE employee_id = 'EMP002'),
           '2024-01-08'
       );


-- =====================================================
-- 2026 Leave Balances
-- =====================================================

-- Annual Leave
INSERT INTO leave_balances (
    employee_ref_id,
    leave_type,
    balance_year,
    entitled_days
)
SELECT
    id,
    'ANNUAL',
    2026,
    18
FROM employees;


-- Casual Leave
INSERT INTO leave_balances (
    employee_ref_id,
    leave_type,
    balance_year,
    entitled_days
)
SELECT
    id,
    'CASUAL',
    2026,
    6
FROM employees;


-- Sick Leave
INSERT INTO leave_balances (
    employee_ref_id,
    leave_type,
    balance_year,
    entitled_days
)
SELECT
    id,
    'SICK',
    2026,
    10
FROM employees;


-- Give Arun 2 carried-forward annual leave days
UPDATE leave_balances
SET carried_forward_days = 2
WHERE employee_ref_id = (
    SELECT id
    FROM employees
    WHERE employee_id = 'EMP004'
)
  AND leave_type = 'ANNUAL'
  AND balance_year = 2026;


-- =====================================================
-- Company Holidays
-- =====================================================

INSERT INTO company_holidays (
    holiday_date,
    name
)
VALUES
    ('2026-01-01', 'New Year''s Day'),
    ('2026-01-26', 'Republic Day'),
    ('2026-08-15', 'Independence Day'),
    ('2026-10-02', 'Gandhi Jayanti'),
    ('2026-12-25', 'Christmas Day');