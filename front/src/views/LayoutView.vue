<script setup>
import { computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { useAuth } from "../stores/auth";
import { modules } from "../config/modules";
import { act } from "../api";
import AppIcon from "../components/AppIcon.vue";
const auth = useAuth(),
  route = useRoute(),
  router = useRouter();
const groups = computed(() =>
  auth.isAdmin
    ? [
        {
          label: "工作空间",
          items: [
            ["dashboard", "概览", "LayoutDashboard"],
            ["books", "图书馆藏", "BookOpen"],
            ["categories", "图书分类", "Folders"],
            ["acquisitions", "入库记录", "PackagePlus"],
          ],
        },
        {
          label: "图书馆服务",
          items: [
            ["loans", "借阅流通", "ArrowLeftRight"],
            ["fines", "逾期罚金", "Receipt"],
            ["resources", "学习空间", "Armchair"],
            ["reservations", "预约管理", "CalendarCheck"],
            ["activities", "阅读活动", "Sparkles"],
            ["registrations", "活动报名", "Tickets"],
            ["announcements", "馆内公告", "Megaphone"],
          ],
        },
        {
          label: "人员与运营",
          items: [
            ["students", "学生管理", "GraduationCap"],
            ["blacklist", "黑名单", "UserRoundX"],
            ["visits", "入馆登记", "DoorOpen"],
            ...(auth.isSuper
              ? [["administrators", "管理员账号", "ShieldCheck"]]
              : []),
          ],
        },
      ]
    : [
        {
          label: "发现图书馆",
          items: [
            ["dashboard", "我的首页", "LayoutDashboard"],
            ["books", "图书馆藏", "BookOpen"],
            ["resources", "学习空间", "Armchair"],
            ["activities", "阅读活动", "Sparkles"],
            ["announcements", "馆内公告", "Megaphone"],
          ],
        },
        {
          label: "我的图书馆",
          items: [
            ["loans", "我的借阅", "ArrowLeftRight"],
            ["reservations", "我的预约", "CalendarCheck"],
            ["registrations", "我的报名", "Tickets"],
            ["fines", "我的罚金", "Receipt"],
          ],
        },
      ],
);
const currentTitle = computed(() =>
  route.path === "/dashboard"
    ? "服务概览"
    : route.path === "/profile"
      ? "个人中心"
      : modules[route.params.module]?.title || "图书馆",
);
const roleLabel = computed(() =>
  auth.isSuper ? "超级管理员" : auth.isAdmin ? "图书馆管理员" : "学生读者",
);
const date = new Intl.DateTimeFormat("zh-CN", {
  month: "long",
  day: "numeric",
  weekday: "long",
}).format(new Date());
onMounted(() => auth.refresh().catch(() => {}));
async function logout() {
  try {
    await ElMessageBox.confirm("确定退出当前账号吗？", "退出登录", {
      type: "info",
    });
    await auth.logout();
    router.push("/login");
  } catch {}
}
async function enter() {
  try {
    await ElMessageBox.confirm("确认记录本次入馆？", "入馆登记");
    await act("visit", {});
    ElMessage.success("入馆登记成功，祝你阅读愉快");
  } catch {}
}
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar">
      <router-link class="brand" to="/dashboard"
        ><div class="brand-mark"><AppIcon name="BookOpen" :size="25" /></div>
        <div>林间图书馆<small>LINJUN LIBRARY</small></div></router-link
      >
      <div class="workspace-label">
        <span class="live-dot"></span
        >{{ auth.isAdmin ? "图书馆管理工作台" : "校园阅读服务平台" }}
      </div>
      <nav>
        <div v-for="group in groups" :key="group.label" class="nav-group">
          <span class="nav-group-label">{{ group.label }}</span
          ><router-link
            v-for="item in group.items"
            :key="item[0]"
            :to="'/' + item[0]"
            class="nav-item"
            ><AppIcon :name="item[2]" :size="18" /><span>{{ item[1] }}</span
            ><span
              v-if="route.path === '/' + item[0]"
              class="nav-active-dot"
            ></span
          ></router-link>
        </div>
      </nav>
      <div class="sidebar-bottom">
        <div class="open-hours">
          <AppIcon name="Clock3" :size="18" />
          <div>今天也是阅读的好日子<small>开放时间 08:00 — 22:00</small></div>
        </div>
        <button class="nav-item" @click="logout">
          <AppIcon name="LogOut" :size="18" /><span>退出登录</span>
        </button>
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumb">
          <span>林间图书馆</span><span>/</span
          ><strong>{{ currentTitle }}</strong>
        </div>
        <div class="topbar-right">
          <span class="top-date">{{ date }}</span
          ><el-button v-if="!auth.isAdmin" text @click="enter"
            ><AppIcon name="ScanLine" :size="17" /> 入馆登记</el-button
          ><router-link
            to="/announcements"
            class="notification"
            aria-label="查看公告"
            ><AppIcon name="Bell" :size="20" /><i></i></router-link
          ><router-link to="/profile" class="user-profile"
            ><div class="avatar">
              {{ auth.user?.name?.slice(-2, -1) || "林" }}
            </div>
            <div>
              <strong>{{ auth.user?.name }}</strong
              ><small>{{ roleLabel }}</small>
            </div>
            <AppIcon name="ChevronDown" :size="15"
          /></router-link>
        </div>
      </header>
      <main class="page-content"><router-view :key="route.path" /></main>
      <footer class="page-footer">
        <span>林间图书馆 · 让阅读成为日常</span
        ><span>LIBRARY MANAGEMENT SYSTEM</span>
      </footer>
    </div>
  </div>
</template>
