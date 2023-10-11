<template>
    <div>
      <div
        id="sidebar-label-symbols"
        class="oda-sidebar-title"
      >
        {{this.pname}}.c
      </div>
      <div class="input-group">
        <span class="input-group-text">Filter</span>
        <input
          v-model="filterValue"
          type="text"
          class="form-control"
          placeholder="Substring"
        >
      </div>
      <div
        class="sidebar-scroll-container"
        style="position:absolute; inset: 6rem 0 0; overflow: scroll;"
      >
        <table
          id="symbol-table"
          class="table table-striped sidebar-table table-condensed"
        >
          <thead>
            <tr>
              <th
                class="col-sm-4"
                style="width:100%; text-align: left;"
              >
                Name
              </th>
            </tr>
          </thead>
          <tbody class="scrollContent">
            <tr v-for="f in filteredFunctions">
              <td style="width:100%; text-align: left;">
                <span
                  class="clickable"
                  @click="handleFunctionClick"
                >{{ f }}</span>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="filteredFunctions.length === 0">
          <em>No Function availables</em>
        </div>
      </div>
    </div>
  </template>

  <script>
  import _ from 'lodash'
  import { bus, NAVIGATE_TO_ADDRESS } from '../../bus'
  import { vmaToLda, getCodeDiffResult} from '../../api/oda'
  import { SET_CODE_DIFF_LOADING } from '../../store/mutation-types'
  import $ from "jquery"
  import { mapState } from 'vuex'

  export default {
    name: 'FunctionList',
    data () {
      return {
        filterValue: null,
        pname: null,
      }
    },
    props: {
      diffItem: {
        type: Object,
        required: true
      }
    },
    created() {
      this.pname = this.$store.state.projectName
    },
    computed: {
      filteredFunctions: function () {
        let functions = this.$store.state.code_diff_function_list
        if (this.filterValue) {
          let fv = this.filterValue
          return functions.filter(function (str) {
            return str === fv
          })
        }
        return functions
      },
      ...mapState(['code_diff_function_list'])
    },
    methods: {
      async handleFunctionClick(e) {
        $('.diff-content').replaceWith(`<div class="diff-content"></div>`)
        this.$store.commit(SET_CODE_DIFF_LOADING,{ loadingState: true })
        let selected_function = e.target.innerHTML
        console.log('this.diffItem', this.diffItem)
        let response = await getCodeDiffResult(selected_function, this.diffItem)
        this.pname = selected_function
        this.$store.commit(SET_CODE_DIFF_LOADING,{ loadingState: false })
        if(response && this.$store.state.code_diff_loading == false) {
          $('.diff-content').replaceWith(response.result)
        }
      }
    }
  }
  </script>

  <style scoped>
    .clickable {
      color: #0088CC;
      text-decoration: none;
      cursor: pointer;
    }

    .clickable:hover {
      text-decoration: underline;
    }

    #symbol-table {
      font-size: 0.85rem;
    }

    .table td {
      padding: 3px 8px;
    }
  </style>
