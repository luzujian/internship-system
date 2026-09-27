import { h } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { InitialCredential } from '@/utils/initialCredentialPayload'

export const showInitialCredentials = async (credentials: InitialCredential[]): Promise<void> => {
  if (credentials.length === 0) return
  const content = h('div', [
    h('p', '这些初始凭据只在本次创建或导入后显示。请现在通过安全渠道交付，关闭后无法在页面找回。'),
    h('div', { style: 'max-height: 50vh; overflow: auto; user-select: text;' }, credentials.map(({ username, password }) =>
      h('div', { style: 'padding: 8px 0; border-bottom: 1px solid #eee;' }, [
        h('div', ['账号：', h('strong', username)]),
        h('div', ['初始密码：', h('code', { style: 'user-select: text; word-break: break-all;' }, password)])
      ])))
  ])
  await ElMessageBox.alert(content, '一次性初始凭据', {
    confirmButtonText: '我已记录并关闭',
    closeOnClickModal: false,
    closeOnPressEscape: false,
    showClose: false,
    dangerouslyUseHTMLString: false
  }).catch(() => undefined)
}
