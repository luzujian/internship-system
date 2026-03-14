import axios from 'axios'

const service = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 600000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

service.interceptors.request.use(
  config => {
    return config
  },
  error => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code && res.code !== 200) {
      console.error('响应错误:', res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    console.error('响应错误:', error)
    if (error.response) {
      console.error('错误状态码:', error.response.status)
      console.error('错误响应数据:', error.response.data)
      switch (error.response.status) {
        case 400:
          console.error('请求参数错误:', error.response.data)
          break
        case 403:
          console.error('拒绝访问')
          break
        case 404:
          console.error('请求的资源不存在')
          break
        case 500:
          console.error('服务器错误')
          break
        default:
          console.error(`请求失败: ${error.response.status}`)
      }
    } else if (error.request) {
      console.error('网络错误，请检查网络连接')
    } else {
      console.error('请求配置错误')
    }
    return Promise.reject(error)
  }
)

const request = (config) => {
  return service(config)
}

export default request