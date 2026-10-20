import { createRouter, createWebHashHistory } from "vue-router";
import LoginView from "../views/LoginView.vue";
import LayoutView from "../views/LayoutView.vue";
import DashboardView from "../views/DashboardView.vue";
import CatalogView from "../views/CatalogView.vue";
import ProfileView from "../views/ProfileView.vue";
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/login", component: LoginView },
    {
      path: "/",
      component: LayoutView,
      children: [
        { path: "", redirect: "/dashboard" },
        { path: "dashboard", component: DashboardView },
        { path: "profile", component: ProfileView },
        { path: ":module", component: CatalogView },
      ],
    },
    { path: "/:pathMatch(.*)*", redirect: "/dashboard" },
  ],
});
router.beforeEach((to) => {
  if (!sessionStorage.getItem("library-token") && to.path !== "/login")
    return "/login";
  if (sessionStorage.getItem("library-token") && to.path === "/login")
    return "/dashboard";
});
export default router;
