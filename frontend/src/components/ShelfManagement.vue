<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  ElTable,
  ElTableColumn,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElSelect,
  ElOption,
  ElTree,
  ElMessageBox,
  ElMessage,
  ElTabs,
  ElTabPane,
  ElInputNumber
} from 'element-plus'
import {
  createShelf,
  getAllShelves,
  deleteShelf,
  bindCode,
  unbindCode,
  reassignCode,
  searchByCode,
  getZoneTree,
  getAllCodeMappings,
  getAllChangeLogs,
  exportCodeMapping,
  type Shelf,
  type ZoneTree,
  type CodeMapping,
  type CodeChangeLog
} from '@/api/shelf'

const shelves = ref<Shelf[]>([])
const zoneTree = ref<ZoneTree[]>([])
const codeMappings = ref<CodeMapping[]>([])
const changeLogs = ref<CodeChangeLog[]>([])
const searchCode = ref('')
const searchResult = ref<Shelf | null>(null)
const activeTab = ref('list')

const showCreateDialog = ref(false)
const showBindDialog = ref(false)
const showReassignDialog = ref(false)
const showUnbindDialog = ref(false)

const createForm = reactive({
  shelfNo: '',
  capacity: 0,
  zone: ''
})

const bindForm = reactive({
  shelfId: 0,
  code: '',
  operator: '',
  remark: ''
})

const reassignForm = reactive({
  shelfId: 0,
  newCode: '',
  operator: '',
  remark: ''
})

const unbindForm = reactive({
  shelfId: 0,
  operator: '',
  remark: ''
})

const zones = ['A区', 'B区', 'C区', 'D区', 'E区']

const operationTypeMap: Record<number, string> = {
  1: '绑定',
  2: '解绑',
  3: '重分配'
}

const operationTypeColorMap: Record<number, string> = {
  1: 'success',
  2: 'warning',
  3: 'primary'
}

