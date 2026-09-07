import axios from 'axios';

// Single Axios instance configured for API requests.
// Base URL defaults to VITE_API_BASE_URL environment variable or fallback REST endpoint.
//
// timeout is 45s, not the usual few seconds: on a free-tier host (Render) the backend
// spins its container down after idling and can take 30-60s to wake up on the next
// request. A short timeout aborts client-side before that first response ever arrives
// (XHR reports it as a plain networkless failure, status 0) even though the request
// keeps running server-side and would have succeeded - which is exactly why a *second*,
// unrelated request right after often looks fine: it hits an already-warm backend.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 45000,
});

// Request interceptor to attach bearer auth token if available
api.interceptors.request.use(
  (config) => {
    const token = sessionStorage.getItem('lm_guard_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: retries a GET exactly once on a connection-level failure (no
// response at all - timeout, or the backend's container still starting up), then
// normalizes whatever error survives into the shape callers already expect. Only GET is
// retried: it's the only method safe to repeat blind, since a POST/PUT/PATCH/DELETE that
// actually reached the server before the client gave up must never be resent.
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config;
    const isRetryableNetworkFailure = !error.response;
    if (config && config.method === 'get' && isRetryableNetworkFailure && !config.__retried) {
      config.__retried = true;
      try {
        return await api(config);
      } catch (retryError) {
        error = retryError;
      }
    }

    const customError = {
      message: error.response?.data?.message || error.message || 'An unexpected API error occurred',
      status: error.response?.status,
    };
    return Promise.reject(customError);
  }
);

export default api;
