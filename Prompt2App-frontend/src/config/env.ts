/**
 * 环境变量配置
 */
import {CodeGenTypeEnum} from "@/utils/codeGenTypes.ts";

// API 基础地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

// 静态资源地址（走后端 StaticResourceController @RequestMapping("/static")）
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 应用部署域名（保留向后兼容；实际部署访问统一走 STATIC_BASE_URL）
export const DEPLOY_DOMAIN = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost'

// 获取部署应用的完整 URL（走后端 StaticResourceController，与预览同源）
export const getDeployUrl = (deployKey: string) => {
  return `${STATIC_BASE_URL}/${deployKey}/`
}

// 获取静态资源预览URL
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  // 如果是 Vue 项目，浏览地址需要添加 dist 后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}
