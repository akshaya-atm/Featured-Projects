INSERT INTO employee_credentials (
    employee_ref_id,
    password_hash
)
VALUES

    (
        (SELECT id FROM employees WHERE employee_id = 'EMP001'),
        '$2y$12$m7zVafzi35I8rP4vTFqElu19tqsKzH5z.DHCWfelGSpS3faamLXBC'
    ),

    (
        (SELECT id FROM employees WHERE employee_id = 'EMP002'),
        '$2y$12$gqqrP9wX0tdeRx0qTbdGpukLBd17G8rG7IcHqMRhFwWq0abMFjRxS'
    ),

    (
        (SELECT id FROM employees WHERE employee_id = 'EMP003'),
        '$2y$12$t0CG2fjT/2f94zIbCdfx4OKhjqmiEdA5rGzTlVTKKg.gC1itJIsmW'
    ),

    (
        (SELECT id FROM employees WHERE employee_id = 'EMP004'),
        '$2y$12$.mSg0Nk7dMMhsGl3Xr4wi.pAzt2/Aua3girgdF8wCDAPycRQdQglu'
    ),

    (
        (SELECT id FROM employees WHERE employee_id = 'EMP005'),
        '$2y$12$uCht99BGjbZJHR3kYHZi8uS5j86n7f5tzvpTwCw/KfQlzadcj.Jum'
    );