<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
import { theme as antTheme } from 'ant-design-vue'
import { computed } from 'vue'

// Ant Design Vue 主题统一：消灭默认 #1890ff，对齐深空蓝设计 token
const antThemeConfig = computed(() => ({
  algorithm: antTheme.darkAlgorithm,
  token: {
    colorPrimary: '#4a6fa5',
    colorBgBase: '#0f1923',
    colorTextBase: '#e8eef5',
    borderRadius: 8,
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif",
  },
  components: {
    Modal: {
      contentBg: '#1a2332',
      headerBg: '#1a2332',
    },
    Card: {
      colorBgContainer: 'rgba(36, 48, 64, 0.8)',
    },
    Input: {
      colorBgContainer: 'rgba(15, 25, 35, 0.6)',
    },
    Menu: {
      itemBg: 'transparent',
      horizontalItemSelectedColor: '#e8eef5',
    },
  },
}))
</script>

<template>
  <a-config-provider :theme="antThemeConfig">
    <BasicLayout />
  </a-config-provider>
</template>

<style>
/* ====================================================================
   Prompt2App Design Tokens — 崩铁设计语言 × 专业 AI 产品
   单一设计来源（Single Source of Truth），所有组件引用这些变量
   ==================================================================== */
:root {
  /* ---- 主色：深空蓝 + 星云紫 ---- */
  --color-primary: #4a6fa5;
  --color-primary-light: #6b8cb8;
  --color-primary-dark: #2d4a73;
  --color-secondary: #7b6cb0;
  --color-accent-gold: #c9a96e;
  --color-accent-cyan: #7ec4cf;

  /* ---- 中性色：避免纯黑纯白 ---- */
  --color-bg: #0f1923;
  --color-bg-surface: #1a2332;
  --color-bg-elevated: #243040;
  --color-bg-glass: rgba(36, 48, 64, 0.75);
  --color-bg-glass-light: rgba(255, 255, 255, 0.04);

  --color-text-primary: #e8eef5;
  --color-text-secondary: #a0aec0;
  --color-text-muted: #6c7a8c;

  --color-border: rgba(255, 255, 255, 0.08);
  --color-border-hover: rgba(255, 255, 255, 0.15);
  --color-border-gold: rgba(201, 169, 110, 0.4);

  /* ---- 圆角 ---- */
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 20px;

  /* ---- 阴影 ---- */
  --shadow-card: 0 4px 24px rgba(0, 0, 0, 0.3);
  --shadow-hover: 0 12px 40px rgba(0, 0, 0, 0.4);
  --shadow-glow: 0 0 20px rgba(74, 111, 165, 0.15);
  --shadow-glow-gold: 0 0 16px rgba(201, 169, 110, 0.15);

  /* ---- 间距 ---- */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 40px;
  --spacing-2xl: 64px;

  /* ---- 过渡 ---- */
  --transition-fast: 200ms ease-out;
  --transition-normal: 250ms ease-out;

  /* ---- 渐变 ---- */
  --gradient-primary: linear-gradient(135deg, #4a6fa5 0%, #7b6cb0 100%);
  --gradient-text: linear-gradient(135deg, #e8eef5 0%, #a0aec0 100%);
}

/* ====================================================================
   全局基础
   ==================================================================== */
* {
  box-sizing: border-box;
}

html {
  overflow-x: hidden;
}

body {
  margin: 0;
  padding: 0;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell',
    'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  overflow-x: hidden;
  background: var(--color-bg);
  color: var(--color-text-primary);
}

/* 全局滚动条 — 深空风格 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.2);
}
::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}
::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* 全局链接 */
a {
  color: var(--color-accent-cyan);
  text-decoration: none;
  transition: color var(--transition-fast);
}
a:hover {
  color: var(--color-primary-light);
}

/* 全局淡入动画工具类 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.fade-in-up {
  animation: fadeInUp 600ms ease-out forwards;
}
</style>
