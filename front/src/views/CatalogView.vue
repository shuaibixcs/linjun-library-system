<script setup>
import { ref, reactive, computed, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { api, listData, act } from "../api";
import { useAuth } from "../stores/auth";
import { modules, labels, options as enumOptions } from "../config/modules";
import AppIcon from "../components/AppIcon.vue";
const route = useRoute(),
  router = useRouter(),
  auth = useAuth();
const module = computed(() => route.params.module),
  meta = computed(() => modules[module.value]),
  title = computed(
    () => (!auth.isAdmin && meta.value?.studentTitle) || meta.value?.title,
  );
const rows = ref([]),
  total = ref(0),
  loading = ref(false),
  busy = ref(false),
  lookup = ref({}),
  mode = ref("cards");
const filters = reactive({
  q: "",
  page: 1,
  size: 10,
  kind: "",
  categoryId: "",
  status: "",
  due: "",
  date: "",
});
const editVisible = ref(false),
  editingId = ref(null),
  form = reactive({}),
  editFormRef = ref();
const actionVisible = ref(false),
  actionName = ref(""),
  actionTitle = ref(""),
  actionFields = ref([]),
  actionForm = reactive({}),
  actionFormRef = ref(),
  slots = ref([]);
const detailVisible = ref(false),
  detailTitle = ref(""),
  detail = ref(null),
  copyRows = ref([]),
  importInput = ref();
const canEdit = computed(() => auth.isAdmin && meta.value?.editable);
const allowed = computed(
  () =>
    meta.value &&
    (!meta.value.admin || auth.isAdmin) &&
    (!meta.value.super || auth.isSuper),
);
const editableFields = computed(() =>
  (meta.value?.fields || []).filter(
    (f) =>
      (!editingId.value || !f.createOnly) &&
      (f.key !== "equipmentStatus" || form.kind === "SEAT"),
  ),
);
const cards = computed(
  () =>
    ["resources", "activities"].includes(module.value) ||
    (module.value === "books" && !auth.isAdmin && mode.value === "cards") ||
    (module.value === "announcements" && !auth.isAdmin),
);
const statusOptions = computed(() =>
  enumOptions(
    {
      loans: ["BORROWED", "RETURNED", "OVERDUE"],
      reservations: [
        "PENDING",
        "APPROVED",
        "CHECKED_IN",
        "COMPLETED",
        "CANCELLED",
        "EXPIRED",
        "REJECTED",
      ],
      registrations: ["REGISTERED", "CHECKED_IN", "CANCELLED"],
      fines: ["UNPAID", "SETTLED"],
      activities: ["OPEN", "CLOSED", "CANCELLED"],
      resources: ["OPEN", "CLOSED", "MAINTENANCE"],
    }[module.value] || [],
  ),
);
function clearForm(target) {
  for (const k in target) delete target[k];
}
function formOptions(field) {
  if (field.options) return field.options;
  let list = lookup.value[field.source] || [];
  if (field.source === "rooms")
    list = (lookup.value.resources || []).filter(
      (r) => r.kind === "ROOM" && r.status === "OPEN",
    );
  return list.map((x) => ({
    value: x.id,
    label:
      field.source === "students"
        ? `${x.studentNo} · ${x.name}`
        : field.source === "books"
          ? x.title
          : field.source === "categories"
            ? x.name
            : `${x.code} · ${x.name}`,
  }));
}
function display(row, key) {
  const value = row[key];
  if (value === null || value === undefined || value === "") return "—";
  if (["enabled"].includes(key)) return value === 1 ? "启用" : "停用";
  if (key === "active") return value === 1 ? "生效中" : "已移出";
  if (key === "blacklisted") return Number(value) > 0 ? "是" : "否";
  if (key === "occupied") return Number(value) > 0 ? "使用中" : "当前空闲";
  if (key === "pinned") return value === 1 ? "已置顶" : "—";
  if (key === "violation") return value === 1 ? "已记录" : "—";
  if (/At$/.test(key)) return String(value).replace("T", " ").slice(0, 16);
  if (key === "amount") return Number(value).toFixed(2);
  return labels[value] || value;
}
function tone(value) {
  if (["OPEN", "AVAILABLE", "RETURNED", "COMPLETED", "SETTLED"].includes(value))
    return "success";
  if (
    ["PENDING", "APPROVED", "BORROWED", "REGISTERED", "UNPAID"].includes(value)
  )
    return "warning";
  if (["OVERDUE", "REJECTED", "MAINTENANCE", "EXPIRED"].includes(value))
    return "danger";
  return "info";
}
async function load() {
  if (!allowed.value) return;
  loading.value = true;
  try {
    const p = { ...filters };
    if (route.query.activityId) p.activityId = route.query.activityId;
    const result = await listData(module.value, p);
    rows.value = result.records;
    total.value = result.total;
  } catch {
  } finally {
    loading.value = false;
  }
}
async function loadOptions() {
  try {
    lookup.value = await api.get("/options");
  } catch {}
}
async function refresh() {
  await Promise.all([load(), loadOptions()]);
}
function search() {
  filters.page = 1;
  load();
}
function reset() {
  Object.assign(filters, {
    q: "",
    page: 1,
    size: 10,
    kind: "",
    categoryId: "",
    status: "",
    due: "",
    date: "",
  });
  load();
}
function edit(row) {
  clearForm(form);
  editingId.value = row?.id || null;
  for (const f of meta.value.fields || [])
    form[f.key] = row?.[f.key] ?? f.default ?? (f.type === "number" ? 1 : "");
  editVisible.value = true;
  editFormRef.value?.clearValidate();
}
const requiredKeys = {
  books: ["isbn", "title"],
  categories: ["name"],
  students: ["username", "studentNo", "name", "password"],
  administrators: ["username", "name", "password"],
  resources: ["kind", "code", "name", "capacity", "status"],
  activities: [
    "title",
    "resourceId",
    "startAt",
    "endAt",
    "maxPeople",
    "status",
  ],
  announcements: ["title", "content"],
};
function isRequired(key) {
  return (requiredKeys[module.value] || []).includes(key);
}
async function save() {
  try {
    await editFormRef.value.validate();
  } catch {
    return;
  }
  busy.value = true;
  try {
    if (editingId.value)
      await api.put(`/data/${module.value}/${editingId.value}`, form);
    else await api.post(`/data/${module.value}`, form);
    ElMessage.success("保存成功");
    editVisible.value = false;
    await refresh();
  } catch {
  } finally {
    busy.value = false;
  }
}
async function remove(row) {
  try {
    const archive = [
      "books",
      "students",
      "administrators",
      "resources",
    ].includes(module.value);
    await ElMessageBox.confirm(
      archive
        ? "确认停用这条记录？历史业务记录会保留。"
        : "确认删除或取消这条记录？",
      "操作确认",
      { type: "warning" },
    );
    await api.delete(`/data/${module.value}/${row.id}`);
    ElMessage.success("操作成功");
    await refresh();
  } catch {}
}
const field = (key, label, type = "text", extra = {}) => ({
  key,
  label,
  type,
  ...extra,
});
function iso(date) {
  const y = date.getFullYear(),
    m = String(date.getMonth() + 1).padStart(2, "0"),
    d = String(date.getDate()).padStart(2, "0"),
    h = String(date.getHours()).padStart(2, "0"),
    mi = String(date.getMinutes()).padStart(2, "0");
  return `${y}-${m}-${d}T${h}:${mi}:00`;
}
async function openAction(name, row) {
  clearForm(actionForm);
  slots.value = [];
  actionName.value = name;
  const student = field("userId", "学生", "select", {
    source: "students",
    required: true,
  });
  let fields = [];
  let label = "";
  if (name === "acquire") {
    label = "图书入库";
    fields = [
      field("bookId", "图书", "select", { source: "books", required: true }),
      field("quantity", "入库数量", "number", { required: true }),
      field("source", "采购来源"),
    ];
    actionForm.bookId = row?.id;
    actionForm.quantity = 1;
  }
  if (name === "borrow") {
    label = "借书登记";
    fields = [
      student,
      field("copyCode", "馆藏编号", "text", { required: true }),
    ];
  }
  if (name === "return") {
    label = "扫码 / 输入编号还书";
    fields = [field("copyCode", "馆藏编号", "text", { required: true })];
  }
  if (name === "blacklist-add") {
    label = "加入黑名单";
    fields = [
      student,
      field("reason", "违规原因", "textarea", { required: true }),
    ];
    if (row) actionForm.userId = row.id;
  }
  if (name === "reserve") {
    label = `预约${labels[row.kind]}`;
    fields = [
      ...(auth.isAdmin ? [student] : []),
      field("startAt", "开始时间", "datetime", { required: true }),
      field("endAt", "结束时间", "datetime", { required: true }),
      field("purpose", "使用用途", "textarea", {
        required: row.kind === "ROOM",
      }),
      ...(row.kind === "ROOM"
        ? [field("organization", "社团名称（可选）")]
        : []),
    ];
    actionForm.resourceId = row.id;
    const start = new Date();
    start.setDate(start.getDate() + 1);
    start.setHours(8, 0, 0, 0);
    const end = new Date(start);
    if (row.kind === "LOCKER") end.setDate(end.getDate() + 7);
    else end.setHours(row.kind === "SEAT" ? 12 : 10);
    actionForm.startAt = iso(start);
    actionForm.endAt = iso(end);
    try {
      slots.value = await api.get(`/resources/${row.id}/availability`);
    } catch {}
  }
  if (name === "review") {
    label = "预约审核";
    fields = [
      field("status", "审核结果", "select", {
        options: [
          { value: "APPROVED", label: "审核通过" },
          { value: "REJECTED", label: "驳回申请" },
        ],
        required: true,
      }),
      field("reviewNote", "审核备注", "textarea"),
    ];
    actionForm.id = row.id;
    actionForm.status = "APPROVED";
  }
  if (name === "register") {
    label = "活动报名";
    fields = auth.isAdmin ? [student] : [];
    actionForm.activityId = row.id;
  }
  if (name === "visit") {
    label = "入馆登记";
    fields = [
      field("userId", "选择学生（校外访客留空）", "select", {
        source: "students",
      }),
      field("visitorName", "校外访客姓名"),
      field("identityRef", "访客登记编号"),
    ];
  }
  actionTitle.value = label;
  actionFields.value = fields;
  actionVisible.value = true;
  actionFormRef.value?.clearValidate();
}
async function submitAction() {
  try {
    await actionFormRef.value?.validate();
  } catch {
    return;
  }
  busy.value = true;
  try {
    const payload = { ...actionForm };
    if (actionName.value === "visit" && !payload.userId) delete payload.userId;
    await act(actionName.value, payload);
    ElMessage.success("操作成功");
    actionVisible.value = false;
    await refresh();
  } catch {
  } finally {
    busy.value = false;
  }
}
async function quickAction(name, row) {
  const msg = {
    return: "确认归还这本图书？",
    "blacklist-remove": "确认将该学生移出黑名单？",
    "reservation-cancel": "确认取消这条预约？",
    "reservation-checkin": "确认到馆签到？",
    "registration-cancel": "确认取消报名？",
    "activity-checkin": "确认核销该学生的活动入场？",
    "fine-settle": "确认已线下处理这笔罚金？",
  };
  try {
    await ElMessageBox.confirm(msg[name] || "确认执行该操作？", "操作确认");
    const result = await act(name, { id: row.id });
    ElMessage.success(
      name === "return"
        ? `归还成功，逾期${result.overdueDays}天，罚金${Number(result.fine).toFixed(2)}元`
        : "操作成功",
    );
    await refresh();
  } catch {}
}
async function showDetail(row) {
  detail.value = row;
  detailTitle.value =
    module.value === "books" ? row.title : row.title || row.name;
  copyRows.value = [];
  if (module.value === "books") {
    try {
      copyRows.value = (
        await listData("copies", { bookId: row.id, size: 100 })
      ).records;
    } catch {}
  }
  detailVisible.value = true;
}
async function importFile(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  const data = new FormData();
  data.append("file", file);
  busy.value = true;
  try {
    const r = await api.post("/students/import", data);
    ElMessage.success(`成功导入 ${r.imported} 名学生`);
    await refresh();
  } catch {
  } finally {
    event.target.value = "";
    busy.value = false;
  }
}
async function downloadTemplate() {
  try {
    const blob = await api.get("/students/template", { responseType: "blob" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "学生导入模板.xlsx";
    a.click();
    URL.revokeObjectURL(url);
  } catch {}
}
watch(() => route.query, load);
onMounted(refresh);
</script>
<template>
  <el-result
    v-if="!allowed"
    icon="warning"
    title="当前页面暂不可访问"
    sub-title="请从左侧菜单选择可用服务"
    ><template #extra
      ><el-button @click="router.push('/dashboard')"
        >返回首页</el-button
      ></template
    ></el-result
  >
  <div v-else v-loading="loading">
    <div class="page-heading">
      <div>
        <span class="eyebrow">{{
          module === "resources"
            ? "FIND YOUR SPACE"
            : module === "books"
              ? "EXPLORE THE COLLECTION"
              : "LIBRARY SERVICES"
        }}</span>
        <h1>{{ title }}<span class="heading-dot">.</span></h1>
        <p>{{ meta.subtitle }}</p>
      </div>
      <div class="heading-actions">
        <el-button v-if="module === 'students'" @click="downloadTemplate"
          ><AppIcon name="Download" :size="16" /> 导入模板</el-button
        ><el-button
          v-if="module === 'students'"
          :loading="busy"
          @click="importInput.click()"
          ><AppIcon name="Upload" :size="16" /> 批量导入</el-button
        ><input
          ref="importInput"
          type="file"
          accept=".xlsx,.xls"
          hidden
          @change="importFile"
        />
        <el-button
          v-if="module === 'acquisitions'"
          type="primary"
          @click="openAction('acquire')"
          ><AppIcon name="Plus" :size="17" /> 藏书入库</el-button
        >
        <template v-if="module === 'loans' && auth.isAdmin"
          ><el-button @click="openAction('return')"
            ><AppIcon name="ScanLine" :size="17" /> 还书登记</el-button
          ><el-button type="primary" @click="openAction('borrow')"
            ><AppIcon name="Plus" :size="17" /> 借书登记</el-button
          ></template
        >
        <el-button
          v-if="module === 'blacklist'"
          type="primary"
          @click="openAction('blacklist-add')"
          ><AppIcon name="Plus" :size="17" /> 加入黑名单</el-button
        ><el-button
          v-if="module === 'visits'"
          type="primary"
          @click="openAction('visit')"
          ><AppIcon name="Plus" :size="17" /> 入馆登记</el-button
        >
        <el-button v-if="canEdit" type="primary" @click="edit()"
          ><AppIcon name="Plus" :size="17" />
          {{
            module === "announcements"
              ? "发布公告"
              : module === "activities"
                ? "发布活动"
                : module === "resources"
                  ? "新增资源"
                  : "新增" +
                    ({
                      books: "图书",
                      categories: "分类",
                      students: "学生",
                      administrators: "管理员",
                    }[module] || "记录")
          }}</el-button
        >
      </div>
    </div>
    <div v-if="module === 'resources'" class="resource-tabs">
      <button
        v-for="kind in ['', 'SEAT', 'LOCKER', 'ROOM']"
        :key="kind"
        :class="{ active: filters.kind === kind }"
        @click="
          filters.kind = kind;
          search();
        "
      >
        <AppIcon
          :name="
            kind === 'SEAT'
              ? 'Monitor'
              : kind === 'LOCKER'
                ? 'Archive'
                : kind === 'ROOM'
                  ? 'Presentation'
                  : 'LayoutGrid'
          "
          :size="18"
        />{{ kind ? labels[kind] : "全部空间" }}
      </button>
    </div>
    <div class="panel data-panel">
      <div class="filter-bar">
        <el-input
          v-model="filters.q"
          :placeholder="
            '搜索' +
            ({
              books: '书名 / 作者 / ISBN',
              students: '姓名 / 学号',
              loans: '书名 / 姓名 / 学号',
            }[module] || title)
          "
          clearable
          class="search-input"
          @keyup.enter="search"
          @clear="search"
          ><template #prefix
            ><AppIcon name="Search" :size="17" /></template></el-input
        ><el-select
          v-if="module === 'books'"
          v-model="filters.categoryId"
          placeholder="全部分类"
          clearable
          @change="search"
          ><el-option
            v-for="c in lookup.categories"
            :key="c.id"
            :value="c.id"
            :label="c.name" /></el-select
        ><el-select
          v-if="statusOptions.length"
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          @change="search"
          ><el-option
            v-for="o in statusOptions"
            :key="o.value"
            :value="o.value"
            :label="o.label" /></el-select
        ><el-select
          v-if="module === 'loans' && auth.isAdmin"
          v-model="filters.due"
          placeholder="到期提醒"
          clearable
          @change="search"
          ><el-option label="三天内到期" value="soon" /></el-select
        ><el-date-picker
          v-if="module === 'visits'"
          v-model="filters.date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          @change="search"
        /><el-button @click="search">查询</el-button
        ><el-button text @click="reset">重置</el-button
        ><span class="filter-total">共 {{ total }} 条</span
        ><button
          v-if="module === 'books' && !auth.isAdmin"
          class="icon-button"
          aria-label="切换列表布局"
          @click="mode = mode === 'cards' ? 'table' : 'cards'"
        >
          <AppIcon
            :name="mode === 'cards' ? 'List' : 'LayoutGrid'"
            :size="19"
          />
        </button>
      </div>
      <template v-if="cards">
        <div v-if="module === 'books'" class="book-grid">
          <article
            v-for="(b, index) in rows"
            :key="b.id"
            class="book-card"
            @click="showDetail(b)"
          >
            <div class="book-cover" :class="'cover-' + (index % 6)">
              <span>{{ b.categoryName }}</span>
              <h3>{{ b.title }}</h3>
              <div class="cover-lines"></div>
              <small>{{ b.author }}</small>
            </div>
            <div class="book-card-body">
              <span class="book-category">{{ b.categoryName }}</span>
              <h3>{{ b.title }}</h3>
              <p>{{ b.author }}</p>
              <div class="book-card-bottom">
                <span
                  ><i :class="{ unavailable: !b.availableStock }"></i
                  >{{
                    b.availableStock > 0
                      ? "可借 " + b.availableStock + " 本"
                      : "暂时借完"
                  }}</span
                ><AppIcon name="ArrowUpRight" :size="17" />
              </div>
            </div>
          </article>
        </div>
        <div v-else-if="module === 'resources'" class="resource-grid">
          <article v-for="r in rows" :key="r.id" class="resource-card">
            <div class="resource-card-top">
              <div class="resource-icon" :class="r.kind.toLowerCase()">
                <AppIcon
                  :name="
                    r.kind === 'SEAT'
                      ? 'Monitor'
                      : r.kind === 'LOCKER'
                        ? 'Archive'
                        : 'Presentation'
                  "
                  :size="27"
                />
              </div>
              <el-tag :type="tone(r.status)" effect="light">{{
                r.kind === "SEAT" && r.equipmentStatus === "DAMAGED"
                  ? "设备损坏"
                  : labels[r.status]
              }}</el-tag>
            </div>
            <span class="resource-code"
              >{{ r.code }} · {{ labels[r.kind] }}</span
            >
            <h3>{{ r.name }}</h3>
            <p><AppIcon name="MapPin" :size="14" />{{ r.location }}</p>
            <p>
              <AppIcon name="Users" :size="14" />容量 {{ r.capacity }} 人
              <span class="occupied-hint">{{
                r.activityOccupied
                  ? "活动占用"
                  : r.occupied
                    ? "当前使用中"
                    : "当前空闲"
              }}</span>
            </p>
            <div class="resource-description">{{ r.description }}</div>
            <div class="resource-actions">
              <el-button
                type="primary"
                plain
                :disabled="
                  r.status !== 'OPEN' || r.equipmentStatus === 'DAMAGED'
                "
                @click="openAction('reserve', r)"
                >查看时段 / 预约
                <AppIcon name="ArrowUpRight" :size="15" /></el-button
              ><el-button v-if="canEdit" text @click="edit(r)">管理</el-button
              ><el-button
                v-if="canEdit && r.status === 'OPEN'"
                text
                type="danger"
                @click="remove(r)"
                >关闭</el-button
              >
            </div>
          </article>
        </div>
        <div v-else-if="module === 'activities'" class="activity-grid">
          <article v-for="(a, i) in rows" :key="a.id" class="activity-card">
            <div class="activity-cover" :class="'activity-' + (i % 3)">
              <span>LINJUN / READING TOGETHER</span
              ><AppIcon
                :name="['BookOpen', 'Lightbulb', 'Coffee'][i % 3]"
                :size="64"
              /><b>{{
                ["READ & SHARE", "IDEAS IN MOTION", "MEET & CONNECT"][i % 3]
              }}</b>
            </div>
            <div class="activity-body">
              <el-tag :type="tone(a.status)" size="small">{{
                labels[a.status]
              }}</el-tag>
              <h3>{{ a.title }}</h3>
              <p>
                <AppIcon name="CalendarDays" :size="15" />{{
                  display(a, "startAt")
                }}
              </p>
              <p>
                <AppIcon name="MapPin" :size="15" />{{ a.resourceName }} ·
                {{ a.location }}
              </p>
              <p class="activity-desc">{{ a.description }}</p>
              <div class="activity-progress">
                <span>已报名 {{ a.enrolled }} / {{ a.maxPeople }} 人</span
                ><el-progress
                  :percentage="
                    Math.min(100, Math.round((a.enrolled / a.maxPeople) * 100))
                  "
                  :show-text="false"
                  :stroke-width="4"
                />
              </div>
              <div class="activity-actions">
                <el-button
                  type="primary"
                  :disabled="
                    a.status !== 'OPEN' ||
                    a.enrolled >= a.maxPeople ||
                    ['REGISTERED', 'CHECKED_IN'].includes(a.myRegistration)
                  "
                  @click="openAction('register', a)"
                  >{{
                    ["REGISTERED", "CHECKED_IN"].includes(a.myRegistration)
                      ? "已报名"
                      : "报名活动"
                  }}</el-button
                ><template v-if="auth.isAdmin"
                  ><el-button text @click="edit(a)">编辑</el-button
                  ><el-button
                    text
                    @click="
                      router.push({
                        path: '/registrations',
                        query: { activityId: a.id },
                      })
                    "
                    >名单</el-button
                  ><el-button text type="danger" @click="remove(a)"
                    >取消</el-button
                  ></template
                >
              </div>
            </div>
          </article>
        </div>
        <div v-else class="notice-list">
          <article
            v-for="a in rows"
            :key="a.id"
            class="notice-article"
            @click="showDetail(a)"
          >
            <div class="notice-symbol" :class="{ pinned: a.pinned }">
              <AppIcon :name="a.pinned ? 'Pin' : 'FileText'" :size="22" />
            </div>
            <div>
              <div class="notice-article-title">
                <el-tag v-if="a.pinned" size="small">置顶</el-tag>
                <h3>{{ a.title }}</h3>
              </div>
              <p>{{ a.content }}</p>
              <span>{{ a.authorName }} · {{ display(a, "createdAt") }}</span>
            </div>
            <AppIcon name="ChevronRight" :size="19" />
          </article>
        </div>
        <el-empty
          v-if="!rows.length"
          description="暂时没有记录，换个条件试试"
        />
      </template>
      <el-table
        v-else
        :data="rows"
        row-key="id"
        :empty-text="'暂无记录'"
        class="library-table"
        ><el-table-column type="index" label="#" width="55" /><el-table-column
          v-for="c in meta.columns"
          :key="c.key"
          :label="c.label"
          :prop="c.key"
          :min-width="c.width || 200"
          show-overflow-tooltip
          ><template #default="{ row }"
            ><el-tag
              v-if="['status', 'displayStatus'].includes(c.key)"
              :type="tone(row[c.key])"
              effect="light"
              >{{ display(row, c.key) }}</el-tag
            ><span
              v-else-if="c.key === 'availableStock'"
              class="stock-number"
              >{{ row[c.key] }}</span
            ><span v-else-if="c.key === 'title'" class="table-title">{{
              display(row, c.key)
            }}</span
            ><span v-else>{{ display(row, c.key) }}</span></template
          ></el-table-column
        >
        <el-table-column
          label="操作"
          fixed="right"
          :width="module === 'books' ? 230 : 190"
          ><template #default="{ row }"
            ><div class="table-actions">
              <el-button
                v-if="module === 'books' || module === 'announcements'"
                link
                type="primary"
                @click="showDetail(row)"
                >{{ module === "books" ? "详情 / 编号" : "查看" }}</el-button
              >
              <el-button
                v-if="canEdit && row.role !== 'SUPER_ADMIN'"
                link
                type="primary"
                @click="edit(row)"
                >编辑</el-button
              >
              <el-button
                v-if="module === 'books' && auth.isAdmin && row.enabled"
                link
                type="primary"
                @click="openAction('acquire', row)"
                >入库</el-button
              >
              <el-button
                v-if="canEdit && row.role !== 'SUPER_ADMIN'"
                link
                type="danger"
                @click="remove(row)"
                >{{
                  ["books", "students", "administrators"].includes(module)
                    ? "停用"
                    : "删除"
                }}</el-button
              >
              <el-button
                v-if="module === 'students' && !row.blacklisted"
                link
                type="warning"
                @click="openAction('blacklist-add', row)"
                >黑名单</el-button
              >
              <el-button
                v-if="module === 'blacklist' && row.active"
                link
                type="primary"
                @click="quickAction('blacklist-remove', row)"
                >移出</el-button
              >
              <el-button
                v-if="
                  module === 'loans' &&
                  auth.isAdmin &&
                  row.status === 'BORROWED'
                "
                link
                type="primary"
                @click="quickAction('return', row)"
                >登记还书</el-button
              >
              <el-button
                v-if="
                  module === 'fines' && auth.isAdmin && row.status === 'UNPAID'
                "
                link
                type="primary"
                @click="quickAction('fine-settle', row)"
                >线下已处理</el-button
              >
              <template v-if="module === 'reservations'"
                ><el-button
                  v-if="auth.isAdmin && row.status === 'PENDING'"
                  link
                  type="primary"
                  @click="openAction('review', row)"
                  >审核</el-button
                ><el-button
                  v-if="row.status === 'APPROVED' && row.kind === 'SEAT'"
                  link
                  type="primary"
                  @click="quickAction('reservation-checkin', row)"
                  >签到</el-button
                ><el-button
                  v-if="['PENDING', 'APPROVED'].includes(row.status)"
                  link
                  type="danger"
                  @click="quickAction('reservation-cancel', row)"
                  >取消</el-button
                ></template
              >
              <template v-if="module === 'registrations'"
                ><el-button
                  v-if="auth.isAdmin && row.status === 'REGISTERED'"
                  link
                  type="primary"
                  @click="quickAction('activity-checkin', row)"
                  >核销入场</el-button
                ><el-button
                  v-if="row.status === 'REGISTERED'"
                  link
                  type="danger"
                  @click="quickAction('registration-cancel', row)"
                  >取消报名</el-button
                ></template
              >
              <span
                v-if="['acquisitions', 'visits'].includes(module)"
                class="muted"
                >已记录</span
              >
            </div></template
          ></el-table-column
        >
      </el-table>
      <div class="pagination-row">
        <span>共 {{ total }} 条记录</span
        ><el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
          :total="total"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>
    <el-dialog
      v-model="editVisible"
      :title="(editingId ? '编辑' : '新增') + title"
      width="580px"
      destroy-on-close
      ><el-form
        ref="editFormRef"
        :model="form"
        label-position="top"
        @submit.prevent="save"
        ><div class="form-grid">
          <el-form-item
            v-for="f in editableFields"
            :key="f.key"
            :label="f.label"
            :prop="f.key"
            :rules="
              isRequired(f.key)
                ? [
                    {
                      required: true,
                      message: '请填写' + f.label,
                      trigger: ['blur', 'change'],
                    },
                  ]
                : []
            "
            :class="{ 'full-field': f.type === 'textarea' }"
            ><el-select
              v-if="f.type === 'select'"
              v-model="form[f.key]"
              filterable
              :disabled="f.key === 'kind' && !!editingId"
              clearable
              ><el-option
                v-for="o in formOptions(f)"
                :key="o.value"
                :value="o.value"
                :label="o.label" /></el-select
            ><el-input-number
              v-else-if="f.type === 'number'"
              v-model="form[f.key]"
              :min="1"
              :max="10000" /><el-date-picker
              v-else-if="f.type === 'datetime'"
              v-model="form[f.key]"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="请选择时间" /><el-input
              v-else
              v-model="form[f.key]"
              :type="
                f.type === 'textarea'
                  ? 'textarea'
                  : f.type === 'password'
                    ? 'password'
                    : 'text'
              "
              :show-password="f.type === 'password'"
              :rows="4"
              :maxlength="f.type === 'textarea' ? 1500 : 150"
          /></el-form-item></div></el-form
      ><template #footer
        ><el-button @click="editVisible = false">取消</el-button
        ><el-button type="primary" :loading="busy" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="actionVisible"
      :title="actionTitle"
      width="590px"
      destroy-on-close
      ><div v-if="actionName === 'reserve'" class="availability-note">
        <strong>该资源未来已占用时段</strong>
        <p v-if="!slots.length">暂无已确认的占用，可提交合适的使用时段。</p>
        <div v-for="(s, i) in slots" :key="i">
          {{ String(s.startAt).replace("T", " ").slice(0, 16) }} —
          {{ String(s.endAt).replace("T", " ").slice(0, 16) }}
          <el-tag size="small">{{
            s.source === "ACTIVITY" ? "活动" : "预约"
          }}</el-tag>
        </div>
        <small
          >座位最长4小时；储物柜最长使用天数以首页规则为准。房间与储物柜需管理员审核。</small
        >
      </div>
      <el-form
        ref="actionFormRef"
        :model="actionForm"
        label-position="top"
        @submit.prevent="submitAction"
        ><el-form-item
          v-for="f in actionFields"
          :key="f.key"
          :label="f.label"
          :prop="f.key"
          :rules="
            f.required
              ? [
                  {
                    required: true,
                    message: '请填写' + f.label,
                    trigger: ['blur', 'change'],
                  },
                ]
              : []
          "
          ><el-select
            v-if="f.type === 'select'"
            v-model="actionForm[f.key]"
            filterable
            clearable
            ><el-option
              v-for="o in formOptions(f)"
              :key="o.value"
              :value="o.value"
              :label="o.label" /></el-select
          ><el-input-number
            v-else-if="f.type === 'number'"
            v-model="actionForm[f.key]"
            :min="1"
            :max="500" /><el-date-picker
            v-else-if="f.type === 'datetime'"
            v-model="actionForm[f.key]"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss" /><el-input
            v-else
            v-model="actionForm[f.key]"
            :type="f.type === 'textarea' ? 'textarea' : 'text'"
            :rows="3"
        /></el-form-item>
        <p v-if="actionName === 'register'" class="muted">
          提交后将占用一个报名名额，可在“我的报名”中查看记录。
        </p>
        <p v-if="actionName === 'visit'" class="muted">
          选择学生后校验账号及黑名单；校外访客填写姓名和访客登记编号。
        </p></el-form
      ><template #footer
        ><el-button @click="actionVisible = false">取消</el-button
        ><el-button type="primary" :loading="busy" @click="submitAction"
          >确认提交</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="detailVisible" :title="detailTitle" width="700px"
      ><template v-if="detail"
        ><template v-if="module === 'books'"
          ><el-descriptions :column="2" border
            ><el-descriptions-item label="作者">{{
              detail.author
            }}</el-descriptions-item
            ><el-descriptions-item label="ISBN">{{
              detail.isbn
            }}</el-descriptions-item
            ><el-descriptions-item label="分类">{{
              detail.categoryName
            }}</el-descriptions-item
            ><el-descriptions-item label="出版社">{{
              detail.publisher
            }}</el-descriptions-item
            ><el-descriptions-item label="位置">{{
              detail.location
            }}</el-descriptions-item
            ><el-descriptions-item label="库存"
              >可借 {{ detail.availableStock }} / 总计
              {{ detail.totalStock }}</el-descriptions-item
            ></el-descriptions
          >
          <p class="detail-content">{{ detail.description }}</p>
          <h4>实体馆藏编号</h4>
          <el-table :data="copyRows" max-height="300"
            ><el-table-column prop="code" label="馆藏编号" /><el-table-column
              label="状态"
              ><template #default="{ row }"
                ><el-tag :type="tone(row.status)">{{
                  labels[row.status]
                }}</el-tag></template
              ></el-table-column
            ></el-table
          >
          <p class="muted">
            借还书时使用具体的馆藏编号，请到服务台办理。
          </p></template
        ><template v-else
          ><p class="muted">
            {{ detail.authorName }} · {{ display(detail, "createdAt") }}
          </p>
          <div class="detail-content">
            {{ detail.content || detail.description }}
          </div></template
        ></template
      ></el-dialog
    >
  </div>
</template>
