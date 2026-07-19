import type { Ref } from "vue";

import { ApiError } from "@/api/ApiError";
import { STATUS_INDICATORS } from "@/constants";
import { useAppStore } from "@/stores/app";
import { useSnackbarStore } from "@/stores/snackbar";

/**
 * Sends an HTTP request to the specified URL with the given options and returns the parsed response.
 *
 * This function wraps the native fetch API and adds global behavior:
 * - Automatically sets a loading state if a Ref is provided.
 * - Applies default configurations (headers, CORS, etc.).
 * - Validates the response via `defaultResponseHandler`.
 * - Handles various response types (JSON, 204 No Content, Plain Text).
 *
 * @template T - The expected type of the response data.
 * @param url - The endpoint URL to which the request is sent.
 * @param options - Optional configuration for the request, such as headers, method, and body.
 * @param loading - Optional reactive reference to indicate the loading state during the request.
 * @param skipGlobalHandler
 * @return A promise that resolves to the response data parsed as JSON, as a string, or undefined if empty.
 */
export async function apiFetch<T>(
  url: string,
  options: RequestInit = {},
  loading?: Ref<boolean>,
  skipGlobalHandler = false
): Promise<T> {
  if (loading) loading.value = true;
  try {
    const response = await fetch(url, {
      ...getConfig(),
      ...options,
    });
    if (!skipGlobalHandler) {
      await defaultResponseHandler(response);
    }

    // Falls 204 No Content oder Content-Length 0, direkt zurückkehren
    if (
      response.status === 204 ||
      response.headers.get("content-length") === "0"
    ) {
      return undefined as T;
    }

    const contentType = response.headers.get("content-type");
    if (contentType && contentType.includes("application/json")) {
      return await response.json();
    }

    // Fallback für Plain Text oder andere Formate
    const text = await response.text();
    try {
      // Versuchen zu parsen, falls es doch JSON ohne Header ist
      return text ? JSON.parse(text) : (undefined as T);
    } catch {
      // Wenn kein JSON, dann den rohen Text zurückgeben (wichtig für Strings/Enums)
      return text as unknown as T;
    }
  } finally {
    if (loading) loading.value = false;
  }
}

/**
 * Returns a default GET configuration for the fetch API.
 * Sets standard headers (Content-Type, XSRF-TOKEN) and ensures CORS/credential handling.
 *
 * @returns {RequestInit} The default request options.
 */
export function getConfig(): RequestInit {
  return {
    headers: getHeaders(),
    mode: "cors",
    credentials: "same-origin",
    redirect: "manual",
  };
}

/**
 * Returns a default DELETE configuration for the fetch API.
 * Uses the DELETE method and includes default headers.
 *
 * @returns {RequestInit} The request options for a DELETE request.
 */
export function deleteConfig(): RequestInit {
  return {
    method: "DELETE",
    headers: getHeaders(),
    mode: "cors",
    credentials: "same-origin",
    redirect: "manual",
  };
}

/**
 * Returns a default POST configuration for the fetch API.
 *
 * @param {any} body - The data to be sent in the request body (will be stringified to JSON).
 * @returns {RequestInit} The request options for a POST request.
 */
// eslint-disable-next-line
export function postConfig(body: any): RequestInit {
  return {
    method: "POST",
    body: body ? JSON.stringify(body) : undefined,
    headers: getHeaders(),
    mode: "cors",
    credentials: "same-origin",
    redirect: "manual",
  };
}

/**
 * Returns a default PUT configuration for the fetch API.
 *
 * If the body object contains a 'version' property, it is automatically
 * included in the "If-Match" header to support optimistic locking.
 *
 * @param {any} body - The data to be updated (will be stringified to JSON).
 * @returns {RequestInit} The request options for a PUT request.
 */
// eslint-disable-next-line
export function putConfig(body: any): RequestInit {
  const headers = getHeaders();
  if (body.version) {
    headers.append("If-Match", body.version);
  }
  return {
    method: "PUT",
    body: body ? JSON.stringify(body) : undefined,
    headers,
    mode: "cors",
    credentials: "same-origin",
    redirect: "manual",
  };
}

