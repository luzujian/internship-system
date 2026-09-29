export const terminal = status => ['SUCCEEDED', 'FAILED', 'CANCELLED', 'WAITING_INPUT'].includes(status)

// SSE boundaries can cross network chunks, including in the middle of a UTF-8 character.
export function sseParser(consume) {
  let buffer = ''
  return chunk => {
    buffer += chunk
    if (buffer.length > 4_000_000) throw new Error('助手结果过大，请缩小查询范围。')
    let boundary
    while ((boundary = /\r?\n\r?\n/.exec(buffer))) {
      const block = buffer.slice(0, boundary.index)
      buffer = buffer.slice(boundary.index + boundary[0].length)
      let event = 'message'; const data = []
      for (const line of block.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
      }
      if (data.length) consume(event, JSON.parse(data.join('\n')))
    }
  }
}

export function shouldDock(viewportWidth, _panelWidth) { return viewportWidth >= 1024 }

export function panelSize(viewportWidth, preferred = 420) {
  if (viewportWidth <= 700) return viewportWidth
  if (viewportWidth < 1024) return 360
  return Math.max(360, Math.min(preferred, 560, viewportWidth - 640))
}

export function responseOperation(task) {
  const allowed = task.allowedOperations || []
  if (allowed.includes('REFRESH_QUERY')) return 'REFRESH_QUERY'
  if (allowed.includes('REGENERATE')) return 'REGENERATE'
  if (task.responseKind === 'QUERY') return 'REFRESH_QUERY'
  // A knowledge card is evidence for an explanation, not a live business query.
  return (task.cards || []).some(card => !['knowledge_lookup', 'ask_user'].includes(card.tool)) ? 'REFRESH_QUERY' : 'REGENERATE'
}

export function taskVersions(tasks, task) {
  const byId = new Map(tasks.map(item => [item.id, item]))
  const rootOf = item => {
    if (item.rootMessageId) return item.rootMessageId
    const seen = new Set()
    while (item.editOf && byId.has(item.editOf) && !seen.has(item.id)) {
      seen.add(item.id); item = byId.get(item.editOf)
    }
    return item.id
  }
  const root = rootOf(task)
  return tasks.filter(item => rootOf(item) === root).sort((a, b) =>
    (a.revisionNumber ?? a.editRound ?? 0) - (b.revisionNumber ?? b.editRound ?? 0)
    || (a.createdAt ?? a.updatedAt) - (b.createdAt ?? b.updatedAt) || a.id.localeCompare(b.id))
}

export function jobSalary(row) {
  if (row.salaryStatus === 'NEEDS_CONFIRMATION') return '待核实'
  if (row.salaryUnit === '面议') return '面议'
  if (!['K/月', '元/天', '元/小时'].includes(row.salaryUnit)) return '待核实'
  const values = [row.salaryMin, row.salaryMax].map(value => value === '' || value == null ? null : Number(value))
  const [low, high] = values
  if (low == null || high == null || !Number.isFinite(low) || !Number.isFinite(high) || low < 0 || high <= 0 || high < low) return '待核实'
  return `${low} – ${high} ${row.salaryUnit}`
}

