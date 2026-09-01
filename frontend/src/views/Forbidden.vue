<template>
  <div class="forbidden">
    <h1>403</h1>
    <p>没有访问该页面的权限</p>
    <router-link to="/">返回首页</router-link>
  </div>
</template>

<script setup>
</script>

<style scoped>
.forbidden {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
}

h1 {
  font-size: 3rem;
}

a {
  color: #1677ff;
}
</style>
