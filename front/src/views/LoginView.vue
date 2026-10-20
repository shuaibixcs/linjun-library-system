<script setup>
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../stores/auth";
import AppIcon from "../components/AppIcon.vue";
const router = useRouter(),
  auth = useAuth(),
  busy = ref(false);
const form = reactive({ username: "", password: "" });
async function submit() {
  if (!form.username || !form.password) return;
  busy.value = true;
  try {
    await auth.login(form);
    router.push("/dashboard");
  } catch {
  } finally {
    busy.value = false;
  }
}
function demo(role) {
  form.username = role;
  form.password = "Library123!";
}
</script>
<template>
  <div class="login-page">
    <section class="login-story">
      <div class="brand">
        <div class="brand-mark"><AppIcon name="BookOpen" :size="26" /></div>
        <div>林间图书馆<small>LINJUN LIBRARY</small></div>
      </div>
      <div class="story-body">
        <span class="eyebrow">A QUIET PLACE TO GROW</span>
        <h1>在书页之间，<br />遇见更大的世界。</h1>
        <p>
          一本好书，一处安静的角落，一次有趣的相遇。<br />你的校园阅读生活，从这里开始。
        </p>
        <div class="book-art">
          <div class="art-sun"></div>
          <div class="art-book b-one">READ<br /><small>AND GROW</small></div>
          <div class="art-book b-two">知<br />识<br />的<br />形<br />状</div>
          <div class="art-book b-three">THE<br />OPEN<br />WORLD</div>
          <div class="art-leaf">❧</div>
          <div class="art-shelf"></div>
        </div>
      </div>
      <span class="login-foot">让知识流动，让灵感发生。</span>
    </section>
    <section class="login-form-wrap">
      <div class="login-form">
        <span class="eyebrow">WELCOME BACK</span>
        <h2>欢迎来到林间</h2>
        <p class="muted">登录账号，开启今天的阅读与探索。</p>
        <el-form
          :model="form"
          label-position="top"
          size="large"
          @submit.prevent="submit"
          ><el-form-item label="登录账号"
            ><el-input
              v-model="form.username"
              placeholder="请输入学号或管理员账号"
              autocomplete="username" /></el-form-item
          ><el-form-item label="登录密码"
            ><el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              @keyup.enter="submit" /></el-form-item
          ><el-button
            type="primary"
            class="login-submit"
            :loading="busy"
            @click="submit"
            >登录图书馆 <AppIcon name="ArrowRight" :size="18" /></el-button
        ></el-form>
        <div class="demo-accounts">
          <span>课程演示账号 · 点击填入</span>
          <div>
            <button @click="demo('superadmin')">超级管理员</button
            ><button @click="demo('admin')">图书馆管理员</button
            ><button @click="demo('2026001')">学生账号</button>
          </div>
          <small>演示初始密码：Library123!</small>
        </div>
        <p class="login-help">
          <AppIcon name="ShieldCheck" :size="15" />
          账号由图书馆统一管理，如需帮助请联系管理员
        </p>
      </div>
    </section>
  </div>
</template>
