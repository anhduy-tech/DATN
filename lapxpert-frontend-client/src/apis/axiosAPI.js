import axios from "axios";

const DEFAULT_TIMEOUT = 10000;

// Use environment variable for baseURL, with a fallback for local development
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

const commonConfig = {
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: DEFAULT_TIMEOUT,
  withCredentials: false
};

export const publicApi = axios.create({
  ...commonConfig,
  baseURL: commonConfig.baseURL
});

export const privateApi = axios.create({
  ...commonConfig,
  baseURL: `${commonConfig.baseURL}/v2`
});

// Token validation utility
const isTokenExpired = (token) => {
  if (!token) return true;

  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const currentTime = Date.now() / 1000;
    return payload.exp < currentTime;
  } catch (error) {
    console.warn("Failed to parse token:", error);
    return true;
  }
};

// Authentication cleanup utility
const clearAuthenticationState = () => {
  localStorage.removeItem("token");
  localStorage.removeItem("vaiTro");
  localStorage.removeItem("nguoiDung");
  localStorage.removeItem("lastTokenValidation"); // Clear this as well
};

// Modified: Removed automatic redirect
const performAutomaticLogout = (reason = "Session expired") => {
  console.warn(`Automatic logout triggered: ${reason}. Clearing authentication state.`);
  clearAuthenticationState();
  // Removed: window.location.href = "/login?expired=true";
};

privateApi.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");

    if (token) {
      // Check if token is expired before making request
      if (isTokenExpired(token)) {
        // No longer perform automatic logout here, just warn and proceed without token
        console.warn("Token expired, proceeding without token for this request.");
        // We don't set Authorization header if token is expired
      } else {
        config.headers.Authorization = `Bearer ${token}`;
      }
    } else {
      console.warn("No token found, proceeding without token for this request.");
      // No token, so no Authorization header
    }
    return config;
  },
  (error) => Promise.reject(error)
);

const handleError = (error) => {
  let errorMessage = 'An unexpected error occurred';

  if (error.response) {
    errorMessage = error.response.data?.message || error.response.statusText;

    // Modified: No longer perform automatic logout on 401/403
    if (error.response.status === 401 || error.response.status === 403) {
      console.warn(`Authentication/Authorization error (${error.response.status}):`, errorMessage);
      // We still clear state, but don't redirect
      // performAutomaticLogout("Authentication/Authorization failed");
    }
  } else if (error.request) {
    errorMessage = error.code === 'ECONNABORTED'
      ? 'Request timeout'
      : 'Network Error';
  }

  console.error("API Error:", errorMessage);
  return Promise.reject({
    message: errorMessage,
    status: error.response?.status,
    code: error.code,
    originalError: error
  });
};

[publicApi, privateApi].forEach(instance => {
  instance.interceptors.response.use(
    response => response,
    handleError
  );
});

// Export utilities for use in other modules
export { isTokenExpired, clearAuthenticationState, performAutomaticLogout };

export default {
  public: publicApi,
  private: privateApi
};
