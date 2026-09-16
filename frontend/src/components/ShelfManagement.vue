<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
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
  ElInputNumber,
  ElTag,
  ElTooltip,
  ElDescriptions,
  ElDescriptionsItem
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
  landPallet,
  removePallet,
  getShelfOccupancy,
  getActivePallets,
  getPalletHistory,
  type Shelf,
  type ZoneTree,
  type CodeMapping,
  type CodeChangeLog,
  type PalletOccupancy,
  type ShelfOccupancy
} from '@/api/shelf'
import ShiftHandover from './ShiftHandover.vue'

const shelves = ref<Shelf[]>([])
const zoneTree = ref<ZoneTree[]>([])
const codeMappings = ref<CodeMapping[]>([])
const changeLogs = ref<CodeChangeLog[]>([])
const activePallets = ref<PalletOccupancy[]>([])
const occupancyDetail = ref<ShelfOccupancy | null>(null)
const palletHistory = ref<PalletOccupancy[]>([])
const historyKeyword = ref('')
const palletFilterShelfNo = ref('')

const searchCode = ref('')
const searchResult = ref<Shelf | null>(null)
const activeTab = ref('shift')

const showCreateDialog = ref(false)
const showBindDialog = ref(false)
const showReassignDialog = ref(false)
const showUnbindDialog = ref(false)
const showLandDialog = ref(false)
const showRemoveDialog = ref(false)
const showOccupancyDialog = ref(false)

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
  shelfNo: '',
  palletCount: 0,
  operator: '',
  remark: ''
})

const landForm = reactive({
  shelfId: 0,
  shelfNo: '',
  capacity: 0,
  usedCapacity: 0,
  remainingCapacity: 0,
  palletNo: '',
  grossWeight: 0,
  operator: '',
  remark: ''
})

const removeForm = reactive({
  palletNo: '',
  shelfNo: '',
  grossWeight: 0,
  operator: '',
  remark: ''
})

const zones = ['A区', 'B区', 'C区', 'D区', 'E区']

const operationTypeMap: Record<number, string> = {
  1: '绑定',
  2: '解绑',
  3: '重分配'
}

const operationTypeColorMap: Record<number, 'success' | 'warning' | 'primary'> = {
  1: 'success',
  2: 'warning',
  3: 'primary'
}

const occupancyRate = (used: number, cap: number) => {
  if (!cap || cap <= 0) return 0
  return Math.min(100, Math.round((Number(used) / Number(cap)) * 100))
}

const occupancyTagType = (used: number, cap: number): 'success' | 'warning' | 'danger' => {
  const r = occupancyRate(used, cap)
  if (r >= 95) return 'danger'
  if (r >= 70) return 'warning'
  return 'success'
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

async function loadActivePallets() {
  try {
    const res = await getActivePallets(palletFilterShelfNo.value.trim())
    if (res.data.code === 200) {
      activePallets.value = res.data.data
    }
  } catch (error) {
    ElMessage.error('加载在架托盘失败')
  }
}

async function refreshAll() {
  await Promise.all([loadShelves(), loadZoneTree(), loadCodeMappings(), loadChangeLogs(), loadActivePallets()])
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
    await ElMessageBox.confirm('确定删除该货架吗？有在架托盘时将被系统拦截。', '提示', {
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
      loadActivePallets()
    } else {
      ElMessage.error(res.data.message)
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
      refreshAll()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '绑定失败')
  }
}

function openUnbindDialog(shelf: Shelf) {
  if (shelf.palletCount > 0) {
    ElMessage.warning(`该架还有 ${shelf.palletCount} 托在架，必须先全部下架、退回承重后才能解绑`)
    return
  }
  unbindForm.shelfId = shelf.id
  unbindForm.shelfNo = shelf.shelfNo
  unbindForm.palletCount = shelf.palletCount
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
      refreshAll()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '解绑失败')
  }
}