/**
 * Returns a default PATCH configuration for the fetch API.
 *
 * Similar to `putConfig`, it includes an "If-Match" header if a version
 * property is present in the body.
 *
 * @param {any} body - The data to be patched (will be stringified to JSON).
 * @returns {RequestInit} The request options for a PATCH request.
 */
// eslint-disable-next-line
export function patchConfig(body: any): RequestInit {
  const headers = getHeaders();
  if (body.version !== undefined) {
    headers.append("If-Match", body.version);
  }
  return {
    method: "PATCH",
    body: body ? JSON.stringify(body) : undefined,
    headers,
    mode: "cors",
    credentials: "same-origin",
    redirect: "manual",
  };
}

/**
 * Centralized handler for HTTP responses to ensure consistent error and success behavior.
 *
 * Functional Overview:
 * 1.  **System Status Sync**: For every error, it fetches the current system status (e.g., to detect Maintenance Mode).
 * 2.  **Access Control (403)**:
 *     - If the system is in Maintenance Mode (LOCKED), it displays the specific maintenance message.
 *     - Otherwise, it displays a generic permission error.
 * 3.  **Redirect Handling**: Handles opaque redirects by reloading the page.
 * 4.  **Client Errors (4xx)**: Attempts to read the response body for a server-provided error message and displays it.
 * 5.  **Server Errors (5xx)**: Displays a generic server error message.
 * 6.  **Success Handling**: If `isAction` is true, displays a success snackbar message.
 *
 * @param response - The fetch Response object to be processed.
 * @param isAction - If true, triggers snackbar notifications for both success and errors.
 * @param successMessage - The message to display on successful completion (only if isAction is true).
 * @param suppressSnackbar - If true, prevents showing snackbar messages for errors.
 * @param errorMessage - Default fallback message for errors.
 * @throws {ApiError} Throws an ApiError if the response status is not OK (2xx).
 * @return Resolves when processing is complete.
 */
