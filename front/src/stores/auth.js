import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { api } from "../api";
export const useAuth = defineStore("auth", () => {
  const user = ref(
    JSON.parse(sessionStorage.getItem("library-user") || "null"),
  );
  const isAdmin = computed(() => user.value && user.value.role !== "STUDENT");
  const isSuper = computed(() => user.value?.role === "SUPER_ADMIN");
  async function login(values) {
    const r = await api.post("/auth/login", values);
    sessionStorage.setItem("library-token", r.token);
    sessionStorage.setItem("library-user", JSON.stringify(r.user));
    user.value = r.user;
  }
  async function refresh() {
    user.value = await api.get("/auth/me");
    sessionStorage.setItem("library-user", JSON.stringify(user.value));
  }
  async function logout() {
    try {
      await api.post("/auth/logout");
    } finally {
      sessionStorage.clear();
      user.value = null;
    }
  }
  return { user, isAdmin, isSuper, login, refresh, logout };
});
