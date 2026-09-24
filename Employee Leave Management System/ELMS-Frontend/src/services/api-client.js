import { API_BASE_URL } from "../config.js";
import { refreshAccessToken } from "./auth-service.js";

let accessToken = null;
let refreshRequest = null;

export function getAccessToken() {
  return accessToken;
}

export function setAccessToken(token) {
  if (typeof token !== "string" || !token.trim()) {
    throw new TypeError("Access token must be a non-empty string");
  }

  accessToken = token;
}

export function clearAccessToken() {
  accessToken = null;
}

export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

function createApiUrl(path) {
  const baseUrl = API_BASE_URL.replace(/\/+$/, "");
  const normalizedPath = path.replace(/^\/+/, "");

  return `${baseUrl}/${normalizedPath}`;
}

function sendRequest(path, options) {
  const headers = new Headers(options.headers);
  const accessToken = getAccessToken();

  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  return fetch(createApiUrl(path), {
    ...options,
    headers,
  });
}

function renewAccessToken() {
  if (!refreshRequest) {
    refreshRequest = refreshAccessToken()
      .then((token) => {
        setAccessToken(token);
        return token;
      })
      .finally(() => {
        refreshRequest = null;
      });
  }

  return refreshRequest;
}

export async function apiRequest(path, options = {}) {
  let response = await sendRequest(path, options);

  if (response.status === 401) {
    try {
      await renewAccessToken();
    } catch (error) {
      clearAccessToken();
      throw error;
    }

    response = await sendRequest(path, options);
  }

  if (response.status === 401) {
    clearAccessToken();
  }

  if (!response.ok) {
    let message = `API request failed with status ${response.status}`;
    let code = null;
    try {
      const data = await response.json();
      if (typeof data.errorMessage === "string" && data.errorMessage.trim()) {
        message = data.errorMessage;
      }
      if (typeof data.code === "string" && data.code.trim()) {
        code = data.code;
      }
    } catch {
      // Non-JSON failures retain the status-based message.
    }
    throw new ApiError(message, response.status, code);
  }

  return response;
}
