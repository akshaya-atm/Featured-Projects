import { getCurrentEmployee } from "../services/employee-service.js";
import { getLeaveBalances } from "../services/leave-request-service.js";

const EMPTY_VALUE = "—";

function getSummaryElements() {
  return {
    section: document.querySelector(".employee-summary"),
    name: document.querySelector("#employee-name"),
    employeeId: document.querySelector("#employee-id"),
    designation: document.querySelector("#employee-designation"),
    department: document.querySelector("#employee-department"),
    manager: document.querySelector("#employee-manager"),
    annualLeave: document.querySelector("#annual-leave-balance"),
    casualLeave: document.querySelector("#casual-leave-balance"),
    sickLeave: document.querySelector("#sick-leave-balance"),
    pendingRequests: document.querySelector("#pending-requests"),
    pendingRequestCount: document.querySelector("#pending-request-count"),
    status: document.querySelector("#employee-summary-status"),
  };
}

function ensureElementsExist(elements) {
  const missingElement = Object.entries(elements).find(([, element]) => !element);

  if (missingElement) {
    throw new Error(`Missing employee summary element: ${missingElement[0]}`);
  }
}

function displayValue(value) {
  return typeof value === "string" && value.trim() ? value : EMPTY_VALUE;
}

function formatLeaveBalance(balance) {
  if (
    !balance ||
    !Number.isFinite(balance.remaining) ||
    !Number.isFinite(balance.total)
  ) {
    return EMPTY_VALUE;
  }

  return `${balance.remaining} / ${balance.total} days`;
}

function formatCount(value) {
  return Number.isInteger(value) && value >= 0 ? String(value) : EMPTY_VALUE;
}

function updateStatus(element, message) {
  element.textContent = message;
  element.hidden = !message;
}

function renderEmployee(employee, elements) {
  elements.name.textContent = displayValue(employee.name);
  elements.employeeId.textContent = displayValue(employee.employeeId);
  elements.designation.textContent = displayValue(employee.designation);
  elements.department.textContent = displayValue(employee.department);
  elements.manager.textContent = displayValue(employee.managerName);
  elements.annualLeave.textContent = formatLeaveBalance(
    employee.leaveBalances.annual,
  );
  elements.casualLeave.textContent = formatLeaveBalance(
    employee.leaveBalances.casual,
  );
  elements.sickLeave.textContent = formatLeaveBalance(
    employee.leaveBalances.sick,
  );
  elements.pendingRequests.hidden = !employee.isManager;
}

export async function initializeEmployeeSummary() {
  const elements = getSummaryElements();
  ensureElementsExist(elements);

  elements.section.setAttribute("aria-busy", "true");
  updateStatus(elements.status, "Loading employee details…");

  try {
    const [employee, balances] = await Promise.all([
      getCurrentEmployee(),
      getLeaveBalances()
    ]);
    
    employee.leaveBalances = balances;
    
    renderEmployee(employee, elements);
    updateStatus(elements.status, "");

    return employee;
  } catch (error) {
    console.error("Unable to load employee summary", error);
    elements.pendingRequests.hidden = true;
    updateStatus(elements.status, "Unable to load employee details.");

    return null;
  } finally {
    elements.section.removeAttribute("aria-busy");
  }
}
