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
  ElCheckbox,
  ElCheckboxGroup,
  ElTag,
  ElMessage,
  ElTabs,
  ElTabPane,
  ElTooltip,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElAlert
} from 'element-plus'
import {
  getShiftZoneSummary,
  openShift,
  getShift,
  getShiftsByZone,
  getShiftLogs,
  updateShiftCheck,
  updateShiftSeal,
  submitShiftHandover,
  type ShiftHandover,
  type ShiftZoneSummary
} from '@/api/shift'

// ===================== 列表与流水 =====================
const zoneSummaries = ref<ShiftZoneSummary[]>([])
const handoverLogs = ref<ShiftHandover[]>([])
const activeTab = ref('zones')

// 某库区历史班次（总览里点“该库区班次”）
const zoneHistory = ref<ShiftHandover[]>([])
const zoneHistoryName = ref('')
const showZoneHistoryDialog = ref(false)

// ===================== 开班弹窗 =====================
const showOpenDialog = ref(false)
const openForm = reactive({
  zone: '',
  shiftType: '白班',
  outgoingName: ''
})

// ===================== 交班作业弹窗（核心） =====================
const showWorkDialog = ref(false)
const current = ref<ShiftHandover | null>(null)
const sealInput = ref('')
const incomingName = ref('')
const handoverNote = ref('')
const saving = ref(false)

// 已勾选的必检项编码集合（可勾列表，是否允许交班由后端最终裁决）
const checkedCodes = ref<string[]>([])

const isHanded = computed(() => current.value?.status === 2)

/** 把班次数据灌进作业表单 */
function applyShift(shift: ShiftHandover) {
  current.value = shift
  checkedCodes.value = shift.checkItems.filter((i) => i.checked === 1).map((i) => i.itemCode)
  sealInput.value = shift.sealNo || ''
  incomingName.value = shift.incomingName || ''
  handoverNote.value = shift.handoverNote || ''
}

