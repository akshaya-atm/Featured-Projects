import { refreshAccessToken, AuthenticationError } from "./services/auth-service.js";
import { setAccessToken, clearAccessToken } from "./services/api-client.js";
import { initializeLogin, showLoginError } from "./login.js";
import { initializeDashboard, LOCAL_SIGNOUT_KEY } from "./main.js";

const sessionScreen = document.querySelector("#session-screen");
const loginScreen = document.querySelector("#login-screen");
const retryButton = document.querySelector("#session-retry");
const sessionMessage = document.querySelector("#session-message");

function showLoginScreen(errorMessage = "") {
  sessionScreen.hidden = true;
  loginScreen.hidden = false;
  
  if (errorMessage) {
    showLoginError(errorMessage);
  }
}

function handleLoginSuccess(accessToken) {
  localStorage.removeItem(LOCAL_SIGNOUT_KEY);
  setAccessToken(accessToken);
  loginScreen.hidden = true;
  initializeDashboard();
}

async function startApp() {
  if (localStorage.getItem(LOCAL_SIGNOUT_KEY) === "1") {
    clearAccessToken();
    showLoginScreen("Signed out of this browser. Server logout could not be confirmed, so the server session may still be active.");
    return;
  }

  // Start with loading screen
  sessionScreen.hidden = false;
  loginScreen.hidden = true;
  retryButton.hidden = true;
  sessionMessage.textContent = "Loading your session…";

  try {
    const accessToken = await refreshAccessToken();
    setAccessToken(accessToken);
    console.info("[App] Session restored automatically.");
    
    // Hide loading screen, show dashboard
    sessionScreen.hidden = true;
    initializeDashboard();
  } catch (error) {
    clearAccessToken();
    
    if (error instanceof AuthenticationError && error.status === 401) {
      console.info("[App] No valid refresh session. Showing login.");
      if (error.backendCode === "TOKEN_EXPIRED") {
        showLoginScreen("Your session has expired. Please log in again.");
      } else {
        showLoginScreen();
      }
    } else {
      console.warn("[App] Unable to load session; check network.");
      sessionMessage.textContent = "Unable to connect to the server. Please retry.";
      retryButton.hidden = false;
      retryButton.disabled = false;
      retryButton.focus();
    }
  }
}

// Initialize the login form event listeners
initializeLogin({ onLoginSuccess: handleLoginSuccess });

retryButton.addEventListener("click", () => {
  retryButton.disabled = true;
  startApp();
});

// Kick off the initial load
startApp();
