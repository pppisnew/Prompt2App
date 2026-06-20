<template>
  <div id="userLoginPage">
    <h2 class="title">Prompt2App · 用户登录</h2>
    <div class="desc">不写一行代码，生成完整应用</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码长度不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <div class="tips">
        没有账号
        <RouterLink to="/user/register">去注册</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">登录</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>
<script lang="ts" setup>
import { reactive } from 'vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const res = await userLogin(values)
  // 登录成功，把登录态保存到全局状态中
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userLoginPage {
  min-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background:
    radial-gradient(circle at 30% 70%, rgba(74, 111, 165, 0.12) 0%, transparent 50%),
    radial-gradient(circle at 70% 30%, rgba(123, 108, 176, 0.10) 0%, transparent 50%),
    var(--color-bg);
  padding: var(--spacing-xl);
}

#userLoginPage > .title,
#userLoginPage > .desc,
#userLoginPage > a-form,
#userLoginPage > form {
  width: 100%;
  max-width: 420px;
}

/* 毛玻璃卡片包裹表单 */
:deep(.ant-form) {
  background: var(--color-bg-glass);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-card);
  width: 100%;
  max-width: 420px;
}

.title {
  text-align: center;
  margin-bottom: var(--spacing-md);
  color: var(--color-text-primary);
  font-size: 24px;
  font-weight: 600;
  max-width: 420px;
  width: 100%;
}

.desc {
  text-align: center;
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-xl);
  max-width: 420px;
  width: 100%;
}

.tips {
  text-align: right;
  color: var(--color-text-muted);
  font-size: 13px;
  margin-bottom: var(--spacing-md);
}

:deep(.ant-input),
:deep(.ant-input-password) {
  background: var(--color-bg-glass-light) !important;
  border-color: var(--color-border) !important;
  border-radius: var(--radius-sm) !important;
}

:deep(.ant-input:focus),
:deep(.ant-input-focused) {
  border-color: var(--color-primary) !important;
  box-shadow: var(--shadow-glow) !important;
}
</style>
