<script setup>
/**
 * 行操作收敛组件
 *
 * - 桌面端（> 768px）或按钮数 <= threshold：原样渲染插槽按钮
 * - 手机端且按钮数 > threshold：收敛为 72–96px 的「更多」下拉菜单
 *   下拉内渲染同一批插槽按钮，因此原处理函数全部复用
 *
 * 用法：把 el-table-column 操作列里的按钮包进 <MobileMoreActions>...</MobileMoreActions>
 */
import { computed, useSlots, Fragment, Comment, Text } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { useIsMobile } from '@/composables/useIsMobile'

const props = defineProps({
  /** 超过该按钮数量时在手机端收敛 */
  threshold: { type: Number, default: 2 },
  /** 「更多」按钮宽度（72–96px） */
  width: { type: Number, default: 84 }
})

const slots = useSlots()
const isMobile = useIsMobile()

const flatten = (vnodes) => {
  const out = []
  for (const vnode of vnodes || []) {
    if (!vnode) continue
    if (vnode.type === Fragment) {
      out.push(...flatten(vnode.children))
    } else if (vnode.type === Comment) {
      // v-if=false 产生的注释节点，跳过
      continue
    } else if (vnode.type === Text && !String(vnode.children ?? '').trim()) {
      continue
    } else {
      out.push(vnode)
    }
  }
  return out
}

const buttons = computed(() => flatten(slots.default?.()))
const collapsed = computed(() => isMobile.value && buttons.value.length > props.threshold)
</script>

<template>
  <div v-if="!collapsed" class="row-actions">
    <slot />
  </div>
  <div v-else class="row-actions row-actions--mobile">
    <el-dropdown trigger="click" placement="bottom-end">
      <el-button
        size="small"
        class="row-actions-more"
        :style="{ width: Math.min(96, Math.max(72, width)) + 'px' }"
        aria-label="更多操作"
      >
        更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu class="row-actions-dropdown-menu">
          <div class="row-actions-dropdown">
            <component v-for="(btn, i) in buttons" :is="btn" :key="i" />
          </div>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped>
.row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.row-actions--mobile {
  flex-wrap: nowrap;
}

/* 下拉内的按钮纵向堆叠、满宽 */
:global(.row-actions-dropdown-menu) {
  max-width: calc(100vw - 24px);
}

:global(.row-actions-dropdown) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px;
  min-width: 140px;
}

:global(.row-actions-dropdown .el-button) {
  width: 100%;
  margin-left: 0 !important;
  justify-content: flex-start;
  min-height: 36px;
}
</style>
