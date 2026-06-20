<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="copyright">
        Prompt2App · AI 网页生成平台
      </p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  background: var(--color-bg-surface);
  backdrop-filter: blur(10px);
  text-align: center;
  padding: var(--spacing-lg);
  margin-top: var(--spacing-xl);
  border-top: 1px solid var(--color-border);
}

.copyright {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 14px;
  letter-spacing: 0.5px;
}
</style>
