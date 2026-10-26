export interface BusinessObject { type: string; id: string }
export interface MessageAttachment { name: string; size?: number }
export interface MessageAttachmentPreview { contextId: string; files: MessageAttachment[] }
export interface PageContext {
  pageKey: string; route: string; title: string; view: string
  object: BusinessObject | null; selectedObjects: BusinessObject[]
  filters: Record<string, unknown>; revision: number
}
export interface Actor { ownerKey: string; id: string | number; role: string; name: string; readOnly: boolean }
export interface AgentLink { label: string; action: string; objectId?: string; parameters: Record<string, string> }
export type AgentUiAction =
  | { id: string; type: 'NAVIGATE'; targetId: string; continue?: boolean }
  | { id: string; type: 'FORM_FILL'; formId: string; instanceId: number; assignments: FillAssignment[]; questions?: string[]; skippedFields?: FillSkipped[] }
  | { id: string; type: 'AUTO_TASK_PREVIEW' | 'CAPABILITIES_PREVIEW'; preview: Record<string, unknown> }
export interface ResultCard {
  tool: string; title: string; status: string; observedAt: string; scope: string
  items: Record<string, unknown>[]; total: number; hasMore: boolean
  facts: Record<string, unknown>; links: AgentLink[]; warnings: string[]
}
export interface AgentTask {
  id: string; sessionId: string; ownerKey: string; message: string; status: string; answer: string
  pageContext: PageContext; version: number; updatedAt: number; errorCode?: string; errorMessage?: string
  editOf?: string | null; editRound?: number;
  createdAt?: number; rootMessageId?: string; revisionNumber?: number
  generationKind?: string; operation?: string; responseKind?: string; allowedOperations?: string[]
  parentTaskId?: string
  harness?: string; attachmentContextId?: string | null
  runtime?: { engine: string; workflowId: string; pendingQuestion?: unknown; revision?: number }
  uiActions?: AgentUiAction[]
  importReceipt?: { actionId: string; successCount: number; skippedCount: number; blockedCount: number; credentialCount: number } | null
  steps: { tool: string; title: string; status: string; durationMs: number; errorCode?: string }[]
  cards: ResultCard[]
}
export interface Capabilities { enabled: boolean; actor: Actor; mode: string; engine?: string; attachmentImport?: boolean; resume?: boolean; harness?: string; tools: { name: string; description: string }[]; capabilities?: Record<string, boolean> }

// ===== 智能填表 =====
export interface FormFieldDescriptor {
  prop: string
  label: string
  type: 'input' | 'textarea' | 'select' | 'date' | 'datetime' | 'radio' | 'number' | 'upload'
  options?: { value: string | number; label: string }[]
  required?: boolean
  patternHint?: string
  /** 仅原控件允许创建选项时开放自由文本。 */
  allowCustom?: boolean
  min?: number
  max?: number
  integer?: boolean
  /** 字段绑定形态：'date' 写 Date 对象，'string' 写字符串，'array' 供多选控件（el-select multiple）接收数组 */
  valueType?: 'date' | 'string' | 'number' | 'array'
}
export interface FillAssignment { prop: string; label?: string; value: string; confidence: number; operation?: 'SET' | 'CLEAR'; overwriteAllowed?: boolean }
export interface FillSkipped { prop: string; label?: string; reason: string }
export interface FormValidationSummary { missingFields: string[]; validationErrors: string[] }
export interface FormBusinessReceipt {
  formId: string; instanceId: number; createdAt: number
  status: 'saved' | 'submitted' | 'published'
  label?: string; url?: string; object?: BusinessObject | null
  /** Original business request snapshot, captured before awaiting save; never inferred from later edits. */
  savedValues?: Record<string, unknown>
}
export interface FillEvent {
  id: string
  kind: 'fill'
  sourceTaskId?: string
  formId: string
  formTitle: string
  message: string
  intent: 'form_fill' | 'chat'
  assignments: FillAssignment[]
  questions: string[]
  skippedFields: FillSkipped[]
  applied: boolean
  undone?: boolean
  /** 撤销时因用户在 AI 填写后再次手动修改而未回滚的字段标签 */
  preservedFields?: string[]
  error?: string
  createdAt: number
  completedAt?: number
  sessionId?: string | null
  fillBatchId?: string
  missingFields?: string[]
  validationErrors?: string[]
  beforeValues?: Record<string, unknown>
  afterValues?: Record<string, unknown>
  businessStatus?: 'suggested' | 'filled' | 'saved' | 'submitted' | 'published'
  receipt?: FormBusinessReceipt
  expectedInstanceId?: number
  activeInstanceId?: number | null
  decision?: 'APPLY' | 'REJECT' | 'CANCEL'
}
