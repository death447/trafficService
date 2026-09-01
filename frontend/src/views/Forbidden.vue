<template>
  <div class="page forbidden">
    <div class="panel box">
      <p class="code">403</p>
      <h2>没有访问权限</h2>
      <p class="msg">当前账号无权打开此功能，请联系管理员分配相应权限后重试。</p>
      <router-link class="back" to="/">返回工作台</router-link>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.forbidden {
  display: flex;
  justify-content: center;
  padding-top: 4rem;
}

.box {
  width: 100%;
  max-width: 480px;
  padding: 2.25rem 2rem;
  text-align: center;
}

.code {
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--accent);
  letter-spacing: 0.04em;
  margin-bottom: 0.5rem;
}

h2 {
  font-size: 1.15rem;
  margin-bottom: 0.6rem;
}

.msg {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 1.25rem;
}

.back {
  display: inline-flex;
  padding: 0.45rem 0.9rem;
  border-radius: var(--radius);
  background: var(--accent);
  color: #fff;
  font-size: 0.875rem;
}

.back:hover {
  background: var(--accent-hover);
  color: #fff;
}
</style>
