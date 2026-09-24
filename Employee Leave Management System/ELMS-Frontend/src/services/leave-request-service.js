import { apiRequest } from "./api-client.js";

const MY_LEAVE_REQUESTS_ENDPOINT = "/leave-requests/me";

function mapLeaveRequest(data) {
  return {
    id: data.id,
    leaveType: data.leaveType,
    startDate: data.startDate,
    endDate: data.endDate,
    status: data.status,
    medicalCertificate: {
      required: data.medicalCertificate?.required === true,
      uploaded: data.medicalCertificate?.uploaded === true,
      fileName: data.medicalCertificate?.fileName,
    },
  };
}

export async function getMyLeaveRequests({ signal } = {}) {
  const response = await apiRequest(MY_LEAVE_REQUESTS_ENDPOINT, {
    headers: {
      Accept: "application/json",
    },
    signal,
  });
  const data = await response.json();

  if (!Array.isArray(data.requests)) {
    throw new Error("Leave requests response did not include a requests array");
  }

  return data.requests.map(mapLeaveRequest);
}

export async function getPendingTeamRequests({ signal } = {}) {
  const response = await apiRequest("/leave-requests/team/pending", {
    headers: { Accept: "application/json" },
    signal,
  });
  const data = await response.json();
  if (!Array.isArray(data.requests)) {
    throw new Error("Team response did not include a requests array");
  }
  return data.requests;
}

export async function decideLeaveRequest(requestId, decision, reason = "") {
  if (!["approve", "reject"].includes(decision)) {
    throw new TypeError("Invalid leave decision");
  }
  const response = await apiRequest(
    `/leave-requests/${encodeURIComponent(requestId)}/${decision}`,
    {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({ reason }),
    },
  );
  return response.json();
}

export async function uploadMedicalCertificate(
  requestId,
  file,
  { signal } = {},
) {
  if (requestId === undefined || requestId === null || requestId === "") {
    throw new TypeError("A leave request ID is required");
  }

  if (!(file instanceof File)) {
    throw new TypeError("A medical certificate file is required");
  }

  const body = new FormData();
  body.append("document", file);

  await apiRequest(
    `/leave-requests/${encodeURIComponent(requestId)}/medical-certificate`,
    {
      method: "POST",
      body,
      signal,
    },
  );
}

export async function downloadMedicalCertificate(requestId, fileName) {
  const response = await apiRequest(
    `/leave-requests/${encodeURIComponent(requestId)}/medical-certificate`,
    { headers: { Accept: "application/pdf,image/png,image/jpeg" } },
  );
  const objectUrl = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileName || `medical-certificate-${requestId}`;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
}

export async function getLeaveBalances({ signal } = {}) {
  const response = await apiRequest("/leave-balances/me", {
    headers: { Accept: "application/json" },
    signal,
  });
  
  const data = await response.json();
  return {
    annual: data.annual,
    casual: data.casual,
    sick: data.sick,
  };
}
