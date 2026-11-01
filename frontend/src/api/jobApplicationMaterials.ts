import request from '@/utils/request'
import { jobApplicationMaterialByteUrl } from '@/utils/authorizedFileUrl'
import { fetchAuthorizedFile } from '@/utils/authorizedFileDownload'

export interface ApplicationMaterialSelection {
  archiveIds: number[]
  resume: File | null
  certificates: File[]
}

export interface ApplicationMaterialFile {
  id: number
  fileName: string
  fileType: 'resume' | 'certificate'
  fileSize: number
}

export interface ApplicationMaterialSnapshot {
  snapshotAvailable: boolean
  accessRestricted: boolean
  submittedAt: string | null
  files: ApplicationMaterialFile[]
}

export type ApplicationMaterialScope = 'student' | 'company' | 'company-activity'

export const applicationMaterialBase = (id: number | string, scope: ApplicationMaterialScope) =>
  scope === 'company-activity' ? `/company/application-activities/${id}/materials`
    : `/${scope}/job-applications/${id}/materials`

export const getApplicationMaterialSnapshot = (id: number | string, scope: ApplicationMaterialScope) =>
  request.get<ApplicationMaterialSnapshot>(applicationMaterialBase(id, scope))

export const downloadApplicationMaterial = async (applicationId: number | string, fileId: number, scope: ApplicationMaterialScope) =>
  (await fetchAuthorizedFile(jobApplicationMaterialByteUrl(applicationId, fileId, scope))).blob()

export const applicationMaterialFormData = (application: unknown, selection: ApplicationMaterialSelection) => {
  const form = new FormData()
  form.append('application', new Blob([JSON.stringify(application)], { type: 'application/json' }))
  form.append('materials', new Blob([JSON.stringify({ archiveIds: selection.archiveIds })], { type: 'application/json' }))
  if (selection.resume) form.append('resume', selection.resume)
  selection.certificates.forEach(file => form.append('certificates', file))
  return form
}