export function answerParts(text) {
  return String(text || '').replace(/^#{1,6}\s+/gm, '').split(/(\*\*[^*\n]+\*\*)/g)
    .filter(Boolean).map(part => part.startsWith('**') && part.endsWith('**')
      ? { text: part.slice(2, -2), bold: true } : { text: part, bold: false })
}

// Receipt URLs come from business callbacks; still require a registered page for this role.
export function localReceiptTarget(url, role, resolve) {
  const prefix = role === 'ROLE_STUDENT' ? '/student/' : role === 'ROLE_COMPANY' ? '/company/' : role === 'ROLE_ADMIN' ? '/admin/' : role?.startsWith('ROLE_TEACHER') ? '/teacher/' : null
  if (!prefix || typeof url !== 'string' || !url.startsWith(prefix) || /[\\\u0000-\u0020]|%(?:2f|5c|00)/i.test(url)) return null
  try {
    const parsed = new URL(url, 'https://local.invalid')
    if (parsed.origin !== 'https://local.invalid' || !parsed.pathname.startsWith(prefix)) return null
    const target = resolve(`${parsed.pathname}${parsed.search}${parsed.hash}`)
    if (!target.path.startsWith(prefix) || !target.matched?.length || target.matched.some(record => record.redirect || /:.*\(\.\*\)/.test(record.path))) return null
    return target.fullPath
  } catch { return null }
}

// Dynamic action→route mapping loaded from backend /api/agent/dicts
let actionRouteMap = {}
let actionRouteQueryKey = {}
export function setActionRouteMap(routeMap, queryKeyMap) {
  actionRouteMap = routeMap || {}
  actionRouteQueryKey = queryKeyMap || {}
}

// The model cannot supply arbitrary routes or scripts. All links resolve locally.
export function businessTarget(link, role) {
  const id = /^\d+$/.test(String(link.objectId)) ? String(link.objectId) : undefined
  // 优先使用后端字典返回的动态路由映射
  const dynamicPath = actionRouteMap[link.action]
  if (dynamicPath) {
    const queryKeyId = actionRouteQueryKey[link.action] || ''
    const query = id && queryKeyId ? { [queryKeyId]: id } : {}
    if (link.parameters?.applicationType) query.applicationType = link.parameters.applicationType
    return { path: dynamicPath, query }
  }
  // 回退到硬编码映射（后端字典未加载时的降级）
  if (role === 'ROLE_STUDENT') {
    switch (link.action) {
      case 'OPEN_JOB': return id ? { path: '/student/jobs', query: { positionId: id } } : null
      case 'OPEN_JOBS': return { path: '/student/jobs', query: {} }
      case 'OPEN_HOME': return { path: '/student/home' }
      case 'OPEN_APPLICATION': return { path: '/student/applications', query: id ? { applicationId: id, applicationType: link.parameters?.applicationType || 'job' } : {} }
      case 'OPEN_INTERVIEWS': return { path: '/student/interviews' }
      case 'OPEN_CONFIRMATION': return { path: '/student/internship-confirmation-form' }
      case 'OPEN_REFLECTIONS': return { path: '/student/internships' }
      case 'OPEN_APPRAISAL': return { path: '/student/internship-appraisal' }
      case 'OPEN_PROFILE': return { path: '/student/profile' }
    }
  }
  if (role?.startsWith('ROLE_TEACHER')) {
    switch (link.action) {
      case 'OPEN_STUDENT_TRACKING': return { path: '/teacher/student-tracking', query: id ? { agentStudentId: id } : {} }
      case 'OPEN_APPROVAL': return { path: '/teacher/approval' }
      case 'OPEN_APPRAISAL_COLLECTION': return { path: '/teacher/internship-appraisals' }
      case 'OPEN_EVALUATION': return { path: '/teacher/evaluation' }
    }
  }
  if (role === 'ROLE_COMPANY') {
    switch (link.action) {
      case 'OPEN_COMPANY_JOBS': return { path: '/company/jobs', query: id ? { agentPositionId: id } : {} }
      case 'OPEN_COMPANY_APPLICATIONS': return { path: '/company/application-view', query: id ? { agentApplicationId: id } : {} }
      case 'OPEN_COMPANY_CONFIRM': return { path: '/company/internship-confirm', query: id ? { agentConfirmationId: id } : {} }
    }
  }
  if (role === 'ROLE_ADMIN') {
    switch (link.action) {
      case 'OPEN_ADMIN_PERMISSIONS': return { path: '/admin/permissions' }
      case 'OPEN_ADMIN_COMPANIES': return { path: '/admin/companies', query: id ? { agentCompanyId: id } : {} }
      case 'OPEN_ADMIN_DASHBOARD': return { path: '/admin/dashboard' }
      case 'OPEN_ADMIN_STUDENTS': return { path: '/admin/student-users', query: id ? { agentStudentId: id } : {} }
    }
  }
  return null
}

function namedJobLink(link, items) {
  if (!['OPEN_JOB', 'OPEN_COMPANY_JOBS'].includes(link.action) || link.label.trim() !== '查看岗位') return link.label
  const row = items.find(item => String(item.id) === String(link.objectId))
  const title = String(row?.title ?? row?.positionName ?? row?.position ?? '').trim()
  if (!title) return link.label
  const company = String(row?.company ?? row?.companyName ?? '').trim()
  return company && company !== title ? `查看岗位：${title}（${company}）` : `查看岗位：${title}`
}

// Only offer navigation supported by the final answer, not every intermediate query.
export function relevantAnswerCards(task, role) {
  if (task.status !== 'SUCCEEDED' || !task.answer) return []
  const question = task.message || ''
  const asksApplicationCount = /(?:申请|投递).*(?:几|多少).*(?:岗|职)|(?:几|多少).*(?:岗|职).*(?:申请|投递)/.test(question)
  const asksNavigationOrDetails = /哪里|入口|打开|查看|详情|怎么|如何|逐项|每个|分别/.test(question)
  if (asksApplicationCount && !asksNavigationOrDetails) return []
  const answer = task.answer.replace(/\*\*/g, '')
  const seen = new Set()
  const seenJobIds = new Set()
  const mentioned = row => {
    const name = ['title', 'positionName', 'position', 'studentName', 'name', 'companyName']
      .map(key => row[key]).find(value => typeof value === 'string' && value.trim().length >= 2)
    return Boolean(name && answer.includes(name.trim()))
  }
  return (task.cards || []).filter(card => card.status === 'OK').map(card => {
    const items = (card.items || []).filter(mentioned)
    const links = (card.links || []).filter(link => {
      const target = businessTarget(link, role)
      if (!target) return false
      if (link.objectId != null) {
        // Object links must identify a record actually mentioned in the answer.
        if (!items.some(row => String(row.id) === String(link.objectId)
          && (!link.parameters?.objectType || row.objectType === link.parameters.objectType))) return false
      } else {
        const label = link.label.replace(/^(前往|进入|查看|打开|去|回)/, '').replace(/核对.*$/, '')
        if (label.length < 2 || !answer.includes(label)) return false
      }
      const key = JSON.stringify(target)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    // Search and detail tools can return the same position in one answer.
    // Keep its navigation label, but offer only one application entry per job.
    const uniqueItems = ['jobs_search', 'jobs_details'].includes(card.tool) ? items.filter(row => {
      if (row.id == null) return false
      const id = String(row.id)
      if (seenJobIds.has(id)) return false
      seenJobIds.add(id)
      return true
    }) : items
    return { ...card, links: links.map(link => ({ ...link, label: namedJobLink(link, items) })), items: uniqueItems }
  }).filter(card => card.links.length || card.items.length)
}
// Read-only navigation may follow relevant evidence. Business writes require
// an explicit request in the current turn; a query result alone is not intent.
export function allowsWriteAction(task, action) {
  if (!task || task.status !== 'SUCCEEDED' || !task.message || !action) return false
  const question = String(task.message).trim()
  if (!question || /(?:几|多少|几个|数量|一共|共计).*(?:岗位|职位|申请|投递)/.test(question)) return false
  if (action === 'JOB_APPLY') {
    return /(?:帮我|我要|我想|希望|请|准备|打算).{0,12}(?:投递|申请|报名|应聘)/.test(question)
      || /(?:投递|申请|报名|应聘).{0,12}(?:这个|该|岗位|职位)/.test(question)
  }
  return false
}