async function loadShelves() {
  try {
    const res = await getAllShelves()
    if (res.data.code === 200) {
      shelves.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载货架列表失败')
  }
}

async function loadZoneTree() {
  try {
    const res = await getZoneTree()
    if (res.data.code === 200) {
      zoneTree.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载分区树失败')
  }
}

async function loadCodeMappings() {
  try {
    const res = await getAllCodeMappings()
    if (res.data.code === 200) {
      codeMappings.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载编码对照表失败')
  }
}

async function loadChangeLogs() {
  try {
    const res = await getAllChangeLogs()
    if (res.data.code === 200) {
      changeLogs.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载变更记录失败')
  }
}

async function handleCreate() {
  if (!createForm.shelfNo || createForm.capacity <= 0 || !createForm.zone) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    const res = await createShelf(createForm)
    if (res.data.code === 200) {
      ElMessage.success('创建成功')
      showCreateDialog.value = false
      createForm.shelfNo = ''
      createForm.capacity = 0
      createForm.zone = ''
      loadShelves()
      loadZoneTree()
      loadCodeMappings()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '创建失败')
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该货架吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteShelf(id)
    if (res.data.code === 200) {
      ElMessage.success('删除成功')
      loadShelves()
      loadZoneTree()
      loadCodeMappings()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

function openBindDialog(shelf: Shelf) {
  bindForm.shelfId = shelf.id
  bindForm.code = ''
  bindForm.operator = ''
  bindForm.remark = ''
  showBindDialog.value = true
}

async function handleBind() {
  if (!bindForm.code) {
    ElMessage.warning('请输入货位编码')
    return
  }
  try {
    const res = await bindCode(bindForm)
    if (res.data.code === 200) {
      ElMessage.success('绑定成功')
      showBindDialog.value = false
      loadShelves()
      loadZoneTree()
      loadCodeMappings()
      loadChangeLogs()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '绑定失败')
  }
}

function openUnbindDialog(shelf: Shelf) {
  unbindForm.shelfId = shelf.id
  unbindForm.operator = ''
  unbindForm.remark = ''
  showUnbindDialog.value = true
}

async function handleUnbind() {
  try {
    const res = await unbindCode(unbindForm.shelfId, unbindForm.operator, unbindForm.remark)
    if (res.data.code === 200) {
      ElMessage.success('解绑成功')
      showUnbindDialog.value = false
      loadShelves()
      loadZoneTree()
      loadCodeMappings()
      loadChangeLogs()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '解绑失败')
  }
}

function openReassignDialog(shelf: Shelf) {
  reassignForm.shelfId = shelf.id
  reassignForm.newCode = ''
  reassignForm.operator = ''
  reassignForm.remark = ''
  showReassignDialog.value = true
}

async function handleReassign() {
  if (!reassignForm.newCode) {
    ElMessage.warning('请输入新货位编码')
    return
  }
  try {
    const res = await reassignCode(reassignForm)
    if (res.data.code === 200) {
      ElMessage.success('重分配成功')
      showReassignDialog.value = false
      loadShelves()
      loadZoneTree()
      loadCodeMappings()
      loadChangeLogs()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '重分配失败')
  }
}

async function handleSearchByCode() {
  if (!searchCode.value.trim()) {
    ElMessage.warning('请输入货位编码')
    return
  }
  try {
    const res = await searchByCode(searchCode.value.trim())
    if (res.data.code === 200) {
      searchResult.value = res.data.data
    } else {
      searchResult.value = null
      ElMessage.warning(res.data.message)
    }
  } catch (error: any) {
    searchResult.value = null
    ElMessage.error(error.response?.data?.message || '搜索失败')
  }
}

async function handleExport() {
  try {
    const res = await exportCodeMapping()
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'shelf_code_mapping.csv'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadShelves()
  loadZoneTree()
  loadCodeMappings()
  loadChangeLogs()
})
</script>

<template>
  <div class="shelf-management">
    <header class="header">
      <h1>冷链物流中转仓 - 置物架货位编码配对管理系统</h1>
      <div class="header-actions">
        <ElButton type="primary" @click="showCreateDialog = true">
          新建货架
        </ElButton>
        <ElButton type="success" @click="handleExport">
          导出对照表
        </ElButton>
      </div>
    </header>

    <div class="main-content">
      <ElTabs v-model="activeTab" type="card" class="main-tabs">
        <ElTabPane label="货架列表" name="list">
          <div class="search-bar">
            <div class="code-search">
              <ElInput
                v-model="searchCode"
                placeholder="按货位编码反向搜索货架"
                style="width: 300px"
                @keyup.enter="handleSearchByCode"
              >
                <template #append>
                  <ElButton @click="handleSearchByCode">搜索</ElButton>
                </template>
              </ElInput>
            </div>
          </div>

          <div v-if="searchResult" class="search-result-card">
            <ElMessage type="success" show-icon>找到匹配的货架信息</ElMessage>
            <ElTable :data="[searchResult]" border>
              <ElTableColumn prop="shelfNo" label="货架编号" />
              <ElTableColumn prop="capacity" label="承重(kg)" />
              <ElTableColumn prop="zone" label="所属库区" />
              <ElTableColumn prop="locationCode" label="货位编码" />
              <ElTableColumn prop="createdAt" label="创建时间" />
            </ElTable>
          </div>

          <ElTable :data="shelves" border stripe>
            <ElTableColumn prop="shelfNo" label="货架编号" />
            <ElTableColumn prop="capacity" label="承重(kg)" />
            <ElTableColumn prop="zone" label="所属库区" />
            <ElTableColumn prop="locationCode" label="货位编码">
              <template #default="{ row }">
                <span :class="row.locationCode ? 'code-bound' : 'code-unbound'">
                  {{ row.locationCode || '未绑定' }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="createdAt" label="创建时间" width="180" />
            <ElTableColumn label="操作" width="300">
              <template #default="{ row }">
                <ElButton
                  v-if="!row.locationCode"
                  type="primary"
                  size="small"
                  @click="openBindDialog(row as Shelf)"
                >
                  绑定编码
                </ElButton>
                <template v-else>
                  <ElButton
                    type="warning"
                    size="small"
                    @click="openUnbindDialog(row as Shelf)"
                  >
                    解绑
                  </ElButton>
                  <ElButton
                    type="info"
                    size="small"
                    @click="openReassignDialog(row as Shelf)"
                  >
                    重分配编码
                  </ElButton>
                </template>
                <ElButton
                  type="danger"
                  size="small"
                  @click="handleDelete((row as Shelf).id)"
                >
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane label="分区树形视图" name="tree">
          <div class="tree-container">
            <ElTree
              :data="zoneTree"
              :props="{ label: 'label', children: 'children' }"
              default-expand-all
              node-key="value"
              highlight-current
            >
              <template #default="{ node }">
                <span class="custom-tree-node">
                  <span>{{ node.label }}</span>
                </span>
              </template>
            </ElTree>
          </div>
        </ElTabPane>

        <ElTabPane label="编码对照表" name="mapping">
          <ElTable :data="codeMappings" border stripe>
            <ElTableColumn prop="shelfNo" label="货架编号" />
            <ElTableColumn prop="capacity" label="承重(kg)" />
            <ElTableColumn prop="zone" label="所属库区" />
            <ElTableColumn prop="locationCode" label="货位编码" />
            <ElTableColumn prop="bindTime" label="绑定时间" width="180" />
          </ElTable>
        </ElTabPane>

        <ElTabPane label="变更记录" name="logs">
          <ElTable :data="changeLogs" border stripe>
            <ElTableColumn prop="shelfNo" label="货架编号" />
            <ElTableColumn prop="oldCode" label="旧编码" />
            <ElTableColumn prop="newCode" label="新编码" />
            <ElTableColumn prop="operationType" label="操作类型">
              <template #default="{ row }">
                <ElTag :type="operationTypeColorMap[row.operationType]">
                  {{ operationTypeMap[row.operationType] }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="operator" label="操作人" />
            <ElTableColumn prop="remark" label="备注" />
            <ElTableColumn prop="createdAt" label="操作时间" width="180" />
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </div>

    <ElDialog title="新建货架" v-model="showCreateDialog" width="400px">
      <ElForm :model="createForm" label-width="100px">
        <ElFormItem label="货架编号">
          <ElInput v-model="createForm.shelfNo" placeholder="请输入货架编号" />
        </ElFormItem>
        <ElFormItem label="承重规格(kg)">
          <ElInputNumber v-model="createForm.capacity" :min="0.1" :step="10" style="width: 100%" />
        </ElFormItem>
        <ElFormItem label="所属库区">
          <ElSelect v-model="createForm.zone" placeholder="请选择库区">
            <ElOption v-for="zone in zones" :key="zone" :label="zone" :value="zone" />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showCreateDialog = false">取消</ElButton>
        <ElButton type="primary" @click="handleCreate">确定</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="绑定货位编码" v-model="showBindDialog" width="400px">
      <ElForm :model="bindForm" label-width="100px">
        <ElFormItem label="货位编码">
          <ElInput v-model="bindForm.code" placeholder="请输入货位编码" />
        </ElFormItem>
        <ElFormItem label="操作人">
          <ElInput v-model="bindForm.operator" placeholder="请输入操作人" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="bindForm.remark" type="textarea" placeholder="请输入备注" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showBindDialog = false">取消</ElButton>
        <ElButton type="primary" @click="handleBind">绑定</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="解绑货位编码" v-model="showUnbindDialog" width="400px">
      <ElForm :model="unbindForm" label-width="100px">
        <ElFormItem label="操作人">
          <ElInput v-model="unbindForm.operator" placeholder="请输入操作人" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="unbindForm.remark" type="textarea" placeholder="请输入解绑原因" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showUnbindDialog = false">取消</ElButton>
        <ElButton type="warning" @click="handleUnbind">确认解绑</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="重分配货位编码" v-model="showReassignDialog" width="400px">
      <ElForm :model="reassignForm" label-width="100px">
        <ElFormItem label="新货位编码">
          <ElInput v-model="reassignForm.newCode" placeholder="请输入新货位编码" />
        </ElFormItem>
        <ElFormItem label="操作人">
          <ElInput v-model="reassignForm.operator" placeholder="请输入操作人" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="reassignForm.remark" type="textarea" placeholder="请输入重分配原因" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showReassignDialog = false">取消</ElButton>
        <ElButton type="primary" @click="handleReassign">确认重分配</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.shelf-management {
  width: 100%;
  min-height: 100vh;
}

.header {
  background: linear-gradient(135deg, #1e3a5f 0%, #2c5282 100%);
  padding: 20px 30px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: white;
}

.header h1 {
  font-size: 22px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.main-content {
  padding: 20px;
}

.main-tabs {
  height: calc(100vh - 120px);
}

.search-bar {
  margin-bottom: 20px;
}

.code-search {
  display: flex;
  gap: 10px;
}

.search-result-card {
  margin-bottom: 20px;
  padding: 15px;
  background: #f0fff4;
  border-radius: 8px;
}

.tree-container {
  height: calc(100vh - 200px);
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: auto;
  padding: 10px;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}

.code-bound {
  color: #67c23a;
  font-weight: 500;
}

.code-unbound {
  color: #909399;
}

:deep(.el-tree-node__content) {
  height: 40px;
}
</style>