// ===================== 加载 =====================
async function loadZoneSummary() {
  try {
    const res = await getShiftZoneSummary()
    if (res.data.code === 200) {
      zoneSummaries.value = res.data.data
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载库区总览失败')
  }
}

async function loadHandoverLogs() {
  try {
    const res = await getShiftLogs()
    if (res.data.code === 200) {
      handoverLogs.value = res.data.data
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载交班流水失败')
  }
}

async function refreshCurrent() {
  if (!current.value) return
  try {
    const res = await getShift(current.value.id)
    if (res.data.code === 200) {
      applyShift(res.data.data)
    }
  } catch {
    // 作业弹窗内静默：下次操作会再拿到最新状态
  }
}

// ===================== 开班 =====================
function openOpenDialog() {
  openForm.zone = ''
  openForm.shiftType = '白班'
  openForm.outgoingName = ''
  showOpenDialog.value = true
}

async function handleOpenShift() {
  if (!openForm.zone.trim()) {
    ElMessage.warning('请填写或选择库区')
    return
  }
  if (!openForm.outgoingName.trim()) {
    ElMessage.warning('请填写当班人姓名')
    return
  }
  try {
    const res = await openShift({
      zone: openForm.zone.trim(),
      shiftType: openForm.shiftType,
      outgoingName: openForm.outgoingName.trim()
    })
    if (res.data.code === 200) {
      ElMessage.success('开班成功，请先勾齐必检项再交班')
      showOpenDialog.value = false
      applyShift(res.data.data)
      showWorkDialog.value = true
      loadZoneSummary()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '开班失败')
    loadZoneSummary()
  }
}

// ===================== 点检勾选 =====================
async function handleCheckChange(code: string, checked: boolean | string | number) {
  if (!current.value || isHanded.value) {
    await refreshCurrent()
    return
  }
  const isChecked = checked === true
  saving.value = true
  try {
    const res = await updateShiftCheck({ shiftId: current.value.id, itemCode: code, checked: isChecked })
    if (res.data.code === 200) {
      applyShift(res.data.data)
    } else {
      // 后端拒绝（例如本班已被交班）：回滚勾选并刷新真实状态
      ElMessage.error(res.data.message)
      await refreshCurrent()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '点检保存失败')
    await refreshCurrent()
  } finally {
    saving.value = false
  }
}

/** 保存铅封号（允许由一个号改成另一个号，但不许清空；后端同样裁决） */
async function handleSaveSeal() {
  if (!current.value) return
  if (!sealInput.value.trim()) {
    ElMessage.warning('铅封号不能空着，请点验铅封后填写')
    sealInput.value = current.value.sealNo || ''
    return
  }
  saving.value = true
  try {
    const res = await updateShiftSeal({
      shiftId: current.value.id,
      sealNo: sealInput.value.trim()
    })
    if (res.data.code === 200) {
      ElMessage.success('铅封号已保存')
      applyShift(res.data.data)
    } else {
      ElMessage.error(res.data.message)
      await refreshCurrent()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '铅封号保存失败')
    await refreshCurrent()
  } finally {
    saving.value = false
  }
}

// ===================== 交班 =====================
async function handleSubmitHandover() {
  if (!current.value) return
  if (isHanded.value) {
    ElMessage.warning('本班已经交班，点检与铅封号已冻结')
    return
  }
  // 前端先给一道即时校验（可勾列表不代表允许漏勾），真正裁决在后端锁内事务
  const missing = current.value.checkItems
    .filter((i) => checkedCodes.value.indexOf(i.itemCode) === -1)
    .map((i) => i.itemName)
  if (missing.length > 0) {
    ElMessage.error('必检项还没勾齐，不能交班，未勾：' + missing.join('、'))
    return
  }
  if (!sealInput.value.trim()) {
    ElMessage.error('铅封号空着，不能交班')
    return
  }
  if (!incomingName.value.trim()) {
    ElMessage.error('请填写接班人姓名')
    return
  }
  if (incomingName.value.trim() === current.value.outgoingName) {
    ElMessage.error(`接班人不能与当班人是同一个人（${incomingName.value.trim()}），本班交不出去`)
    return
  }

  // 铅封号若改过还没点“保存铅封号”，先确保铅封号落库（后端只认库内铅封号）
  if (sealInput.value.trim() !== (current.value.sealNo || '')) {
    ElMessage.warning('铅封号有改动，请先点“保存铅封号”再交班')
    return
  }

  try {
    const res = await submitShiftHandover({
      shiftId: current.value.id,
      incomingName: incomingName.value.trim(),
      handoverNote: handoverNote.value.trim()
    })
    if (res.data.code === 200) {
      applyShift(res.data.data)
      ElMessage.success(
        `交班成功：库区【${res.data.data.zone}】${res.data.data.shiftType} 已由 ${res.data.data.outgoingName} 交给 ${res.data.data.incomingName}，本班已冻结`
      )
      loadZoneSummary()
      loadHandoverLogs()
    } else {
      ElMessage.error(res.data.message)
      await refreshCurrent()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '交班失败，本班未交出')
    await refreshCurrent()
  }
}

// ===================== 进入作业 / 查看 =====================
async function openWorkDialog(shiftId: number) {
  try {
    const res = await getShift(shiftId)
    if (res.data.code === 200) {
      applyShift(res.data.data)
      showWorkDialog.value = true
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载班次失败')
  }
}

async function showZoneHistory(zone: string) {
  zoneHistoryName.value = zone
  try {
    const res = await getShiftsByZone(zone)
    if (res.data.code === 200) {
      zoneHistory.value = res.data.data
      showZoneHistoryDialog.value = true
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载库区班次失败')
  }
}

function handleTabChange(name: string | number) {
  if (name === 'logs') {
    loadHandoverLogs()
  } else if (name === 'zones') {
    loadZoneSummary()
  }
}

onMounted(() => {
  loadZoneSummary()
})
</script>

<template>
  <div class="shift-handover">
    <div class="shift-toolbar">
      <div class="shift-tip">
        交班顺序：先按库区把<b>外观、门帘、铅封号</b>三项必检勾齐并登记铅封号，
        再填<b>接班人姓名</b>和交班说明；漏勾、铅封号空着、接班人与当班人同名，这一班都交不出去。
      </div>
      <ElButton type="primary" @click="openOpenDialog">开新班（当班人接班开班）</ElButton>
    </div>

    <ElTabs v-model="activeTab" type="card" @tab-change="handleTabChange">
      <!-- ================= 库区总览：按库区反查当班人 ================= -->
      <ElTabPane label="库区总览 / 反查当班人" name="zones">
        <ElTable :data="zoneSummaries" border stripe>
          <ElTableColumn prop="zone" label="库区" width="120" />
          <ElTableColumn label="本班状态" width="130">
            <template #default="{ row }">
              <ElTag v-if="row.hasOpenShift" type="warning">进行中（未交班）</ElTag>
              <ElTag v-else type="success">上一班已交完</ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="班次" width="90">
            <template #default="{ row }">{{ row.currentShiftType || '-' }}</template>
          </ElTableColumn>
          <ElTableColumn label="当前当班人" width="130">
            <template #default="{ row }">
              <span :class="row.currentOutgoingName ? 'person-on-duty' : 'person-none'">
                {{ row.currentOutgoingName || '（无进行中的班）' }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="currentShiftId" label="本班次ID" width="100">
            <template #default="{ row }">{{ row.currentShiftId ?? '-' }}</template>
          </ElTableColumn>
          <ElTableColumn prop="currentOpenedAt" label="开班时间" width="180">
            <template #default="{ row }">{{ row.currentOpenedAt || '-' }}</template>
          </ElTableColumn>
          <ElTableColumn label="上一班接班人" width="130">
            <template #default="{ row }">{{ row.lastIncomingName || '-' }}</template>
          </ElTableColumn>
          <ElTableColumn label="累计交班" width="100">
            <template #default="{ row }">{{ row.handedCount }} 次</template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <ElButton
                v-if="row.hasOpenShift"
                type="primary"
                size="small"
                @click="openWorkDialog(row.currentShiftId)"
              >
                继续点检 / 交班
              </ElButton>
              <ElTooltip
                v-else
                content="上一班已交完，可由当班人开新班"
                placement="top"
              >
                <span>
                  <ElButton type="success" size="small" disabled>开新班</ElButton>
                </span>
              </ElTooltip>
              <ElButton size="small" @click="showZoneHistory(row.zone)">该库区班次</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <ElEmpty v-if="zoneSummaries.length === 0" description="还没有库区与班次，先开一个班" />
      </ElTabPane>

      <!-- ================= 交班流水 ================= -->
      <ElTabPane label="交班流水" name="logs">
        <ElTable :data="handoverLogs" border stripe>
          <ElTableColumn prop="id" label="班次ID" width="90" />
          <ElTableColumn prop="zone" label="库区" width="100" />
          <ElTableColumn prop="shiftType" label="班次" width="80" />
          <ElTableColumn prop="outgoingName" label="交班人(当班)" width="130" />
          <ElTableColumn prop="incomingName" label="接班人" width="130" />
          <ElTableColumn prop="sealNo" label="铅封号" width="140" />
          <ElTableColumn label="必检" width="90">
            <template #default="{ row }">
              <ElTag type="success" size="small">{{ row.checkedItems }}/{{ row.totalItems }}</ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="handoverNote" label="交班说明" min-width="200" show-overflow-tooltip />
          <ElTableColumn prop="handedAt" label="交班时间" width="180" />
          <ElTableColumn label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <ElButton size="small" @click="openWorkDialog(row.id)">查看</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <ElEmpty v-if="handoverLogs.length === 0" description="还没有已交班的班" />
      </ElTabPane>
    </ElTabs>

    <!-- ================= 开班弹窗 ================= -->
    <ElDialog title="库区开班（白班/夜班）" v-model="showOpenDialog" width="440px">
      <ElForm :model="openForm" label-width="100px">
        <ElFormItem label="库区">
          <ElSelect
            v-model="openForm.zone"
            placeholder="选择已有库区，或直接输入新库区名"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <ElOption v-for="z in zoneSummaries" :key="z.zone" :label="z.zone" :value="z.zone" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="班次">
          <ElSelect v-model="openForm.shiftType" style="width: 100%">
            <ElOption label="白班" value="白班" />
            <ElOption label="夜班" value="夜班" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="当班人">
          <ElInput v-model="openForm.outgoingName" placeholder="本班当班人姓名（交班责任人）" />
        </ElFormItem>
      </ElForm>
      <div class="dialog-hint">
        同一库区上一班还没交班时，开新会被系统拦住，先开的那一班保持原样。
      </div>
      <template #footer>
        <ElButton @click="showOpenDialog = false">取消</ElButton>
        <ElButton type="primary" @click="handleOpenShift">开班</ElButton>
      </template>
    </ElDialog>

    <!-- ================= 交班作业弹窗 ================= -->
    <ElDialog
      :title="`库区交接班作业 - ${current?.zone || ''} ${current?.shiftType || ''}（班次ID ${current?.id ?? ''}）`"
      v-model="showWorkDialog"
      width="680px"
      :close-on-click-modal="false"
    >
      <div v-if="current">
        <ElAlert v-if="isHanded" type="success" :closable="false" class="frozen-alert"
          :title="`本班已于 ${current.handedAt} 交班：${current.outgoingName} → ${current.incomingName}，点检勾选与铅封号已冻结，不可再改`"
          show-icon />

        <!-- 当班信息 -->
        <ElDescriptions :column="3" border size="small" class="work-desc">
          <ElDescriptionsItem label="库区">{{ current.zone }}</ElDescriptionsItem>
          <ElDescriptionsItem label="班次">{{ current.shiftType }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <ElTag :type="isHanded ? 'success' : 'warning'" size="small">{{ current.statusText }}</ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="当班人">{{ current.outgoingName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="开班时间">{{ current.openedAt }}</ElDescriptionsItem>
          <ElDescriptionsItem label="交班时间">{{ current.handedAt || '-' }}</ElDescriptionsItem>
        </ElDescriptions>

        <!-- 第一步：必检项可勾列表 -->
        <div class="section-title">
          一、必检项点检（逐项勾选）
          <ElTag size="small" :type="current.allChecked ? 'success' : 'danger'" class="step-tag">
            已勾 {{ current.checkedItems }}/{{ current.totalItems }}
          </ElTag>
        </div>
        <ElCheckboxGroup v-model="checkedCodes" class="check-list" :disabled="isHanded || saving">
          <div v-for="item in current.checkItems" :key="item.itemCode" class="check-row">
            <ElCheckbox
              :value="item.itemCode"
              @change="(val: any) => handleCheckChange(item.itemCode, val)"
            >
              <span class="check-name">{{ item.itemName }}</span>
            </ElCheckbox>
            <span v-if="item.itemCode === 'SEAL'" class="seal-inline">
              <ElInput
                v-model="sealInput"
                placeholder="填写铅封号"
                size="small"
                style="width: 200px"
                :disabled="isHanded || saving"
              />
              <ElButton
                size="small"
                type="primary"
                plain
                :disabled="isHanded || saving"
                @click="handleSaveSeal"
              >保存铅封号</ElButton>
            </span>
            <span v-if="item.checked === 1 && item.checkedAt" class="checked-at">
              {{ item.checkedAt }} 勾
            </span>
          </div>
        </ElCheckboxGroup>

        <!-- 第二步：接班人与交班说明 -->
        <div class="section-title">二、接班人及交班说明</div>
        <ElForm label-width="100px" class="handover-form">
          <ElFormItem label="接班人姓名">
            <ElInput
              v-model="incomingName"
              placeholder="接班人必须与当班人不是同一个人"
              :disabled="isHanded"
            />
          </ElFormItem>
          <ElFormItem label="交班说明">
            <ElInput
              v-model="handoverNote"
              type="textarea"
              :rows="3"
              placeholder="把过去靠口头交代的事项写在这里（门帘异常、铅封核对、库区未尽事项等）"
              :disabled="isHanded"
            />
          </ElFormItem>
        </ElForm>

        <div v-if="!isHanded" class="submit-hint">
          三项必检全部勾齐、铅封号已保存、接班人非当班人本人后才能交出本班。
        </div>
      </div>
      <template #footer>
        <ElButton @click="showWorkDialog = false">{{ isHanded ? '关闭' : '取消' }}</ElButton>
        <ElButton
          v-if="!isHanded"
          type="success"
          :loading="saving"
          @click="handleSubmitHandover"
        >确认交班（本班冻结）</ElButton>
      </template>
    </ElDialog>

    <!-- ================= 某库区班次列表 ================= -->
    <ElDialog :title="`库区【${zoneHistoryName}】班次（同库区一次只能有一个进行中的班）`"
      v-model="showZoneHistoryDialog" width="760px">
      <ElTable :data="zoneHistory" border stripe max-height="420">
        <ElTableColumn prop="id" label="班次ID" width="90" />
        <ElTableColumn prop="shiftType" label="班次" width="80" />
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="row.status === 2 ? 'success' : 'warning'" size="small">
              {{ row.statusText }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="outgoingName" label="当班人" width="110" />
        <ElTableColumn label="接班人" width="110">
          <template #default="{ row }">{{ row.incomingName || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn label="必检" width="80">
          <template #default="{ row }">{{ row.checkedItems }}/{{ row.totalItems }}</template>
        </ElTableColumn>
        <ElTableColumn label="铅封号" width="130">
          <template #default="{ row }">{{ row.sealNo || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="openedAt" label="开班时间" width="170" />
        <ElTableColumn prop="handedAt" label="交班时间" width="170">
          <template #default="{ row }">{{ row.handedAt || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <ElButton size="small" @click="openWorkDialog(row.id)">
              {{ row.status === 2 ? '查看' : '继续' }}
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElDialog>
  </div>
</template>

<style scoped>
.shift-handover {
  width: 100%;
}

.shift-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}

.shift-tip {
  font-size: 13px;
  color: #4a5568;
  background: #f4f6fa;
  border-left: 3px solid #2c5282;
  padding: 8px 12px;
  border-radius: 4px;
  line-height: 1.6;
}

.person-on-duty {
  color: #b7791f;
  font-weight: 600;
}

.person-none {
  color: #909399;
}

.dialog-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.frozen-alert {
  margin-bottom: 12px;
}

.work-desc {
  margin-bottom: 14px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e3a5f;
  margin: 14px 0 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-tag {
  font-weight: 500;
}

.check-list {
  width: 100%;
}

.check-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  border-bottom: 1px dashed #ebeef5;
  width: 100%;
}

.check-name {
  font-size: 14px;
  color: #303133;
}

.seal-inline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.checked-at {
  font-size: 12px;
  color: #67c23a;
}

.handover-form {
  margin-top: 4px;
}

.submit-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #b7791f;
  background: #fffbeb;
  border-radius: 4px;
  padding: 8px 12px;
  line-height: 1.6;
}
</style>
