<script setup>
import { ref, onMounted } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const categories = ref([])
const articles = ref([])
const selectedCat = ref(null)
const form = ref({ id: null, title: '', content: '', helpCategoryId: null })
const editing = ref(false)
const msg = ref('')

async function loadCategories() {
  try {
    const res = await fetch(`${API_BASE}/admin/help/categories`, { credentials: 'include' })
    const json = await res.json()
    if (json.code === 0) categories.value = json.data || []
  } catch (_) {}
}

async function loadArticles(catId) {
  selectedCat.value = catId
  try {
    const res = await fetch(`${API_BASE}/admin/help/list?categoryId=${catId || ''}`, { credentials: 'include' })
    const json = await res.json()
    if (json.code === 0) articles.value = json.data || []
  } catch (_) { articles.value = [] }
}

function startAdd() {
  form.value = { id: null, title: '', content: '', helpCategoryId: selectedCat.value }
  editing.value = true; msg.value = ''
}
function startEdit(a) {
  form.value = { id: a.id, title: a.title, content: a.content, helpCategoryId: a.helpCategoryId }
  editing.value = true; msg.value = ''
}
function cancel() { editing.value = false }

async function save() {
  msg.value = ''
  if (!form.value.title) { msg.value = '请填写标题'; return }
  const url = form.value.id ? `${API_BASE}/admin/help/update` : `${API_BASE}/admin/help/add`
  try {
    const res = await fetch(url, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form.value)
    })
    const json = await res.json()
    if (json.code === 0) { editing.value = false; loadArticles(selectedCat.value) }
    else msg.value = json.message || '保存失败'
  } catch (e) { msg.value = e.message }
}

async function del(id) {
  if (!confirm('确定删除？')) return
  try {
    await fetch(`${API_BASE}/admin/help/delete?id=${id}`, { method: 'DELETE', credentials: 'include' })
    loadArticles(selectedCat.value)
  } catch (_) {}
}

onMounted(async () => {
  await loadCategories()
  if (categories.value.length) loadArticles(categories.value[0].id)
  else loadArticles(null)
})
</script>

<template>
  <div class="admin-page">
    <h1 class="page-title">帮助文章</h1>

    <template v-if="!editing">
      <div class="cat-tabs">
        <button v-for="c in categories" :key="c.id" :class="{ active: selectedCat === c.id }" @click="loadArticles(c.id)">{{ c.name }}</button>
        <button :class="{ active: !selectedCat }" @click="loadArticles(null)">全部</button>
      </div>
      <button class="btn-primary" @click="startAdd" style="margin:12px 0">+ 新增文章</button>
      <table class="data-table">
        <thead><tr><th>ID</th><th>标题</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="a in articles" :key="a.id">
            <td>{{ a.id }}</td><td>{{ a.title }}</td>
            <td>
              <button class="btn-sm" @click="startEdit(a)">编辑</button>
              <button class="btn-sm del" @click="del(a.id)">删除</button>
            </td>
          </tr>
          <tr v-if="!articles.length"><td colspan="3" class="empty">暂无文章</td></tr>
        </tbody>
      </table>
    </template>

    <template v-else>
      <h3 class="form-title">{{ form.id ? '编辑文章' : '新增文章' }}</h3>
      <div class="form-group"><label>标题</label><input v-model="form.title" class="form-input" /></div>
      <div class="form-group"><label>内容</label><textarea v-model="form.content" class="form-input ta" rows="8"></textarea></div>
      <p v-if="msg" class="msg">{{ msg }}</p>
      <div class="form-actions">
        <button class="btn-primary" @click="save">保存</button>
        <button class="btn-cancel" @click="cancel">取消</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-page { max-width: 900px; }
.page-title { font-size: 22px; font-weight: 700; margin: 0 0 20px; color: #fff; }
.cat-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.cat-tabs button { padding: 5px 14px; background: #172636; border: 1px solid #27313e; color: #828ea1; border-radius: 4px; cursor: pointer; font-size: 12px; }
.cat-tabs button.active { border-color: #f0a70a; color: #f0a70a; }
.btn-primary { padding: 6px 16px; background: #f0a70a; border: none; border-radius: 4px; color: #000; font-weight: 600; cursor: pointer; font-size: 13px; }
.data-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.data-table th { padding: 8px 10px; text-align: left; color: #828ea1; border-bottom: 1px solid #1e2d3d; }
.data-table td { padding: 8px 10px; border-bottom: 1px solid #1e2d3d; color: #e4e4e7; }
.btn-sm { padding: 3px 10px; background: transparent; border: 1px solid #f0a70a; color: #f0a70a; border-radius: 4px; cursor: pointer; font-size: 11px; margin-right: 6px; }
.btn-sm.del { border-color: #f6465d; color: #f6465d; }
.empty { color: #828ea1; text-align: center; padding: 40px; }
.form-title { font-size: 16px; color: #fff; margin: 0 0 16px; }
.form-group { margin-bottom: 12px; }
.form-group label { display: block; font-size: 12px; color: #828ea1; margin-bottom: 4px; }
.form-input { width: 100%; padding: 8px 12px; background: #172636; border: 1px solid #27313e; border-radius: 4px; color: #fff; font-size: 13px; box-sizing: border-box; }
.ta { resize: vertical; font-family: inherit; }
.msg { color: #f6465d; font-size: 12px; }
.form-actions { display: flex; gap: 10px; margin-top: 16px; }
.btn-cancel { padding: 6px 16px; background: transparent; border: 1px solid #27313e; border-radius: 4px; color: #828ea1; cursor: pointer; font-size: 13px; }
</style>
