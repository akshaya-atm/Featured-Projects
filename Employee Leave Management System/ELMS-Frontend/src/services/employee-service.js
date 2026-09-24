import { apiRequest } from "./api-client.js";

export async function getCurrentEmployee({ signal } = {}) {
  const response = await apiRequest("/employees/me", {
    headers: {
      Accept: "application/json",
    },
    signal,
  });

  const data = await response.json();

  return {
    name: data.name,
    employeeId: data.employeeId,
    designation: data.designation,
    department: data.department,
    managerName: data.managerName,
    isManager: data.manager === true,
  };
}
