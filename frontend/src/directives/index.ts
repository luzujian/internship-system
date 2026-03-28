// 指令注册文件
// 用于统一注册和管理 Vue 应用中的自定义指令

// 导入懒加载指令
import lazyLoad from './lazyLoad'

// 导入权限指令
import permission from './permission'

// 导出所有指令的注册函数
export const registerDirectives = (app: { directive: (name: string, directive: unknown) => void }) => {
  // 注册图片懒加载指令
  app.directive('lazy-load', lazyLoad)

  // 注册权限指令
  app.directive('permission', permission)

  // 未来可以在这里添加更多自定义指令
  // app.directive('other-directive', otherDirective)
}

export default registerDirectives
