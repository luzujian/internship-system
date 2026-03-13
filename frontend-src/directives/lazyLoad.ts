// 图片懒加载指令
// 用于延迟加载不在视口内的图片，提高页面加载速度

import type { Directive, DirectiveBinding } from 'vue'

interface LazyLoadElement extends HTMLElement {
  _observer?: IntersectionObserver
  _loadHandler?: () => void
  _errorHandler?: () => void
  dataset: DOMStringMap & { src?: string }
  src: string
  complete: boolean
}

const lazyLoad = {
  // 指令挂载时的处理
  mounted(el: LazyLoadElement, binding: DirectiveBinding<string>) {
    // 保存图片的原始 src
    el.dataset.src = binding.value

    const placeholderSvg = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"%3E%3C/svg%3E'

    if (!el.src) {
      el.src = placeholderSvg
    }

    el.setAttribute('decoding', 'async')

    // 创建图片加载完成处理函数
    const handleLoad = () => {
      // 移除事件监听器
      el.removeEventListener('load', handleLoad)
      el.removeEventListener('error', handleError)

      // 加载成功后可以添加一个加载完成的类
      el.classList.add('lazy-loaded')

      // 确保加载事件不会被延迟
      if (el.complete) {
        const loadEvent = new Event('load', { bubbles: true })
        setTimeout(() => el.dispatchEvent(loadEvent), 0)
      }
    }

    // 创建图片加载错误处理函数
    const handleError = () => {
      // 移除事件监听器
      el.removeEventListener('load', handleLoad)
      el.removeEventListener('error', handleError)

      // 可以设置一个错误占位图
      el.src = placeholderSvg
      el.classList.add('lazy-error')
    }

    // 创建 IntersectionObserver 实例
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting && el.dataset.src) {
          // 图片进入视口时，加载图片
          el.addEventListener('load', handleLoad)
          el.addEventListener('error', handleError)
          el.src = el.dataset.src

          // 使用 setTimeout 确保 IntersectionObserver 回调不会阻塞主线程
          setTimeout(() => {
            // 加载完成后停止观察
            if (observer && el) {
              observer.unobserve(el)
            }
          }, 0)
        }
      })
    }, {
      rootMargin: '200px', // 增大预加载范围
      threshold: 0.01, // 只要有 1% 可见就触发，提高加载灵敏度
      root: null // 使用视口作为根元素
    })

    // 开始观察元素
    observer.observe(el)

    // 保存 observer 实例和事件处理函数，以便在 unmounted 时清理
    el._observer = observer
    el._loadHandler = handleLoad
    el._errorHandler = handleError

    // 检查图片是否已经在视口中（处理初始状态）
    const rect = el.getBoundingClientRect()
    const isVisible = (
      rect.top < window.innerHeight + 200 &&
      rect.bottom >= -200 &&
      rect.left < window.innerWidth + 200 &&
      rect.right >= -200
    )

    if (isVisible && el.dataset.src) {
      el.addEventListener('load', handleLoad)
      el.addEventListener('error', handleError)
      el.src = el.dataset.src
      if (el._observer) {
        el._observer.unobserve(el)
      }
    }
  },

  // 指令更新时的处理
  updated(el: LazyLoadElement, binding: DirectiveBinding<string>) {
    if (binding.value !== binding.oldValue) {
      // 移除之前的事件监听器
      if (el._loadHandler) {
        el.removeEventListener('load', el._loadHandler)
      }
      if (el._errorHandler) {
        el.removeEventListener('error', el._errorHandler)
      }

      // 图片源改变时，更新数据属性
      el.dataset.src = binding.value

      // 重新开始观察
      if (el._observer) {
        el._observer.unobserve(el)
        el._observer.observe(el)
      } else {
        // 如果 observer 不存在，重新创建一个
        const observer = new IntersectionObserver((entries) => {
          entries.forEach(entry => {
            if (entry.isIntersecting && el.dataset.src) {
              // 图片进入视口时，加载图片
              el.src = el.dataset.src
              if (observer) {
                observer.unobserve(el)
              }
            }
          })
        }, {
          rootMargin: '200px',
          threshold: 0.01
        })
        observer.observe(el)
        el._observer = observer
      }
    }
  },

  // 指令卸载时的清理工作
  unmounted(el: LazyLoadElement) {
    // 移除事件监听器
    if (el._loadHandler) {
      el.removeEventListener('load', el._loadHandler)
      delete el._loadHandler
    }
    if (el._errorHandler) {
      el.removeEventListener('error', el._errorHandler)
      delete el._errorHandler
    }

    // 清理 observer，防止内存泄漏
    if (el._observer) {
      el._observer.unobserve(el)
      // 对于某些浏览器，调用 disconnect 更彻底
      el._observer.disconnect()
      delete el._observer
    }

    // 清理数据属性
    delete el.dataset.src
  }
} as Directive

export default lazyLoad
