export interface AnnouncementMessage {
  id?: number | string
  title?: string
  content?: string
  publisher?: string
  publisherName?: string
  publisher_name?: string
  publisherDisplayName?: string
  publisherRole?: string
  publisherRoleText?: string
  publisherRoleName?: string
  publisher_role?: string
  role?: string
  publishDate?: string | number | Date | null
  publishTime?: string | number | Date | null
  time?: string | number | Date | null
  attachments?: unknown
  isRead?: boolean
}
