import request from '@/utils/request'

export interface ResourceDocument {
  id: number
  title: string
  description?: string
  url?: string
  fileType?: string
  fileSize?: number
  downloadCount?: number
  uploaderId?: number
  uploaderType?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface ResourceDocumentResponse {
  code: number
  data: ResourceDocument | ResourceDocument[] | { rows: ResourceDocument[]; total: number }
  msg?: string
}

type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    msg?: string
  }
}

export const getAllResourceDocuments = () => {
  return request.get<ResourceDocument[]>('/resource-documents') as unknown as AxiosResponseWithMeta<ResourceDocument[]>
}

export const getResourceDocumentById = (id: number) => {
  return request.get<ResourceDocument>(`/resource-documents/${id}`)
}

export const getResourceDocumentsByPage = (params: {
  page?: number
  pageSize?: number
  title?: string
  fileType?: string
  status?: number
}) => {
  return request.get<ResourceDocument[]>('/resource-documents/page', { params })
}

export const addResourceDocument = (data: Omit<ResourceDocument, 'id' | 'createTime' | 'updateTime'>) => {
  return request.post('/resource-documents', data)
}

export const updateResourceDocument = (id: number, data: Partial<ResourceDocument>) => {
  return request.put(`/resource-documents/${id}`, data)
}

export const deleteResourceDocument = (id: number) => {
  return request.delete(`/resource-documents/${id}`)
}

export const batchDeleteResourceDocuments = (ids: number[]) => {
  return request.delete('/resource-documents/batch', { data: { ids } })
}

export const searchResourceDocuments = (params: {
  keyword?: string
  fileType?: string
  page?: number
  pageSize?: number
}) => {
  return request.get<ResourceDocument[]>('/resource-documents/search', { params })
}

export const uploadResourceDocument = (formData: FormData) => {
  return request.post('/resource-documents/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const downloadResourceDocument = (id: number) => {
  return request.get(`/resource-documents/download/${id}`, {
    responseType: 'blob'
  })
}
