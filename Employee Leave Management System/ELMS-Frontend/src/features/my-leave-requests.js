import {
  getMyLeaveRequests,
  uploadMedicalCertificate,
} from "../services/leave-request-service.js";

const DATE_FORMATTER = new Intl.DateTimeFormat(undefined, {
  month: "short",
  day: "numeric",
});
const ACCEPTED_CERTIFICATE_TYPES =
  ".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg";

function getRequiredElement(selector) {
  const element = document.querySelector(selector);

  if (!element) {
    throw new Error(`Missing leave request element: ${selector}`);
  }

  return element;
}

function createElement(tagName, className, textContent) {
  const element = document.createElement(tagName);

  if (className) {
    element.className = className;
  }

  if (textContent !== undefined) {
    element.textContent = textContent;
  }

  return element;
}

function formatLabel(value, fallback) {
  if (typeof value !== "string" || !value.trim()) {
    return fallback;
  }

  return value
    .trim()
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/\b\w/g, (character) => character.toUpperCase());
}

function parseDate(value) {
  if (typeof value !== "string") {
    return null;
  }

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);

  if (!match) {
    return null;
  }

  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));

  return Number.isNaN(date.getTime()) ? null : date;
}

function createDateRange(request) {
  const startDate = parseDate(request.startDate);
  const endDate = parseDate(request.endDate);
  const dateRange = createElement("p", "leave-request-card__dates");

  if (!startDate || !endDate) {
    dateRange.textContent = "Dates unavailable";
    return dateRange;
  }

  const start = createElement("time", "", DATE_FORMATTER.format(startDate));
  const end = createElement("time", "", DATE_FORMATTER.format(endDate));
  start.dateTime = request.startDate;
  end.dateTime = request.endDate;
  dateRange.append(start, " – ", end);
  return dateRange;
}

function setStatus(element, message = "", state = "") {
  element.textContent = message;
  element.hidden = !message;

  if (state) {
    element.dataset.state = state;
  } else {
    delete element.dataset.state;
  }
}

function createCertificateAction(request, refresh) {
  const action = createElement("div", "leave-request-card__action");
  const notice = createElement(
    "p",
    request.medicalCertificate?.overdue ? "leave-request-card__notice leave-request-card__notice--error" : "leave-request-card__notice",
    request.medicalCertificate?.overdue
      ? "Reminder: medical certificate not uploaded"
      : "Medical certificate requested (optional)",
  );
  const uploadButton = createElement(
    "button",
    "leave-request-card__upload",
    "Upload certificate",
  );
  const input = createElement("input", "leave-request-card__file-input");
  const status = createElement("p", "leave-request-card__upload-status");

  notice.setAttribute("role", "note");
  uploadButton.type = "button";
  input.type = "file";
  input.accept = ACCEPTED_CERTIFICATE_TYPES;
  input.hidden = true;
  status.setAttribute("role", "status");
  status.setAttribute("aria-live", "polite");
  status.hidden = true;

  uploadButton.addEventListener("click", () => input.click());
  input.addEventListener("change", async () => {
    const [file] = input.files;

    if (!file) {
      return;
    }

    if (file.size > 1024 * 1024) {
      setStatus(status, "File must be smaller than 1MB.", "error");
      input.value = "";
      return;
    }

    uploadButton.disabled = true;
    uploadButton.textContent = "Uploading…";
    setStatus(status, `Uploading ${file.name}…`);

    try {
      await uploadMedicalCertificate(request.id, file);
      setStatus(status, "Certificate uploaded.", "success");
      await refresh();
    } catch (error) {
      console.error("Unable to upload medical certificate", error);
      setStatus(
        status,
        "Unable to upload the certificate. Please try again.",
        "error",
      );
      uploadButton.disabled = false;
      uploadButton.textContent = "Upload certificate";
      input.value = "";
    }
  });

  action.append(notice, uploadButton, input, status);
  return action;
}

function createRequestCard(request, refresh) {
  const item = createElement("li", "leave-request-row");
  const title = createElement(
    "h4",
    "leave-request-card__title",
    formatLabel(request.leaveType, "Leave request"),
  );
  const status = createElement(
    "span",
    "leave-request-card__status",
    formatLabel(request.status, "Status unavailable"),
  );

  if (typeof request.status === "string" && request.status.trim()) {
    status.dataset.status = request.status.trim().toLowerCase();
  }

  item.append(title, createDateRange(request), status);

  const certificateUploadAllowed = ["PENDING", "APPROVED"].includes(
    request.status?.toUpperCase(),
  );

  if (
    certificateUploadAllowed &&
    request.medicalCertificate.required &&
    !request.medicalCertificate.uploaded
  ) {
    item.append(createCertificateAction(request, refresh));
  } else if (request.medicalCertificate.uploaded) {
    item.append(
      createElement(
        "p",
        "leave-request-card__document",
        request.medicalCertificate.fileName
          ? `Certificate uploaded: ${request.medicalCertificate.fileName}`
          : "Medical certificate uploaded",
      ),
    );
  }

  return item;
}

export function initializeMyLeaveRequests() {
  const list = getRequiredElement("#my-leave-requests");
  const empty = getRequiredElement("#my-requests-empty");
  const status = getRequiredElement("#my-requests-status");
  const section = getRequiredElement(".request-overview");
  let requestController = null;

  async function refresh() {
    requestController?.abort();
    const controller = new AbortController();
    requestController = controller;
    section.setAttribute("aria-busy", "true");
    empty.hidden = true;
    setStatus(status, "Loading leave requests…");

    try {
      const requests = await getMyLeaveRequests({
        signal: controller.signal,
      });

      list.replaceChildren(
        ...requests.map((request) => createRequestCard(request, refresh)),
      );
      empty.hidden = requests.length > 0;
      setStatus(status);
      return requests;
    } catch (error) {
      if (error?.name !== "AbortError") {
        console.error("Unable to load leave requests", error);
        list.replaceChildren();
        empty.hidden = true;
        setStatus(status, "Unable to load leave requests.", "error");
      }

      return [];
    } finally {
      if (requestController === controller) {
        requestController = null;
        section.removeAttribute("aria-busy");
      }
    }
  }

  return {
    refresh,
    cleanup() {
      requestController?.abort();
    },
  };
}
