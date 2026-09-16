import axios from 'axios'

const BASE_URL = '/api/shift'

// ========== 库区交接班 ==========

export interface ShiftCheckItem {
  id: number
  shiftId: number
  /** APPEARANCE 外观 / DOOR_CURTAIN 门帘 / SEAL 铅封 */
  itemCode: string
  itemName: string
  /** 0未勾 1已勾 */
  checked: number
  checkedAt: string | null
  remark: string | null
}

export interface ShiftHandover {
  id: number
  zone: string
  shiftType: string
  /** 1进行中 2已交班 */
  status: number
  statusText: string
  outgoingName: string
  incomingName: string | null
  sealNo: string | null
  handoverNote: string | null
  openedAt: string
  handedAt: string | null
  totalItems: number
  checkedItems: number
  allChecked: boolean
  checkItems: ShiftCheckItem[]
}

export interface ShiftZoneSummary {
  zone: string
  hasOpenShift: boolean
  currentOutgoingName: string | null
  currentShiftType: string | null
  currentShiftId: number | null
  currentOpenedAt: string | null
  lastIncomingName: string | null
  handedCount: number
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 库区总览：按库区反查当班人 */
export async function getShiftZoneSummary() {
  return axios.get<ApiResponse<ShiftZoneSummary[]>>(BASE_URL + '/zones')
}

/** 开班 */
export async function openShift(data: { zone: string; shiftType: string; outgoingName: string }) {
  return axios.post<ApiResponse<ShiftHandover>>(BASE_URL + '/open', data)
}

/** 班次详情（含必检项明细） */
export async function getShift(id: number) {
  return axios.get<ApiResponse<ShiftHandover>>(`${BASE_URL}/${id}`)
}

/** 某库区全部班次 */
export async function getShiftsByZone(zone: string) {
  return axios.get<ApiResponse<ShiftHandover[]>>(BASE_URL + '/zone/' + encodeURIComponent(zone))
}

/** 交班流水：全部已交班班次 */
export async function getShiftLogs() {
  return axios.get<ApiResponse<ShiftHandover[]>>(BASE_URL + '/logs')
}

/** 勾选/取消必检项 */
export async function updateShiftCheck(data: {
  shiftId: number
  itemCode: string
  checked: boolean
  remark?: string
}) {
  return axios.post<ApiResponse<ShiftHandover>>(BASE_URL + '/check', data)
}

/** 登记/修改铅封号（不允许清空） */
export async function updateShiftSeal(data: { shiftId: number; sealNo: string }) {
  return axios.post<ApiResponse<ShiftHandover>>(BASE_URL + '/seal', data)
}

/** 交班 */
export async function submitShiftHandover(data: {
  shiftId: number
  incomingName: string
  handoverNote?: string
}) {
  return axios.post<ApiResponse<ShiftHandover>>(BASE_URL + '/handover', data)
}
