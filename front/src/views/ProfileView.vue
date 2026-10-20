<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../stores/auth";
import { api } from "../api";
import { ElMessage } from "element-plus";
import { labels } from "../config/modules";
import AppIcon from "../components/AppIcon.vue";
const auth = useAuth(),
  router = useRouter(),
  form = reactive({ oldPassword: "", newPassword: "", confirm: "" }),
  busy = ref(false);
async function save() {
  if (form.newPassword !== form.confirm) {
    ElMessage.warning("两次输入的新密码不一致");
    return;
  }
  if (form.newPassword.length < 8) {
    ElMessage.warning("新密码至少8位");
    return;
  }
  busy.value = true;
  try {
    await api.post("/auth/password", form);
    sessionStorage.clear();
    auth.user = null;
    ElMessage.success("密码已修改，请重新登录");
    router.push("/login");
  } catch {
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">MY ACCOUNT</span>
      <h1>个人中心<span class="heading-dot">.</span></h1>
      <p>查看个人资料，管理账号密码。</p>
    </div>
  </div>
  <div class="profile-grid">
    <section class="panel">
      <div class="profile-intro">
        <div class="avatar large">{{ auth.user?.name?.slice(0, 1) }}</div>
        <h2>{{ auth.user?.name }}</h2>
        <el-tag effect="plain">{{ labels[auth.user?.role] }}</el-tag>
      </div>
      <el-descriptions :column="1" border
        ><el-descriptions-item label="登录账号">{{
          auth.user?.username
        }}</el-descriptions-item
        ><el-descriptions-item label="学号">{{
          auth.user?.studentNo || "—"
        }}</el-descriptions-item
        ><el-descriptions-item label="院系">{{
          auth.user?.department || "—"
        }}</el-descriptions-item
        ><el-descriptions-item label="联系方式">{{
          auth.user?.phone || "—"
        }}</el-descriptions-item></el-descriptions
      >
    </section>
    <section class="panel">
      <div class="section-title">
        <h3><AppIcon name="LockKeyhole" :size="19" /> 修改密码</h3>
      </div>
      <p class="muted">修改成功后，账号的所有登录会话将退出。</p>
      <el-form label-position="top" @submit.prevent="save"
        ><el-form-item label="当前密码"
          ><el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            autocomplete="current-password" /></el-form-item
        ><el-form-item label="新密码（8到72位）"
          ><el-input
            v-model="form.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            maxlength="72" /></el-form-item
        ><el-form-item label="确认新密码"
          ><el-input
            v-model="form.confirm"
            type="password"
            show-password
            autocomplete="new-password" /></el-form-item
        ><el-button type="primary" :loading="busy" @click="save"
          >保存新密码</el-button
        ></el-form
      >
    </section>
  </div>
</template>
