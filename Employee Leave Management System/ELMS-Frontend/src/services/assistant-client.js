import { apiRequest } from "./api-client.js";

const ASSISTANT_MESSAGE_ENDPOINT = "/assistant/messages";

export async function sendAssistantMessage(
  { message, conversationId = null },
  { signal } = {},
) {
  const response = await apiRequest(ASSISTANT_MESSAGE_ENDPOINT, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ message, conversationId }),
    signal,
  });

  const data = await response.json();

  if (typeof data.reply !== "string" || !data.reply.trim()) {
    throw new Error("Assistant response did not include a reply");
  }

  const nextConversationId = data.conversationId ?? conversationId;

  if (
    typeof nextConversationId !== "string" ||
    !nextConversationId.trim()
  ) {
    throw new Error("Assistant response did not include a conversation ID");
  }

  let certificateUpload = null;

  if (data.certificateUpload != null) {
    const requestId = data.certificateUpload.requestId;

    if (!Number.isSafeInteger(requestId) || requestId <= 0) {
      throw new Error("Assistant response included an invalid certificate upload request");
    }

    certificateUpload = { requestId };
  }

  return {
    conversationId: nextConversationId,
    reply: data.reply,
    dashboardChanged: data.dashboardChanged === true,
    certificateUpload,
  };
}
