import { clearAccessToken } from "./services/api-client.js";
import { initializeChatAssistant } from "./features/chat-assistant.js";
import { initializeEmployeeSummary } from "./features/employee-summary.js";
import { initializeMyLeaveRequests } from "./features/my-leave-requests.js";
import { logout } from "./services/auth-service.js";
import { initializeTeamRequests } from "./features/team-requests.js";

let dashboardInitialized = false;
export const LOCAL_SIGNOUT_KEY = "elms.localSignout";

export async function initializeDashboard() {
  document.querySelector("#app").hidden = false;
  document.querySelector("#logout-button").hidden = false;

  const logoutButton = document.querySelector("#logout-button");
  logoutButton.onclick = async () => {
    logoutButton.disabled = true;
    logoutButton.textContent = "Logging out…";

    try {
      await logout();
      localStorage.removeItem(LOCAL_SIGNOUT_KEY);
      clearAccessToken();
      window.location.reload();
    } catch {
      console.warn("[Auth] Server logout could not be confirmed; signing out of this browser locally.");
      localStorage.setItem(LOCAL_SIGNOUT_KEY, "1");
      clearAccessToken();
      window.location.reload();
    } finally {
      logoutButton.disabled = false;
      logoutButton.textContent = "Logout";
    }
  };

  if (!dashboardInitialized) {
    dashboardInitialized = true;
    const leaveRequests = initializeMyLeaveRequests();
    const teamRequests = initializeTeamRequests({ onDecision: () => refreshDashboard() });
    const refreshDashboard = async () => {
      const [employee] = await Promise.all([initializeEmployeeSummary(), leaveRequests.refresh()]);
      await teamRequests.refresh({ enabled: employee?.isManager === true });
    };

    initializeChatAssistant({ onDashboardChange: refreshDashboard });
    await refreshDashboard();
  }
}
