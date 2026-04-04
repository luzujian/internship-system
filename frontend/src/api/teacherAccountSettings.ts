import request from '../utils/request'

export interface UserInfo {
  id: number
  username: string
  name: string
  teacherId: string
  phone: string
  email: string
  gender: number
  departmentId: string
  teacherType: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
  phoneCode: string
}

export interface UpdateContactRequest {
  phone?: string
  email?: string
  verifyCode?: string
  requestId?: string
}

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

export const accountSettingsApi = {
  getAccountInfo: async () => {
    const response = await request.get('/account-settings/info')
    return response.data
  },

  sendVerifyCode: (phone: string) => {
    return request.post('/account-settings/send-verify-code', { phone })
  },

  changePassword: (data: ChangePasswordRequest) => {
    return request.post('/account-settings/change-password', data)
  },

  updateContact: (data: UpdateContactRequest) => {
    return request.post('/account-settings/update-contact', data)
  },

  logout: () => {
    return request.post('/account-settings/logout')
  }
}
