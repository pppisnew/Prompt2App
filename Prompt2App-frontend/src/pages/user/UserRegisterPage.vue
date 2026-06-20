<template>
  <div id="userRegisterPage">
    <h2 class="title">Prompt2App · 用户注册</h2>
    <div class="desc">不写一行代码，生成完整应用</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请确认密码' },
          { min: 8, message: '密码不能小于 8 位' },
          { validator: validateCheckPassword },
        ]"
      >
        <a-input-password v-model:value="formState.checkPassword" placeholder="请确认密码" />
      </a-form-item>
      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">注册</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 验证确认密码
 * @param rule
 * @param value
 * @param callback
 */
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  // 注册成功，跳转到登录页面
  if (res.data.code === 0) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
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

#userRegisterPage > .title,
#userRegisterPage > .desc,
#userRegisterPage > a-form,
#userRegisterPage > form {
  width: 100%;
  max-width: 420px;
}

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
  margin-bottom: var(--spacing-md);
  color: var(--color-text-muted);
  font-size: 13px;
  text-align: right;
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
