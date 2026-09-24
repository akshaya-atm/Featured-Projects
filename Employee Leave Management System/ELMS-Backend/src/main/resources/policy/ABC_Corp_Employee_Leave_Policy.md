# ABC CORP

## Employee Leave Policy

**Version 1.2 | Effective 23 September 2026**

### 1. Purpose

This policy defines the leave rules used by the ABC Corp Employee Leave Management System. It is intentionally concise and is the source for both application behavior and policy retrieval by the Leave Assistant.

### 2. Working-day rules

- A working day is Monday through Friday, excluding published company holidays.
- Saturdays, Sundays, and company holidays are not deducted from leave balances.
- Leave duration is the number of working days within one continuous start-to-end date range.
- Half-day leave is not supported.
- A request cannot start in the past.

### 3. Entitlements

| Leave type | Annual entitlement | Carry forward |
|---|---:|---:|
| Annual Leave | 18 working days | Up to 5 days |
| Casual Leave | 6 working days | Not allowed |
| Sick Leave | 10 working days | Not allowed |
| Unpaid Leave | No fixed entitlement | Not applicable |

Available paid-leave balance equals entitlement plus eligible carry-forward, minus leave already used or reserved by active requests.

### 4. Approval routes

| Leave request | Required route |
|---|---|
| Casual Leave, 1-2 working days | Auto-approved |
| Casual Leave, more than 2 working days | Rejected by validation |
| Annual Leave, 1-5 working days | Direct manager |
| Annual Leave, more than 5 working days | Direct manager, then second-level manager |
| Sick Leave, any duration | Direct manager |
| Unpaid Leave, 1-3 working days | Direct manager |
| Unpaid Leave, more than 3 working days | Direct manager, then second-level manager |

There is no advance-notice calculation, team-coverage calculation, or HR approval stage in this demonstration system.

### 5. Casual Leave auto-approval

A Casual Leave request is auto-approved when it contains one or two working days, has sufficient balance, and does not overlap an existing active request. The system records an `AUTO` approval entry. A request containing more than two working days is rejected without creating a leave request or reserving balance.

### 6. Sequential manager approval

- The direct manager is the manager assigned to the employee record.
- The second-level manager is the direct manager's manager.
- Required stages are completed in sequence.
- A second-level manager cannot act before the direct manager approves.
- A manager can act only on the stage assigned to them.
- An employee cannot approve their own request.
- If a required active approver is unavailable, the request is not submitted.

### 7. Validation and balance rules

- Paid leave cannot exceed the employee's available balance.
- A request cannot overlap an existing `PENDING` or `APPROVED` request for the same employee.
- Pending paid leave reserves balance.
- Final approval consumes the reserved balance.
- Rejection releases the reserved balance.
- Unpaid Leave does not consume a paid-leave balance.
- Only working days are counted when calculating duration and balance changes.

### 8. Medical certificates

- A medical certificate is optional for Sick Leave of one or two working days.
- A medical certificate is required for Sick Leave of three or more working days.
- The Sick Leave request can be submitted before the certificate is uploaded.
- The certificate should be uploaded within two working days after the employee returns.
- A missing certificate produces a follow-up reminder; it does not invent or change an approval outcome.

### 9. Request statuses

| Status | Meaning |
|---|---|
| `PENDING` | Waiting for one or more required manager approvals |
| `APPROVED` | Auto-approval completed or all manager stages approved |
| `REJECTED` | An assigned manager rejected the request |
| `CANCELLED` | The request was withdrawn or cancelled |
| `COMPLETED` | The approved leave period ended |

### 10. Access and authorization

- Employees may view their own balances and requests and submit their own leave.
- Managers may view and decide only approval stages assigned to them.
- Manager authority comes from the employee reporting hierarchy, not from the AI model.
- The authenticated employee ID comes from the bearer token.
- The Leave Assistant and any MCP client must use backend tools and cannot bypass application authorization.

### 11. Examples

**Two-day Casual Leave:** An employee requests Thursday and Friday as Casual Leave. Balance is sufficient and there is no overlap, so the request is auto-approved.

**Long Casual Leave:** An employee requests three working days as Casual Leave. The system rejects the submission because Casual Leave is limited to two working days per request.

**Long Annual Leave:** An employee requests seven working days of Annual Leave. The direct manager must approve first, followed by the second-level manager.

**Long Unpaid Leave:** An employee requests four working days of Unpaid Leave. The direct manager must approve first, followed by the second-level manager.

**Sick Leave certificate:** An employee requests three working days of Sick Leave. The direct manager reviews the request, and the employee uploads a medical certificate after returning.

### 12. Leave Assistant guidance

For policy questions, retrieve the most specific applicable rule from this document. Transactional facts such as current balance, reporting hierarchy, request status, assigned approver, and certificate state must come from application tools rather than this document.
