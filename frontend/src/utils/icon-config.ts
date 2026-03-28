// 图标配置文件 - 只导入和注册应用中实际使用的图标
import {
  ArrowRight,
  Avatar,
  Collection,
  Document,
  Management,
  Operation,
  School,
  UserFilled,
  // 其他可能在应用中使用的图标
  HomeFilled,
  User,
  Lock,
  Menu,
  Search,
  Plus,
  Edit,
  Delete,
  Download,
  Upload,
  Refresh,
  Filter,
  Setting,
  Bell,
  Message,
  Check,
  Close,
  Warning,
  Calendar,
  Clock,
  Switch,
  View
  } from '@element-plus/icons-vue'

// 导出常用图标
export const commonIcons = {
  ArrowRight,
  Avatar,
  Collection,
  Document,
  Management,
  Operation,
  School,
  UserFilled,
  HomeFilled,
  User,
  Lock,
  Menu,
  Search,
  Plus,
  Edit,
  Delete,
  Download,
  Upload,
  Refresh,
  Filter,
  Setting,
  Bell,
  Message,
  Check,
  Close,
  Warning,
  Calendar,
  Clock,
  Switch,
  View
}

// 注册图标到应用实例的函数
export const registerIcons = (app: { component: (name: string, component: unknown) => void }) => {
  Object.entries(commonIcons).forEach(([key, component]) => {
    app.component(key, component)
  })
}

// 导出按需导入的工具函数
export const icons = commonIcons
