import { API_BASE_URL } from "../config.js";

const LOGIN_ENDPOINT = `${API_BASE_URL}/auth/login`;
const REFRESH_ENDPOINT = `${API_BASE_URL}/auth/refresh`;
const LOGOUT_ENDPOINT = `${API_BASE_URL}/auth/logout`;

export class AuthenticationError extends Error {
  constructor(status, backendMessage, backendCode) {
    super(`Authentication failed with status ${status}`);
    this.name = "AuthenticationError";
    this.status = status;
    this.backendMessage = backendMessage;
    this.backendCode = backendCode;
  }
}

async function readAccessToken(response) {
  if (!response.ok) {
    console.warn("[Auth] Token refresh rejected. HTTP status:", response.status);
    let backendMessage = "";
    let backendCode = "";
    try {
      const data = await response.json();
      backendMessage = data.errorMessage;
      backendCode = data.code;
    } catch (e) {
      // Ignored if response isn't JSON
    }
    throw new AuthenticationError(response.status, backendMessage, backendCode);
  }

  const data = await response.json();

  if (typeof data.accessToken !== "string" || !data.accessToken.trim()) {
    console.warn("[Auth] Token refresh response has no usable access token.");
    throw new Error("Authentication response did not include an access token");
  }

  return data.accessToken;
}

export async function login(credentials, { signal } = {}) {
  console.info("[Auth] Sending login request to", LOGIN_ENDPOINT);
  const response = await fetch(LOGIN_ENDPOINT, {
    method: "POST",
    credentials: "include",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(credentials),
    signal,
  });

  console.info("[Auth] Login response HTTP status:", response.status);
  return readAccessToken(response);
}

export async function refreshAccessToken({ signal } = {}) {
  console.info("[Auth] Requesting an access token from", REFRESH_ENDPOINT);
  const response = await fetch(REFRESH_ENDPOINT, {
    method: "POST",
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
    signal,
  });

  console.info("[Auth] Token refresh response HTTP status:", response.status);
  return readAccessToken(response);
}

export async function logout({ signal } = {}) {
  const response = await fetch(LOGOUT_ENDPOINT, {
    method: "POST",
    credentials: "include",
    signal,
  });

  if (!response.ok) {
    throw new Error(`Logout failed with status ${response.status}`);
  }
}
