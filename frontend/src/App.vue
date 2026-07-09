<script setup lang="ts">
import ShelfManagement from './components/ShelfManagement.vue'
</script>

<template>
  <div class="app-container">
    <ShelfManagement />
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.app-container {
  width: 100%;
  min-height: 100vh;
  background-color: #f5f7fa;
}
</style>
