import { getPendingTeamRequests, decideLeaveRequest, downloadMedicalCertificate } from "../services/leave-request-service.js";
import { ApiError } from "../services/api-client.js";

export function initializeTeamRequests({ onDecision }) {
  const list = document.querySelector("#team-leave-requests");
  const status = document.querySelector("#team-requests-status");
  const empty = document.querySelector("#team-requests-empty");
  const count = document.querySelector("#pending-request-count");
  const reload = document.querySelector("#team-requests-reload");
  let loading = null;
  let deciding = false;

  function message(text, error = false) {
    status.textContent = text;
    status.hidden = !text;
    status.dataset.state = error ? "error" : "";
  }

  function element(tag, text, className = "") {
    const node = document.createElement(tag);
    node.textContent = text;
    node.className = className;
    return node;
  }

  function createCard(request) {
    const row = element("li", "", "leave-request-row team-request");
    const details = document.createElement("details");
    const summary = document.createElement("summary");
    const body = element("div", "", "team-request__body");
    details.className = "team-request__details";
    summary.className = "team-request__summary";
    summary.append(
      element("span", `${request.employeeName} (${request.employeeId})`, "team-request__employee"),
      element(
        "span",
        `${request.leaveType} · ${request.startDate} – ${request.endDate} · ${request.workingDays} working day(s)`,
        "team-request__meta",
      ),
    );
    if (request.reason) body.append(element("p", request.reason, "team-request__reason"));

    if (request.medicalCertificate?.overdue) {
      body.append(element("p", "Medical certificate not uploaded", "leave-request-card__notice leave-request-card__notice--error"));
    } else if (request.medicalCertificate?.required && !request.medicalCertificate?.uploaded) {
      body.append(element("p", "Optional medical certificate not uploaded", "leave-request-card__notice"));
    } else if (request.medicalCertificate?.uploaded) {
      const docP = element("p", "", "leave-request-card__document");
      const downloadButton = element(
        "button",
        request.medicalCertificate.fileName
          ? `Download Certificate: ${request.medicalCertificate.fileName}`
          : "Download Medical Certificate",
      );
      downloadButton.type = "button";
      const downloadStatus = element("span", "", "team-request__download-status");
      downloadStatus.setAttribute("role", "status");
      downloadButton.addEventListener("click", async () => {
        downloadButton.disabled = true;
        downloadStatus.textContent = "Downloading…";
        try {
          await downloadMedicalCertificate(
            request.id,
            request.medicalCertificate.fileName,
          );
          downloadStatus.textContent = "Download started.";
        } catch {
          downloadStatus.textContent = "Unable to download the certificate. Please try again.";
        } finally {
          downloadButton.disabled = false;
        }
      });
      docP.append(downloadButton, downloadStatus);
      body.append(docP);
    }

    const label = element("label", "Decision note (optional)", "team-request__note-label");
    const note = document.createElement("textarea");
    note.id = `decision-note-${request.id}`;
    note.maxLength = 500;
    note.rows = 2;
    label.htmlFor = note.id;
    const actions = element("div", "", "team-request-actions");
    for (const decision of ["approve", "reject"]) {
      const button = element("button", decision === "approve" ? "Approve" : "Reject");
      button.type = "button";
      button.dataset.decision = decision;
      button.setAttribute("aria-label", `${button.textContent} request ${request.id} from ${request.employeeName}`);
      button.addEventListener("click", async () => {
        if (deciding) return;
        deciding = true;
        list.querySelectorAll("button, textarea").forEach(node => { node.disabled = true; });
        reload.disabled = true;
        message("Saving decision…");
        try {
          const result = await decideLeaveRequest(request.id, decision, note.value.trim());
          await onDecision();
          message(result.status === "PENDING"
            ? "Your approval was recorded. The request is awaiting its next review."
            : decision === "approve" ? "Leave approved." : "Leave rejected.");
        } catch (error) {
          // Re-read before retrying: the server may have committed a decision whose response was lost.
          await refresh();
          message(error instanceof ApiError && [400, 403, 404, 409].includes(error.status)
            ? error.message
            : "Unable to confirm the decision. Refresh the requests before trying again.", true);
        } finally {
          deciding = false;
          reload.disabled = false;
          list.querySelectorAll("button, textarea").forEach(node => { node.disabled = false; });
        }
      });
      actions.append(button);
    }
    body.append(label, note, actions);
    details.append(summary, body);
    row.append(details);
    return row;
  }

  async function refresh({ enabled = true } = {}) {
    loading?.abort();
    list.replaceChildren();
    empty.hidden = true;
    if (!enabled) {
      message("");
      return;
    }
    const controller = new AbortController();
    loading = controller;
    reload.disabled = true;
    message("Loading team requests…");
    try {
      const requests = await getPendingTeamRequests({ signal: controller.signal });
      if (loading !== controller) return;
      list.replaceChildren(...requests.map(createCard));
      count.textContent = String(requests.length);
      empty.hidden = requests.length > 0;
      message("");
    } catch (error) {
      if (error.name !== "AbortError" && loading === controller) {
        count.textContent = "—";
        message("Unable to load team requests. Please refresh.", true);
      }
    } finally {
      if (loading === controller) {
        loading = null;
        reload.disabled = deciding;
      }
    }
  }

  reload.addEventListener("click", () => { if (!deciding) onDecision(); });
  return { refresh };
}
