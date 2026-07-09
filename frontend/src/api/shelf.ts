import axios from 'axios'

const BASE_URL = '/api/shelf'

export interface Shelf {
  id: number
  shelfNo: string
  capacity: number
  zone: string
  status: number
  locationCode: string | null
  createdAt: string
  updatedAt: string
}

export interface ZoneTree {
  label: string
  value: string
  children: ShelfItem[]
}

export interface ShelfItem {
  id: number
  shelfNo: string
  locationCode: string
  label: string
}

export interface CodeMapping {
  shelfId: number
  shelfNo: string
  capacity: number
  zone: string
  locationCode: string
  bindTime: string | null
}

export interface CodeChangeLog {
  id: number
  shelfId: number
  shelfNo: string
  oldCode: string | null
  newCode: string | null
  operationType: number
  operator: string
  remark: string | null
  createdAt: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export async function createShelf(data: { shelfNo: string; capacity: number; zone: string }) {
  return axios.post<ApiResponse<Shelf>>(BASE_URL, data)
}

export async function getAllShelves() {
  return axios.get<ApiResponse<Shelf[]>>(BASE_URL)
}

export async function getShelfById(id: number) {
  return axios.get<ApiResponse<Shelf>>(`${BASE_URL}/${id}`)
}

export async function deleteShelf(id: number) {
  return axios.delete<ApiResponse<null>>(`${BASE_URL}/${id}`)
}

export async function bindCode(data: { shelfId: number; code: string; operator?: string; remark?: string }) {
  return axios.post<ApiResponse<Shelf>>(`${BASE_URL}/bind-code`, data)
}

export async function unbindCode(shelfId: number, operator?: string, remark?: string) {
  return axios.post<ApiResponse<Shelf>>(`${BASE_URL}/unbind-code/${shelfId}`, null, {
    params: { operator, remark }
  })
}

export async function reassignCode(data: { shelfId: number; newCode: string; operator?: string; remark?: string }) {
  return axios.post<ApiResponse<Shelf>>(`${BASE_URL}/reassign-code`, data)
}

export async function searchByCode(code: string) {
  return axios.get<ApiResponse<Shelf>>(`${BASE_URL}/search/by-code`, { params: { code } })
}

export async function getZoneTree() {
  return axios.get<ApiResponse<ZoneTree[]>>(`${BASE_URL}/zone-tree`)
}

export async function getAllCodeMappings() {
  return axios.get<ApiResponse<CodeMapping[]>>(`${BASE_URL}/code-mappings`)
}

export async function getAllChangeLogs() {
  return axios.get<ApiResponse<CodeChangeLog[]>>(`${BASE_URL}/change-logs`)
}

export async function exportCodeMapping() {
  return axios.get(`${BASE_URL}/export`, { responseType: 'blob' })
}
