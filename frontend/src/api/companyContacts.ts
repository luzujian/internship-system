import request from '@/utils/request'

export interface CompanyContact {
  id: number
  name: string
  phone: string
  email: string
  positionCount?: number
}
export type ContactInput = Pick<CompanyContact, 'name' | 'phone' | 'email'>
export const listCompanyContacts = () => request<CompanyContact[]>({ url: '/company/contacts', method: 'get' })
export const saveCompanyContact = (id: number | null, data: ContactInput) => request<CompanyContact>({
  url: id === null ? '/company/contacts' : `/company/contacts/${id}`,
  method: id === null ? 'post' : 'put', data
})
export const deleteCompanyContact = (id: number) => request({ url: `/company/contacts/${id}`, method: 'delete' })