function openReassignDialog(shelf: Shelf) {
  if (shelf.palletCount > 0) {
    ElMessage.warning(`该架还有 ${shelf.palletCount} 托在架，必须先全部下架、退回承重后才能重分配编码`)
    return
  }
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
      refreshAll()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '重分配失败')
  }
}

// ========== 托盘落架/下架 ==========

function openLandDialog(shelf: Shelf) {
  if (!shelf.locationCode) {
    ElMessage.warning('该货架还没绑货位编码，不能落架')
    return
  }
  landForm.shelfId = shelf.id
  landForm.shelfNo = shelf.shelfNo
  landForm.capacity = Number(shelf.capacity)
  landForm.usedCapacity = Number(shelf.usedCapacity)
  landForm.remainingCapacity = Number(shelf.remainingCapacity)
  landForm.palletNo = ''
  landForm.grossWeight = 0
  landForm.operator = ''
  landForm.remark = ''
  showLandDialog.value = true
}

async function handleLand() {
  if (!landForm.palletNo.trim()) {
    ElMessage.warning('请输入托盘号')
    return
  }
  if (!landForm.grossWeight || landForm.grossWeight <= 0) {
    ElMessage.warning('请输入大于0的毛重')
    return
  }
  // 前端先给一道即时提示，真正的并发/承重裁决在后端行锁事务里
  if (Number(landForm.grossWeight) > landForm.remainingCapacity) {
    ElMessage.warning(
      `本托 ${landForm.grossWeight}kg 超过该架剩余承重 ${landForm.remainingCapacity}kg，提交也会失败`
    )
    return
  }
  try {
    const res = await landPallet({
      shelfId: landForm.shelfId,
      palletNo: landForm.palletNo.trim(),
      grossWeight: landForm.grossWeight,
      operator: landForm.operator,
      remark: landForm.remark
    })
    if (res.data.code === 200) {
      ElMessage.success(
        `落架成功：在架合计 ${res.data.data.usedCapacity}kg，剩余 ${res.data.data.remainingCapacity}kg`
      )
      showLandDialog.value = false
      refreshAll()
    } else {
      // 并发落架失败：先前那一托和剩余承重保持不变
      ElMessage.error(res.data.message)
      loadShelves()
      loadActivePallets()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '落架失败，在架状态保持不变')
    loadShelves()
    loadActivePallets()
  }
}

function openRemoveDialog(pallet: PalletOccupancy) {
  removeForm.palletNo = pallet.palletNo
  removeForm.shelfNo = pallet.shelfNo
  removeForm.grossWeight = Number(pallet.grossWeight)
  removeForm.operator = ''
  removeForm.remark = ''
  showRemoveDialog.value = true
}

async function handleRemove() {
  try {
    const res = await removePallet({
      palletNo: removeForm.palletNo,
      operator: removeForm.operator,
      remark: removeForm.remark
    })
    if (res.data.code === 200) {
      ElMessage.success(
        `托盘 ${removeForm.palletNo} 已下架，退回承重 ${removeForm.grossWeight}kg，该架剩余 ${res.data.data.remainingCapacity}kg`
      )
      showRemoveDialog.value = false
      refreshAll()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '下架失败')
  }
}

