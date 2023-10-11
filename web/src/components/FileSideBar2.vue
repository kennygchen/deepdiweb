<template>
    <div>
      <div
        style="position:absolute; left:0px; width: 41px; top: 0px; bottom: 0px; background-color: whiteSmoke; border-right: 1px solid #222;"
      >
        <ul class="icon-list">
          <li>
            <a
              :class="{ 'active-link': selectedPanel == 'LiveEntry' }"
              @click="setSelectedPanel('LiveEntry')"
            >
              <i class="fa fa-pencil-square-o" />
            </a>
          </li>
          <li>
            <a
              :class="{ 'active-link': selectedPanel == 'FunctionList' }"
              @click="setSelectedPanel('FunctionList')"
            >
              <i class="fa fa-list" />
            </a>
          </li>
          <li>
            <a
              :class="{ 'active-link': selectedPanel == 'findBytes' }"
              @click="setSelectedPanel('findBytes')"
            >
              <i class="fa fa-search" />
            </a>
          </li>

        </ul>
      </div>

      <div style="position: absolute; left: 41px; right: 0px; top: 0px; bottom: 0px; padding: 10px; overflow:scroll;">
        <div v-if="selectedPanel == 'LiveEntry'">
          <LiveEntry />
        </div>
        <div v-if="selectedPanel == 'FunctionList'">
          <FunctionList :diffItem="currentDiffItem" />
        </div>
        <div v-if="selectedPanel == 'findBytes'">
          <FindBytes />
        </div>
      </div>
    </div>
  </template>

  <script>
  import { bus, CLOSE_SIDEBAR, SHOW_FINDBYTES_PANEL, SHOW_RAWVIEWFORM_PANEL, SHOW_RAWMODE_PANEL } from '../bus'
  import LiveEntry from './sidebar/LiveEntry'
  import FunctionList from './panels/FunctionList'
  import FindBytes from './panels/FindBytes'
  // import Structures from './panels/Structures'
  // import Operations from './panels/Operations'

  export default {
    name: 'FileSidebar_liveMode',
    components: {
      LiveEntry, FunctionList, FindBytes //, Structures, Operations
    },
    data () {
      return {
        selectedPanel: 'LiveEntry'
      }
    },
    props: {
      currentDiffItem: Object
    },
    created () {
      bus.$on(SHOW_FINDBYTES_PANEL, () => {
        this.selectedPanel = 'findBytes'
      })
      bus.$on(SHOW_RAWVIEWFORM_PANEL, () => {
        this.selectedPanel = 'FunctionList'
      })
      bus.$on(SHOW_RAWMODE_PANEL, () => {
        this.selectedPanel = 'LiveEntry'
      })

      // icons
    },
    methods: {
      setSelectedPanel (panel) {
        if (panel === this.selectedPanel) {
          bus.$emit(CLOSE_SIDEBAR)
        } else {
          this.selectedPanel = panel
        }
      }
    }
  }
  </script>

  <style scoped>
    .icon-list {
      list-style: none;
      margin: 0;
      padding: 0;
    }

    .icon-list li {
      text-align: center;
      font-size: 16px;
      position: relative;
      margin: 0px;
      padding: 0px;
    }

    .icon-list li a {
      display: inline-block;
      width: 40px;
      height: 40px;
      line-height: 40px;
      vertical-align: center;
      color: #999;
      text-decoration: none;
      font-size: 20px;
      padding: 0;
    }

    .active-link {
      text-shadow: rgba(0, 0, 0, 0.2) 0px -1px 0px !important;
      background-color: rgb(0, 92, 230) !important;
      color: white !important;
    }

    ::v-deep .oda-sidebar-title {
      font-size: 16px;
      font-weight: bold;
      border-bottom: 2px solid #ddd;
      margin-bottom: 10px;
    }
  </style>
