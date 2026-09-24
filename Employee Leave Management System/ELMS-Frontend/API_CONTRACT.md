# Frontend API contract

The frontend uses `http://localhost:8080/api` as its configured API base URL.

## Authentication

### `POST /api/auth/login`

Request body:

```json
{
  "employeeId": "EMP001",
  "password": "employee-password"
}
```

The frontend expects a successful response to set the refresh-token cookie. It
does not require a response body.

### `POST /api/auth/refresh`

The dashboard sends the refresh cookie with this request before showing its
content. A successful response must contain a nonempty string `accessToken`.

Return `401` when the refresh token is missing, expired, or invalid. The
dashboard redirects to login with the message “Your session has expired.
Please log in again.”

Network/CORS failures, other HTTP errors (including `403`, `404`, and server
errors), and malformed success responses keep the dashboard hidden and show
“Unable to load your session. Please retry.” with a Retry button. A `403` is
not interpreted as expiry because it can indicate a security configuration
problem. Retry repeats refresh and initializes the dashboard only on success.

## My leave requests

### `GET /api/leave-requests/me`

Return the most recent requests in display order:

```json
{
  "requests": [
    {
      "id": 42,
      "leaveType": "SICK_LEAVE",
      "startDate": "2026-09-22",
      "endDate": "2026-09-24",
      "status": "APPROVED",
      "medicalCertificate": {
        "required": true,
        "uploaded": false,
        "fileName": null
      }
    }
  ]
}
```

The backend owns the policy that decides when a certificate should be
requested. Uploading remains optional and does not block leave submission. The
frontend only displays the `required`, `uploaded`, and reminder state supplied
by the backend.

### `POST /api/leave-requests/{requestId}/medical-certificate`

Accept `multipart/form-data` with the uploaded file in the `document` field.
A `2xx` response marks the upload as successful; the frontend then fetches the
request list again.

The backend must confirm that the authenticated employee owns the request and
must validate the file type, size, and content before storing it.

## Assistant actions

`POST /api/assistant/messages` may include `"dashboardChanged": true` when a
successful assistant action changes leave requests, balances, or approval
counts. The frontend then refreshes the employee summary and request list.

The frontend sends the returned `conversationId` with each later message in
the same page session. The backend uses it for a bounded chat-memory window and
namespaces it with the authenticated employee ID so conversation identifiers
cannot expose another employee's history. Reloading the page starts a new
conversation.

The response may also include a `certificateUpload` action after the backend
has verified that the authenticated employee owns an eligible sick-leave
request. The frontend displays optional **Choose certificate** and **Skip for
now** controls. File bytes are uploaded directly to the multipart endpoint and
are never sent to the model.

```json
{
  "conversationId": "conversation-123",
  "reply": "Your leave request has been submitted.",
  "dashboardChanged": true,
  "certificateUpload": {
    "requestId": 42
  }
}
```