async function showOccupancy(shelf: Shelf) {
  try {
    const res = await getShelfOccupancy(shelf.id)
    if (res.data.code === 200) {
      occupancyDetail.value = res.data.data
      showOccupancyDialog.value = true
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载占用详情失败')
  }
}

async function handleSearchHistory() {
  if (!historyKeyword.value.trim()) {
    ElMessage.warning('请输入托盘号')
    return
  }
  try {
    const res = await getPalletHistory(historyKeyword.value.trim())
    if (res.data.code === 200) {
      palletHistory.value = res.data.data
      if (res.data.data.length === 0) {
        ElMessage.info('未找到该托盘的落架记录')
      }
    } else {
      palletHistory.value = []
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    palletHistory.value = []
    ElMessage.error(error.response?.data?.message || '查询失败')
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

const totalStats = computed(() => {
  const used = shelves.value.reduce((acc, s) => acc + Number(s.usedCapacity || 0), 0)
  const cap = shelves.value.reduce((acc, s) => acc + Number(s.capacity || 0), 0)
  const pallets = shelves.value.reduce((acc, s) => acc + Number(s.palletCount || 0), 0)
  return { used: used.toFixed(2), cap: cap.toFixed(2), pallets, remaining: (cap - used).toFixed(2) }
})

onMounted(() => {
  loadShelves()
  loadZoneTree()
  loadCodeMappings()
  loadChangeLogs()
  loadActivePallets()
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
      <ElTabs v-model="activeTab" type="card" class="main-tabs" @tab-change="loadActivePallets">
        <ElTabPane label="库区交接班" name="shift" lazy>
          <ShiftHandover />
        </ElTabPane>

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
            <ElTableColumn prop="shelfNo" label="货架编号" width="110" />
            <ElTableColumn prop="capacity" label="额定承重(kg)" width="120" />
            <ElTableColumn prop="zone" label="所属库区" width="90" />
            <ElTableColumn prop="locationCode" label="货位编码" width="130">
              <template #default="{ row }">
                <span :class="row.locationCode ? 'code-bound' : 'code-unbound'">
                  {{ row.locationCode || '未绑定' }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="在架托数" width="90">
              <template #default="{ row }">
                <ElTag :type="row.palletCount > 0 ? 'primary' : 'info'">{{ row.palletCount || 0 }} 托</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="在架/剩余承重(kg)" width="200">
              <template #default="{ row }">
                <div class="weight-cell">
                  <ElTag size="small" :type="occupancyTagType(row.usedCapacity, row.capacity)">
                    在架 {{ Number(row.usedCapacity).toFixed(2) }}
                  </ElTag>
                  <span class="remaining-weight">剩余 {{ Number(row.remainingCapacity).toFixed(2) }}</span>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="createdAt" label="创建时间" width="170" />
            <ElTableColumn label="操作" width="390" fixed="right">
              <template #default="{ row }">
                <ElButton
                  type="success"
                  size="small"
                  :disabled="!row.locationCode"
                  @click="openLandDialog(row as Shelf)"
                >
                  托盘落架
                </ElButton>
                <ElButton
                  v-if="!row.locationCode"
                  type="primary"
                  size="small"
                  @click="openBindDialog(row as Shelf)"
                >
                  绑定编码
                </ElButton>
                <ElTooltip
                  v-else
                  :content="row.palletCount > 0 ? `有 ${row.palletCount} 托在架，请先下架再解绑` : ''"
                  :disabled="row.palletCount === 0"
                  placement="top"
                >
                  <ElButton
                    type="warning"
                    size="small"
                    :disabled="row.palletCount > 0"
                    @click="openUnbindDialog(row as Shelf)"
                  >
                    解绑
                  </ElButton>
                </ElTooltip>
                <ElTooltip
                  :content="row.palletCount > 0 ? `有 ${row.palletCount} 托在架，请先下架再重分配` : ''"
                  :disabled="row.palletCount === 0"
                  placement="top"
                >
                  <ElButton
                    type="info"
                    size="small"
                    :disabled="!row.locationCode || row.palletCount > 0"
                    @click="openReassignDialog(row as Shelf)"
                  >
                    重分配
                  </ElButton>
                </ElTooltip>
                <ElButton size="small" @click="showOccupancy(row as Shelf)">占用</ElButton>
                <ElButton type="danger" size="small" @click="handleDelete((row as Shelf).id)">
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane label="托盘占用" name="occupancy">
          <div class="occupancy-toolbar">
            <ElInput
              v-model="palletFilterShelfNo"
              placeholder="按货架编号过滤"
              style="width: 220px"
              clearable
              @keyup.enter="loadActivePallets"
              @clear="loadActivePallets"
            />
            <ElButton type="primary" @click="loadActivePallets">查询</ElButton>
            <div class="occupancy-stats">
              全仓在架：<b>{{ totalStats.pallets }}</b> 托 ｜
              在架合计 <b>{{ totalStats.used }}</b>kg ｜
              剩余承重合计 <b>{{ totalStats.remaining }}</b>kg ｜
              额定合计 {{ totalStats.cap }}kg
            </div>
          </div>

          <ElTable :data="activePallets" border stripe>
            <ElTableColumn prop="palletNo" label="托盘号" width="150" />
            <ElTableColumn prop="shelfNo" label="所在货架" width="120" />
            <ElTableColumn prop="grossWeight" label="毛重(kg)" width="120">
              <template #default="{ row }">{{ Number(row.grossWeight).toFixed(2) }}</template>
            </ElTableColumn>
            <ElTableColumn prop="landedBy" label="落架操作人" width="120">
              <template #default="{ row }">{{ row.landedBy || '-' }}</template>
            </ElTableColumn>
            <ElTableColumn prop="landedAt" label="落架时间" width="180" />
            <ElTableColumn prop="landRemark" label="落架备注" />
            <ElTableColumn label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <ElButton type="warning" size="small" @click="openRemoveDialog(row as PalletOccupancy)">
                  下架
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>

          <div class="history-section">
            <h3>托盘轨迹查询</h3>
            <div class="history-search">
              <ElInput
                v-model="historyKeyword"
                placeholder="输入托盘号查询落架/下架历史"
                style="width: 300px"
                @keyup.enter="handleSearchHistory"
              >
                <template #append>
                  <ElButton @click="handleSearchHistory">查询</ElButton>
                </template>
              </ElInput>
            </div>
            <ElTable v-if="palletHistory.length > 0" :data="palletHistory" border stripe style="margin-top: 12px">
              <ElTableColumn prop="palletNo" label="托盘号" width="140" />
              <ElTableColumn prop="shelfNo" label="货架" width="110" />
              <ElTableColumn prop="grossWeight" label="毛重(kg)" width="100">
                <template #default="{ row }">{{ Number(row.grossWeight).toFixed(2) }}</template>
              </ElTableColumn>
              <ElTableColumn label="状态" width="100">
                <template #default="{ row }">
                  <ElTag :type="row.status === 1 ? 'success' : 'info'">
                    {{ row.status === 1 ? '在架' : '已下架' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="landedAt" label="落架时间" width="170" />
              <ElTableColumn prop="landedBy" label="落架人" width="100" />
              <ElTableColumn prop="removedAt" label="下架时间" width="170">
                <template #default="{ row }">{{ row.removedAt || '-' }}</template>
              </ElTableColumn>
              <ElTableColumn prop="removedBy" label="下架人" width="100">
                <template #default="{ row }">{{ row.removedBy || '-' }}</template>
              </ElTableColumn>
            </ElTable>
          </div>
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

    <ElDialog title="解绑货位编码" v-model="showUnbindDialog" width="420px">
      <ElForm :model="unbindForm" label-width="100px">
        <ElFormItem label="货架">
          <span>{{ unbindForm.shelfNo }}（在架 {{ unbindForm.palletCount }} 托）</span>
        </ElFormItem>
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

    <ElDialog title="托盘落架登记" v-model="showLandDialog" width="460px">
      <ElDescriptions :column="1" border size="small" class="land-summary">
        <ElDescriptionsItem label="落架货架">{{ landForm.shelfNo }}</ElDescriptionsItem>
        <ElDescriptionsItem label="额定承重">{{ landForm.capacity }} kg</ElDescriptionsItem>
        <ElDescriptionsItem label="当前在架合计">{{ landForm.usedCapacity }} kg</ElDescriptionsItem>
        <ElDescriptionsItem label="当前剩余承重">
          <span :class="landForm.remainingCapacity > 0 ? 'code-bound' : 'code-unbound'">
            {{ landForm.remainingCapacity }} kg
          </span>
        </ElDescriptionsItem>
      </ElDescriptions>
      <ElForm :model="landForm" label-width="100px" style="margin-top: 12px">
        <ElFormItem label="托盘号">
          <ElInput v-model="landForm.palletNo" placeholder="请输入托盘号" />
        </ElFormItem>
        <ElFormItem label="毛重(kg)">
          <ElInputNumber v-model="landForm.grossWeight" :min="0.1" :step="10" :precision="2" style="width: 100%" />
        </ElFormItem>
        <ElFormItem label="操作人">
          <ElInput v-model="landForm.operator" placeholder="请输入操作人" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="landForm.remark" type="textarea" placeholder="请输入备注" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showLandDialog = false">取消</ElButton>
        <ElButton type="success" @click="handleLand">确认落架</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="托盘下架（退回承重）" v-model="showRemoveDialog" width="420px">
      <ElForm :model="removeForm" label-width="100px">
        <ElFormItem label="托盘号">{{ removeForm.palletNo }}</ElFormItem>
        <ElFormItem label="所在货架">{{ removeForm.shelfNo }}</ElFormItem>
        <ElFormItem label="毛重">将退回 {{ removeForm.grossWeight }} kg 承重</ElFormItem>
        <ElFormItem label="操作人">
          <ElInput v-model="removeForm.operator" placeholder="请输入操作人" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="removeForm.remark" type="textarea" placeholder="请输入下架备注" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showRemoveDialog = false">取消</ElButton>
        <ElButton type="warning" @click="handleRemove">确认下架</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="货架占用详情" v-model="showOccupancyDialog" width="720px">
      <div v-if="occupancyDetail">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="货架编号">{{ occupancyDetail.shelfNo }}</ElDescriptionsItem>
          <ElDescriptionsItem label="所属库区">{{ occupancyDetail.zone }}</ElDescriptionsItem>
          <ElDescriptionsItem label="货位编码">
            {{ occupancyDetail.locationCode || '未绑定（不可落架）' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="在架托数">{{ occupancyDetail.palletCount }} 托</ElDescriptionsItem>
          <ElDescriptionsItem label="额定承重">{{ occupancyDetail.capacity }} kg</ElDescriptionsItem>
          <ElDescriptionsItem label="在架合计">{{ occupancyDetail.usedCapacity }} kg</ElDescriptionsItem>
          <ElDescriptionsItem label="剩余承重" :span="2">
            <ElTag :type="occupancyTagType(occupancyDetail.usedCapacity, occupancyDetail.capacity)">
              {{ occupancyDetail.remainingCapacity }} kg
            </ElTag>
          </ElDescriptionsItem>
        </ElDescriptions>
        <ElTable :data="occupancyDetail.pallets" border stripe style="margin-top: 12px" max-height="320">
          <ElTableColumn prop="palletNo" label="托盘号" width="150" />
          <ElTableColumn prop="grossWeight" label="毛重(kg)" width="120">
            <template #default="{ row }">{{ Number(row.grossWeight).toFixed(2) }}</template>
          </ElTableColumn>
          <ElTableColumn prop="landedBy" label="落架人" width="110">
            <template #default="{ row }">{{ row.landedBy || '-' }}</template>
          </ElTableColumn>
          <ElTableColumn prop="landedAt" label="落架时间" width="180" />
          <ElTableColumn prop="landRemark" label="备注" />
        </ElTable>
      </div>
      <template #footer>
        <ElButton type="primary" @click="showOccupancyDialog = false">关闭</ElButton>
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
  min-height: calc(100vh - 120px);
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

.weight-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.remaining-weight {
  font-size: 12px;
  color: #606266;
}

.occupancy-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.occupancy-stats {
  margin-left: auto;
  font-size: 13px;
  color: #303133;
  background: #f4f6fa;
  border-radius: 6px;
  padding: 6px 12px;
}

.history-section {
  margin-top: 28px;
}

.history-section h3 {
  font-size: 15px;
  margin-bottom: 10px;
  color: #1e3a5f;
}

.history-search {
  display: flex;
  gap: 10px;
}

.land-summary {
  background: #fafcff;
}

:deep(.el-tree-node__content) {
  height: 40px;
}
</style>
