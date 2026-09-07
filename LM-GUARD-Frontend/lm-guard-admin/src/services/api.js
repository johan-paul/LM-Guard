import axios from 'axios';

// Single Axios instance configured for API requests.
// Base URL defaults to VITE_API_BASE_URL environment variable or fallback REST endpoint.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
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

// Response interceptor for unified error formatting
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const customError = {
      message: error.response?.data?.message || error.message || 'An unexpected API error occurred',
      status: error.response?.status,
    };
    return Promise.reject(customError);
  }
);

export default api;
