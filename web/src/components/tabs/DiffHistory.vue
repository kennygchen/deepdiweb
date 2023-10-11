<template>
<!--  <div>-->
<!--    <ul>-->
<!--      &lt;!&ndash; 遍历 history 数据并展示 &ndash;&gt;-->
<!--      <li-->
<!--        v-for="item in history"-->
<!--        :key="item.timestamp"-->
<!--        @click="showCodeDiff(item)"-->
<!--      >-->
<!--        {{ item.file1.projectName }} vs {{ item.file2.projectName }} - {{ item.timestamp | formatTimestamp }}-->
<!--      </li>-->
<!--    </ul>-->
<!--  </div>-->
  <div class="diff-history-container">
    <ul>
      <!-- 遍历 history 数据并展示 -->
      <li
        v-for="item in history"
        :key="item.timestamp"
        :class="{ 'selected': selectedItem === item }"
      >
        {{ item.file1.projectName }} vs {{ item.file2.projectName }} - {{ item.timestamp | formatTimestamp }}
        <button @click.prevent="showCodeDiff(item)">Choose</button>
      </li>
    </ul>
  </div>
</template>

<script>
// import {CODE_DIFF_UPLOAD_DIR} from "../../../../proxy/src/config";

import { getJsonContent } from '../../api/oda'

export default {
  name: 'DiffHistory',
  data() {
    return {
      history: [],
      selectedItem: null
    };
  },
  filters: {
    formatTimestamp(value) {
      if (!value) return '';
      const date = new Date(value);
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`;
    }
  },
  async created() {
    try {
      this.jsonData = await getJsonContent();
      this.fetchDiffHistory();
    } catch (e) {
      console.error("Error fetching JSON content:", e);
    }
  },
  methods: {
    async fetchDiffHistory() {
      try {
        this.history = this.jsonData
      } catch (error) {
        console.error("There was a problem fetching the history:", error)
      }
    },
    showCodeDiff(item) {
      console.log(`Showing diff for: ${item.file1.projectName} vs ${item.file2.projectName}`)

      // Emit an event to notify the parent component
      this.$emit('show-diff', item);
    },
  },
};
</script>

<style>
.diff-history-container {
  display: flex;
  justify-content: center;
}

ul {
  list-style-type: none;
}

li.selected {
  background-color: #e0e0e0; /* 你可以选择其他颜色来高亮所选的行 */
}
</style>
