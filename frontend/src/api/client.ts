import axios from 'axios';
import { useAuthStore } from '../auth/useAuthStore';
import { redirectToLogin } from './navigation';

export const apiClient = axios.create({
  baseURL: '/api',
});

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url: string = error.config?.url ?? '';
    const isLoginRequest = url.endsWith('/auth/login');
    if (status === 401 && !isLoginRequest) {
      useAuthStore.getState().logout();
      redirectToLogin();
    }
    return Promise.reject(error);
  }
);
