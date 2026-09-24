import { COMPANY_NAME } from "./config.js";
import { login } from "./services/auth-service.js";

const DEFAULT_ERROR_MESSAGE =
  "Unable to log in. Check your credentials and try again.";

let errorElementInstance = null;

export function showLoginError(message) {
  if (errorElementInstance) {
    errorElementInstance.textContent = message;
    errorElementInstance.hidden = !message;
  }
}

function getRequiredElement(selector, parent = document) {
  const element = parent.querySelector(selector);

  if (!element) {
    throw new Error(`Missing login element: ${selector}`);
  }

  return element;
}

function updateError(element, message = "") {
  element.textContent = message;
  element.hidden = !message;
}

function setSubmitting(form, button, isSubmitting) {
  button.disabled = isSubmitting;
  button.textContent = isSubmitting ? "Logging in…" : "Login";

  if (isSubmitting) {
    form.setAttribute("aria-busy", "true");
  } else {
    form.removeAttribute("aria-busy");
  }
}

function readCredentials(form) {
  const formData = new FormData(form);

  return {
    employeeId: String(formData.get("employeeId") ?? "").trim(),
    password: String(formData.get("password") ?? ""),
  };
}

export function initializeLogin({ onLoginSuccess }) {
  const form = getRequiredElement("#login-form");
  const submitButton = getRequiredElement(
    '.login-form__submit[type="submit"]',
    form,
  );
  const errorElement = getRequiredElement("#login-error");
  errorElementInstance = errorElement;

  async function handleSubmit(event) {
    event.preventDefault();
    updateError(errorElement);

    if (!form.reportValidity()) {
      return;
    }

    setSubmitting(form, submitButton, true);

    try {
      const accessToken = await login(readCredentials(form));
      console.info("[Auth] Login succeeded. Storing access token and opening dashboard.");
      
      form.reset(); // clear the form for the next time
      
      if (onLoginSuccess) {
        onLoginSuccess(accessToken);
      }
    } catch (error) {
      console.warn("[Auth] Login did not complete. If no HTTP status was logged, check Network for a connection or CORS failure.");
      const message = error.backendMessage || DEFAULT_ERROR_MESSAGE;
      updateError(errorElement, message);
    } finally {
      setSubmitting(form, submitButton, false);
    }
  }

  function handleInput() {
    updateError(errorElement);
  }

  form.addEventListener("submit", handleSubmit);
  form.addEventListener("input", handleInput);

  return function cleanupLogin() {
    form.removeEventListener("submit", handleSubmit);
    form.removeEventListener("input", handleInput);
  };
}

document.querySelector("#company-name").textContent = COMPANY_NAME;
