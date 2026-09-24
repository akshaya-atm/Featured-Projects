import { sendAssistantMessage } from "../services/assistant-client.js";
import { ApiError } from "../services/api-client.js";
import { uploadMedicalCertificate } from "../services/leave-request-service.js";

const ASSISTANT_NAME = "Leave Assistant";
const USER_NAME = "You";
const REQUEST_ERROR_MESSAGE =
  "I couldn't complete that request. Please try again.";
const ACCEPTED_CERTIFICATE_TYPES =
  ".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg";
const MAX_CERTIFICATE_SIZE = 1024 * 1024;

function getRequiredElement(selector, parent = document) {
  const element = parent.querySelector(selector);

  if (!element) {
    throw new Error(`Missing chat element: ${selector}`);
  }

  return element;
}

function updateStatus(element, message = "", state = "") {
  element.textContent = message;
  element.hidden = !message;

  if (state) {
    element.dataset.state = state;
  } else {
    delete element.dataset.state;
  }
}

function setSubmitting(form, input, button, isSubmitting) {
  input.disabled = isSubmitting;
  button.disabled = isSubmitting;
  button.textContent = isSubmitting ? "Sending…" : "Send";

  if (isSubmitting) {
    form.setAttribute("aria-busy", "true");
  } else {
    form.removeAttribute("aria-busy");
  }
}

function createCertificateUploadAction(certificateUpload, onUploaded) {
  const action = document.createElement("div");
  const prompt = document.createElement("p");
  const actions = document.createElement("div");
  const uploadButton = document.createElement("button");
  const skipButton = document.createElement("button");
  const input = document.createElement("input");
  const status = document.createElement("p");

  action.className = "chat-message__upload-action";
  prompt.className = "chat-message__upload-prompt";
  actions.className = "chat-message__upload-buttons";
  uploadButton.className = "chat-message__upload-button";
  skipButton.className = "chat-message__upload-skip";
  status.className = "chat-message__upload-status";

  prompt.textContent = `Optional medical certificate for request #${certificateUpload.requestId}`;
  uploadButton.type = "button";
  uploadButton.textContent = "Choose certificate";
  skipButton.type = "button";
  skipButton.textContent = "Skip for now";
  input.type = "file";
  input.accept = ACCEPTED_CERTIFICATE_TYPES;
  input.hidden = true;
  status.setAttribute("role", "status");
  status.setAttribute("aria-live", "polite");
  status.hidden = true;

  uploadButton.addEventListener("click", () => input.click());
  skipButton.addEventListener("click", () => {
    uploadButton.hidden = true;
    skipButton.hidden = true;
    input.disabled = true;
    updateStatus(
      status,
      "Skipped. You can upload it later from My Leave Requests or ask me again.",
      "success",
    );
  });
  input.addEventListener("change", async () => {
    const [file] = input.files;

    if (!file) {
      return;
    }

    if (file.size > MAX_CERTIFICATE_SIZE) {
      updateStatus(status, "File must be smaller than 1MB.", "error");
      input.value = "";
      return;
    }

    uploadButton.disabled = true;
    skipButton.disabled = true;
    uploadButton.textContent = "Uploading…";
    updateStatus(status, `Uploading ${file.name}…`);

    try {
      await uploadMedicalCertificate(certificateUpload.requestId, file);
      uploadButton.textContent = "Certificate uploaded";
      skipButton.hidden = true;
      input.disabled = true;
      updateStatus(status, `${file.name} uploaded successfully.`, "success");

      try {
        await onUploaded?.();
      } catch (refreshError) {
        console.error("Certificate uploaded, but the dashboard did not refresh", refreshError);
      }
    } catch (error) {
      uploadButton.disabled = false;
      skipButton.disabled = false;
      uploadButton.textContent = "Choose certificate";
      input.value = "";
      updateStatus(
        status,
        error?.message || "Unable to upload the certificate. Please try again.",
        "error",
      );
    }
  });

  actions.append(uploadButton, skipButton);
  action.append(prompt, actions, input, status);
  return action;
}

function appendMessage(
  container,
  { role, content, certificateUpload = null, onCertificateUploaded = null },
) {
  const message = document.createElement("article");
  const sender = document.createElement("p");
  const body = document.createElement("p");

  message.classList.add("chat-message", `chat-message--${role}`);
  sender.classList.add("chat-message__sender");
  body.classList.add("chat-message__content");

  sender.textContent = role === "user" ? USER_NAME : ASSISTANT_NAME;
  body.textContent = content;

  message.append(sender, body);

  if (role === "assistant" && certificateUpload) {
    message.append(
      createCertificateUploadAction(
        certificateUpload,
        onCertificateUploaded,
      ),
    );
  }

  container.append(message);
  container.scrollTop = container.scrollHeight;
}

export function initializeChatAssistant({ onDashboardChange } = {}) {
  const form = getRequiredElement("#chat-form");
  const input = getRequiredElement("#chat-input", form);
  const submitButton = getRequiredElement('button[type="submit"]', form);
  const messages = getRequiredElement("#chat-messages");
  const status = getRequiredElement("#chat-status");

  let conversationId = null;
  let requestController = null;

  async function handleSubmit(event) {
    event.preventDefault();

    if (!form.reportValidity()) {
      return;
    }

    const message = input.value.trim();

    if (!message) {
      return;
    }

    appendMessage(messages, { role: "user", content: message });
    input.value = "";
    updateStatus(status, "Assistant is responding…");
    setSubmitting(form, input, submitButton, true);
    requestController = new AbortController();

    try {
      const response = await sendAssistantMessage(
        { message, conversationId },
        { signal: requestController.signal },
      );

      conversationId = response.conversationId;
      appendMessage(messages, {
        role: "assistant",
        content: response.reply,
        certificateUpload: response.certificateUpload,
        onCertificateUploaded: onDashboardChange,
      });

      if (response.dashboardChanged && onDashboardChange) {
        await onDashboardChange();
      }

      updateStatus(status);
    } catch (error) {
      if (error?.name !== "AbortError") {
        const errorMessage =
          error instanceof ApiError ? error.message : REQUEST_ERROR_MESSAGE;

        appendMessage(messages, {
          role: "assistant",
          content: errorMessage,
        });
        updateStatus(status);
      }
    } finally {
      requestController = null;
      setSubmitting(form, input, submitButton, false);
      input.focus();
    }
  }

  function handleKeyDown(event) {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      form.requestSubmit();
    }
  }

  function handleInput() {
    if (status.dataset.state === "error") {
      updateStatus(status);
    }
  }

  form.addEventListener("submit", handleSubmit);
  input.addEventListener("keydown", handleKeyDown);
  input.addEventListener("input", handleInput);

  return function cleanupChatAssistant() {
    requestController?.abort();
    form.removeEventListener("submit", handleSubmit);
    input.removeEventListener("keydown", handleKeyDown);
    input.removeEventListener("input", handleInput);
  };
}
