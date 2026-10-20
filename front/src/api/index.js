import axios from "axios";
import { ElMessage } from "element-plus";
export const api = axios.create({ baseURL: "/api", timeout: 20000 });
api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem("library-token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
api.interceptors.response.use(
  (response) =>
    response.config.responseType === "blob"
      ? response.data
      : response.data.data,
  (error) => {
    const message = error.response?.data?.message || "连接服务失败，请稍后重试";
    ElMessage.error(message);
    if (
      error.response?.status === 401 &&
      !error.config?.url?.includes("/auth/login")
    ) {
      sessionStorage.removeItem("library-token");
      sessionStorage.removeItem("library-user");
      window.location.hash = "#/login";
    }
    return Promise.reject(error);
  },
);
export const listData = (module, params) =>
  api.get(`/data/${module}`, { params });
export const act = (name, values) => api.post(`/actions/${name}`, values);
