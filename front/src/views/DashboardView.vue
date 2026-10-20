<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from "vue";
import { useRouter } from "vue-router";
import * as echarts from "echarts/core";
import { LineChart, PieChart } from "echarts/charts";
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
} from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import { api } from "../api";
import { useAuth } from "../stores/auth";
import AppIcon from "../components/AppIcon.vue";
echarts.use([
  LineChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  CanvasRenderer,
]);
const auth = useAuth(),
  router = useRouter(),
  data = ref(null),
  loading = ref(true),
  line = ref(),
  pie = ref(),
  charts = [];
const stats = computed(() =>
  auth.isAdmin
    ? [
        {
          label: "在馆图书种类",
          value: data.value?.books,
          unit: "种",
          icon: "BookOpen",
          tone: "green",
          note: `共 ${data.value?.copies || 0} 本实体馆藏`,
        },
        {
          label: "当前借阅",
          value: data.value?.activeLoans,
          unit: "本",
          icon: "ArrowLeftRight",
          tone: "blue",
          note: `${data.value?.overdueLoans || 0} 本已逾期，待跟进`,
        },
        {
          label: "今日入馆人次",
          value: data.value?.todayVisits,
          unit: "次",
          icon: "DoorOpen",
          tone: "orange",
          note: `${data.value?.todayUniqueVisitors || 0} 位独立访客`,
        },
        {
          label: "待审核预约",
          value: data.value?.pendingReservations,
          unit: "项",
          icon: "CalendarClock",
          tone: "purple",
          note: "查看并处理空间申请",
        },
      ]
    : [
        {
          label: "我的在借图书",
          value: data.value?.activeLoans,
          unit: "本",
          icon: "BookOpen",
          tone: "green",
          note: `最多可同时借阅 ${data.value?.rules?.maxLoans || 5} 本`,
        },
        {
          label: "逾期待归还",
          value: data.value?.overdueLoans,
          unit: "本",
          icon: "Clock3",
          tone: "orange",
          note: "及时归还，让好书流转",
        },
        {
          label: "有效空间预约",
          value: data.value?.activeReservations,
          unit: "项",
          icon: "Armchair",
          tone: "blue",
          note: `累计预约违约 ${data.value?.violations || 0} 次`,
        },
        {
          label: "已报名活动",
          value: data.value?.registrations,
          unit: "场",
          icon: "Sparkles",
          tone: "purple",
          note: "期待每一次有趣的相遇",
        },
      ],
);
const shortcuts = computed(() =>
  auth.isAdmin
    ? [
        ["登记借书", "录入学号与馆藏编号", "ArrowUpRight", "/loans"],
        ["空间审核", "处理待审核的使用申请", "CalendarCheck", "/reservations"],
        ["新增馆藏", "维护书目与实体库存", "BookPlus", "/books"],
        ["发布公告", "传递图书馆最新消息", "Megaphone", "/announcements"],
      ]
    : [
        ["发现好书", "在馆藏中寻找下一本", "BookOpen", "/books"],
        ["预约空间", "为专注找到合适位置", "Armchair", "/resources"],
        ["参与活动", "与有趣的人相遇", "Sparkles", "/activities"],
        ["我的借阅", "查看借阅与归还日期", "ArrowLeftRight", "/loans"],
      ],
);
async function load() {
  loading.value = true;
  try {
    data.value = await api.get("/dashboard");
    await nextTick();
    renderCharts();
  } catch {
  } finally {
    loading.value = false;
  }
}
function renderCharts() {
  charts.forEach((c) => c.dispose());
  charts.length = 0;
  if (!auth.isAdmin || !line.value) return;
  const trend = echarts.init(line.value);
  trend.setOption({
    tooltip: { trigger: "axis" },
    grid: { left: 42, right: 20, top: 20, bottom: 28 },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: data.value.monthlyLoans.map((m) => m.name.slice(5) + "月"),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: "#8b9591" },
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      splitLine: { lineStyle: { color: "#f0f2ef", type: "dashed" } },
      axisLabel: { color: "#9aa39f" },
    },
    series: [
      {
        type: "line",
        smooth: true,
        data: data.value.monthlyLoans.map((m) => m.value),
        symbol: "circle",
        symbolSize: 7,
        itemStyle: { color: "#3b8b73" },
        lineStyle: { width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: "rgba(81,151,120,.22)" },
            { offset: 1, color: "rgba(81,151,120,0)" },
          ]),
        },
      },
    ],
  });
  charts.push(trend);
  const donut = echarts.init(pie.value);
  donut.setOption({
    color: ["#3b806c", "#91b8a0", "#c1d3b7", "#e9c596", "#b5bfd1", "#dfb6ab"],
    tooltip: { trigger: "item" },
    legend: {
      bottom: 0,
      icon: "circle",
      itemWidth: 7,
      itemHeight: 7,
      textStyle: { fontSize: 11, color: "#6e7b74" },
    },
    series: [
      {
        type: "pie",
        radius: ["49%", "73%"],
        center: ["50%", "43%"],
        avoidLabelOverlap: true,
        label: { show: false },
        itemStyle: { borderRadius: 5, borderColor: "#fff", borderWidth: 4 },
        data: data.value.categoryShare,
      },
    ],
  });
  charts.push(donut);
}
function resize() {
  charts.forEach((c) => c.resize());
}
onMounted(() => {
  load();
  window.addEventListener("resize", resize);
});
onBeforeUnmount(() => {
  charts.forEach((c) => c.dispose());
  window.removeEventListener("resize", resize);
});
</script>
<template>
  <div v-loading="loading">
    <div class="page-heading">
      <div>
        <span class="eyebrow">{{
          auth.isAdmin ? "LIBRARY OVERVIEW" : "YOUR LIBRARY"
        }}</span>
        <h1>
          {{ auth.isAdmin ? "服务概览" : "我的图书馆"
          }}<span class="heading-dot">.</span>
        </h1>
        <p>
          {{
            auth.isAdmin
              ? "每一次借阅与相遇，都在这里发生。"
              : "今天，给自己留一点阅读的时间。"
          }}
        </p>
      </div>
      <el-button class="refresh-button" @click="load"
        ><AppIcon name="RefreshCw" :size="16" /> 刷新数据</el-button
      >
    </div>
    <section class="welcome-banner">
      <div>
        <span class="welcome-tag"
          ><span class="live-dot"></span> 阅读，让成长有迹可循</span
        >
        <h2>你好，{{ auth.user?.name }}<span class="greeting-sun">☀</span></h2>
        <p>
          {{
            auth.isAdmin
              ? "新的一天，继续为每一位读者创造更好的阅读体验。"
              : "探索一本好书，预约一处空间，遇见新的灵感。"
          }}
        </p>
        <button @click="router.push(auth.isAdmin ? '/reservations' : '/books')">
          {{ auth.isAdmin ? "查看待办事项" : "开始探索馆藏" }}
          <AppIcon name="ArrowRight" :size="17" />
        </button>
      </div>
      <div class="banner-art" aria-hidden="true">
        <div class="banner-orbit"></div>
        <div class="tiny-plant"><span></span><i></i><b></b></div>
        <div class="banner-book book-sage">
          READ<br /><small>TO DISCOVER</small>
        </div>
        <div class="banner-book book-cream">知<br />行</div>
        <div class="banner-book book-peach">A NEW<br />CHAPTER</div>
        <div class="banner-base"></div>
      </div>
    </section>
    <el-alert
      v-if="data?.blacklisted"
      title="当前账号处于黑名单，借阅、预约及入馆服务暂停。如有疑问请联系管理员。"
      type="warning"
      :closable="false"
      show-icon
      class="mb20"
    />
    <section class="stat-grid">
      <article v-for="s in stats" :key="s.label" class="stat-card">
        <div class="stat-top">
          <span>{{ s.label }}</span>
          <div class="stat-icon" :class="s.tone">
            <AppIcon :name="s.icon" :size="20" />
          </div>
        </div>
        <div class="stat-value">
          {{ s.value ?? "—" }}<small>{{ s.unit }}</small>
        </div>
        <div class="stat-note">
          <span class="small-dot" :class="s.tone"></span>{{ s.note }}
        </div>
      </article>
    </section>
    <section class="quick-panel panel">
      <div class="section-title">
        <h3>常用服务</h3>
        <span>QUICK ACCESS</span>
      </div>
      <div class="quick-grid">
        <button v-for="s in shortcuts" :key="s[0]" @click="router.push(s[3])">
          <div class="quick-icon"><AppIcon :name="s[2]" :size="23" /></div>
          <div>
            <strong>{{ s[0] }}</strong
            ><small>{{ s[1] }}</small>
          </div>
          <AppIcon name="ChevronRight" :size="16" />
        </button>
      </div>
    </section>
    <section v-if="auth.isAdmin" class="charts-grid">
      <article class="panel">
        <div class="section-title">
          <h3>借阅趋势 <small>近六个月</small></h3>
          <span class="chart-legend"><i></i>借阅量</span>
        </div>
        <div ref="line" class="chart-line"></div>
      </article>
      <article class="panel">
        <div class="section-title">
          <h3>热门分类</h3>
          <span>借阅占比</span>
        </div>
        <div ref="pie" class="chart-pie"></div>
      </article>
    </section>
    <section class="bottom-grid">
      <article class="panel announcement-panel">
        <div class="section-title">
          <h3>馆内公告</h3>
          <router-link to="/announcements"
            >查看全部 <AppIcon name="ArrowUpRight" :size="14"
          /></router-link>
        </div>
        <button
          v-for="n in data?.announcements"
          :key="n.id"
          class="notice-row"
          @click="router.push('/announcements')"
        >
          <div class="notice-symbol" :class="{ pinned: n.pinned }">
            <AppIcon :name="n.pinned ? 'Pin' : 'FileText'" :size="17" />
          </div>
          <div>
            <strong>{{ n.title }}</strong>
            <p>{{ n.content }}</p>
          </div>
          <time>{{ n.createdAt?.slice(5, 10) }}</time></button
        ><el-empty
          v-if="data && !data.announcements?.length"
          description="暂无公告"
        />
      </article>
      <article v-if="auth.isAdmin" class="panel ranking-panel">
        <div class="section-title">
          <h3>热门借阅</h3>
          <span>TOP 5</span>
        </div>
        <div v-for="(b, i) in data?.topBooks" :key="b.name" class="rank-row">
          <span class="rank-no" :class="{ 'top-three': i < 3 }">{{
            String(i + 1).padStart(2, "0")
          }}</span
          ><strong>{{ b.name }}</strong
          ><span>{{ b.value }} <small>次</small></span>
        </div>
        <el-empty
          v-if="data && !data.topBooks?.length"
          description="暂无借阅记录"
        />
      </article>
      <article v-else class="panel reading-note">
        <AppIcon name="Quote" :size="32" />
        <h3>阅读是一座随身携带的避难所。</h3>
        <p>
          借阅期限 {{ data?.rules?.borrowDays }} 天<br />座位预约开始后
          {{ data?.rules?.checkinGraceMinutes }} 分钟内签到<br />储物柜最长使用
          {{ data?.rules?.lockerMaxDays }} 天
        </p>
        <span>MAKE ROOM FOR READING.</span>
      </article>
    </section>
  </div>
</template>