export async function defaultResponseHandler(
  response: Response,
  isAction = false,
  successMessage?: string,
  suppressSnackbar = false,
  errorMessage = "Es ist ein unbekannter Fehler aufgetreten."
): Promise<void> {
  if (!response.ok) {
    const appStore = useAppStore();

    // 1. Sync system status (e.g. to get current maintenanceMessage)
    await appStore.fetchSystemStatus();

    // 2. Handle Forbidden (403) - Potential Maintenance Mode
    if (response.status === 403) {
      let message =
        "Sie haben nicht die nötigen Rechte um diese Aktion durchzuführen.";

      try {
        const clone = response.clone();
        const contentType = clone.headers.get("content-type");
        if (contentType && contentType.includes("application/json")) {
          const json = await clone.json();
          if (json.error === "MAINTENANCE_MODE_ACTIVE" || appStore.isLocked) {
            message = `Anwendung im Wartungsmodus: ${appStore.maintenanceMessage || "Bitte versuchen Sie es später erneut."}`;
          }
        } else {
          const bodyText = await clone.text();
          if (bodyText === "MAINTENANCE_MODE_ACTIVE" || appStore.isLocked) {
            message = `Anwendung im Wartungsmodus: ${appStore.maintenanceMessage || "Bitte versuchen Sie es später erneut."}`;
          }
        }
      } catch (e) {
        console.debug(
          "Could not determine detailed 403 reason, falling back to default.",
          e
        );
      }

      if (isAction) {
        useSnackbarStore().showMessage({
          message: message,
          level: STATUS_INDICATORS.ERROR,
        });
      }
      throw new ApiError({
        level: STATUS_INDICATORS.ERROR,
        message: message,
      });
    }

    // 3. Handle Service Unavailable (503)
    if (response.status === 503) {
      const message = `Anwendung im Wartungsmodus: ${appStore.maintenanceMessage || "Bitte versuchen Sie es später erneut."}`;

      if (isAction) {
        useSnackbarStore().showMessage({
          message: message,
          level: STATUS_INDICATORS.ERROR,
        });
      }
      throw new ApiError({
        level: STATUS_INDICATORS.ERROR,
        message: message,
      });
    }

    // 4. Handle Redirection
    if (response.type === "opaqueredirect") {
      location.reload();
      return;
    }

    // 5. Handle Client Input Errors (4xx) - Read body correctly via await
    if (isStatusInput(response)) {
      let message = errorMessage;
      try {
        const contentType = response.headers.get("content-type");
        if (contentType && contentType.includes("application/json")) {
          const json = await response.json();
          message = json.message || json.error || JSON.stringify(json);
        } else {
          const bodyText = await response.text();
          if (bodyText === "MAINTENANCE_MODE_ACTIVE") {
            message = `Anwendung im Wartungsmodus: ${appStore.maintenanceMessage || "Bitte versuchen Sie es später erneut."}`;
          } else {
            message = bodyText || errorMessage;
          }
        }
      } catch (e) {
        console.error("Could not read error response body", e);
      }

      if (!suppressSnackbar) {
        useSnackbarStore().showMessage({
          message: message,
          level: STATUS_INDICATORS.ERROR,
        });
      }
      throw new ApiError({
        message: message,
        level: STATUS_INDICATORS.ERROR,
      });
    }

    // 6. Handle Server Errors (5xx)
    if (isStatusServer(response)) {
      if (!suppressSnackbar) {
        useSnackbarStore().showMessage({
          message:
            "Serverfehler. Bitte versuchen Sie es später erneut, oder wenden Sie sich an die Administration.",
          level: STATUS_INDICATORS.ERROR,
        });
      }
      throw new ApiError({
        message: errorMessage,
        level: STATUS_INDICATORS.ERROR,
      });
    }

    // 7. Fallback for any other error
    if (!suppressSnackbar) {
      useSnackbarStore().showMessage({
        message: errorMessage,
        level: STATUS_INDICATORS.WARNING,
      });
    }
    throw new ApiError({
      level: STATUS_INDICATORS.WARNING,
      message: errorMessage,
    });
  } else if (response.ok && isAction) {
    // Handle Success
    useSnackbarStore().showMessage({
      message: successMessage,
      level: STATUS_INDICATORS.SUCCESS,
    });
  }
}

/**
 * Checks if the response status code indicates a server-side error (500-599).
 *
 * @param {Response} response - The response object to check.
 * @returns {boolean} True if it is a server error.
 */
function isStatusServer(response: Response): boolean {
  return response.status >= 500 && response.status <= 599;
}

/**
 * Checks if the response status code indicates a client-side input error (400-499).
 *
 * @param {Response} response - The response object to check.
 * @returns {boolean} True if it is an input error.
 */
function isStatusInput(response: Response): boolean {
  return response.status >= 400 && response.status <= 499;
}

/**
 * Default catch handler for service requests to ensure rejected promises are wrapped in an ApiError.
 *
 * * @param error - The caught error object.
 *  * @param errorMessage - The message to be included in the ApiError.
 *  * @throws {ApiError} Always throws an ApiError.
 *  */
export function defaultCatchHandler(
  error: Error,
  errorMessage = "Es ist ein unbekannter Fehler aufgetreten."
): PromiseLike<never> {
  throw new ApiError({
    level: STATUS_INDICATORS.WARNING,
    message: errorMessage,
  });
}

/**
 * Constructs the default Headers object for requests.
 * Includes Content-Type and retrieves the XSRF token from cookies if available.
 *
 * @returns {Headers} The initialized Headers object.
 */
function getHeaders(): Headers {
  const headers = new Headers({
    "Content-Type": "application/json",
  });
  const csrfCookie = getXSRFToken();
  if (csrfCookie !== "") {
    headers.append("X-XSRF-TOKEN", csrfCookie);
  }
  return headers;
}

/**
 * Retrieves the XSRF-TOKEN from the document cookies.
 *
 * @returns {string} The token string, or an empty string if not found.
 */
function getXSRFToken(): string {
  const help = document.cookie.match(
    "(^|;)\\s*" + "XSRF-TOKEN" + "\\s*=\\s*([^;]+)"
  );
  return (help ? help.pop() : "") as string;
}
