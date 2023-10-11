import Vue from 'vue'
import Vuex from 'vuex'
import * as types from './mutation-types'
import * as actions from './actions'
import _ from 'lodash'
import { state as apiState } from '../api/oda'

const CHUNK_SIZE = 1000

Vue.use(Vuex)

export default new Vuex.Store({
  // initializing state variables
  state: {
    binaryBytes: undefined,

    user: null,

    architectures: [],
    architectureOptions: [],
    endians: [],

    selectedDu: null,

    projectName: null,
    shortName: null,
    shortName2: null,
    codeDiffFiles: [],
    codeDiffResult: null,
    code_diff_function_list: [],
    code_diff_loading: false,

    branches: null,
    branchesByAddr: null,

    structTypes: null,

    binary: null,

    uploadFileSwitch: 1,
    revision: 0,
    sections: [],
    parcels: [],
    displayUnitsLength: 0,
    liveMode: false,
    symbols: [],
    functions: [],
    comments: [],
    strings: [],
    binaryOptions: {},
    binaryText: '',
    default_permission_level: null,

    /* First Attempt to store sparse array of DisplayUnits */
    displayUnits: [],
    displayUnitChunks: {},

    inAnActiveDuLoad: false,

    activeUsers: [],

    //
    vmaToLda: {}
  },
  // modify states' value
  mutations: {
    [types.LOAD_ODBFILE] (state, { odbFile, allDus, vmaToLda }) {
      state.displayUnits.splice(0)
      for (const k in state.displayUnitChunks) {
        delete state.displayUnitChunks[k]
      }

      for (let i = 0; i < allDus.length / CHUNK_SIZE; i++) {
        state.displayUnitChunks[i] = allDus.slice((i * CHUNK_SIZE), CHUNK_SIZE * (i + 1))
      }

      state.architectures = odbFile.architectures.sort()
      state.endians = odbFile.endians

      state.user = odbFile.user

      state.sections = odbFile.sections
      // calculated later instead of being in the server response
      // state.parcels = odbFile.parcels

      state.projectName = odbFile.project_name
      state.default_permission_level = odbFile.default_permission_level

      state.displayUnitsLength = allDus.length // odbFile.displayUnits.size
      state.displayUnits = allDus
      state.selectedDu = state.displayUnits[0]

      state.binary = odbFile.binary

      state.symbols = odbFile.symbols
      state.strings = odbFile.strings
      state.functions = odbFile.functions
      state.comments = odbFile.comments
      state.liveMode = true // odbFile.live_mode // set to true for testing
      state.binaryOptions = state.binary.options
      state.binaryText = state.binary.text

      state.structTypes = odbFile.structTypes
      state.structFieldTypes = odbFile.structFieldTypes

      state.vmaToLda = vmaToLda
      apiState.vmaToLda = state.vmaToLda

      // _.each(startingDisplayUnits, (du, i) => {
      //   Vue.set(state.displayUnits, i, du)
      // })
    },
    // 保存第一个文件名字
    [types.SET_SHORTNAME] (state, { shortName }) {
      state.shortName = shortName
    },
    // 保存第二个文件名字
    [types.SET_SHORTNAME2] (state, { shortName2 }) {
      state.shortName2 = shortName2
    },
    [types.SET_CODE_DIFF_FILE] (state, { index, fileName }) {
      // vue cannot detect array element change if using index way must use splice method to change an element at the index
      state.codeDiffFiles.splice(index, 1, fileName)
    },
    [types.SET_CODE_DIFF_LOADING] (state, { loadingState }) {
      state.code_diff_loading = loadingState
      console.log(`set to ${loadingState}`)
    },
    [types.UPLOADFILESWITCH] (state, { num }) {
      state.uploadFileSwitch = num
    },
    [types.SET_CODEDIFFRESULT] (state, { result }) {
      state.codeDiffResult = result
    },
    [types.SET_CODE_DIFF_FUNCTION_LIST] (state, { list }) {
      state.code_diff_function_list = list
    },
    [types.LOAD_BINARY] (state, { binaryBytes, raw }) {
      // this can be loaded before odbfile, so we save this to attach to odbfile later
      state.binaryBytes = binaryBytes
    },

    [types.CACHE_DISPLAYUNITS] (state, { start, displayUnits }) {
      _.each(displayUnits, (du, i) => {
        Vue.set(state.displayUnits, start + i, du)
      })
    },

    [types.SET_SELECTED_DU] (state, { du }) {
      state.selectedDu = du
    },

    [types.SET_BINARYTEXT] (state, { binaryText }) {
      state.binaryText = binaryText
    },

    [types.CLEAR_AND_SET_DISPLAYUNITS] (state, { start, displayUnits }) {
      state.displayUnits.splice(0)
      _.each(displayUnits, (du, i) => {
        Vue.set(state.displayUnits, start + i, du)
      })
    },

    [types.SET_BINARYOPTIONS] (state, { architecture, baseAddress, endian, selectedOpts }) {
      state.binaryOptions.architecture = architecture
      state.binaryOptions.base_address = baseAddress
      state.binaryOptions.endina = endian
      state.binaryOptions.selected_opts = selectedOpts
    },

    [types.SET_PARCELS] (state, { parcels }) {
      state.parcels = parcels
      window.parcels = parcels
    },

    [types.UPDATE_USER] (state, { user }) {
      state.user = user
    },

    [types.MAKE_COMMENT] (state, { comment, vma }) {
      const index = _.findIndex(state.comments, { vma: vma })
      if (index !== -1) {
        Vue.set(state.comments, index, {
          comment: comment,
          vma: vma
        })
      } else {
        state.comments.push({
          comment: comment,
          vma: vma
        })
      }
    },

    [types.SET_DEFAULT_PERMISSION_LEVEL] (state, { permissionLevel }) {
      state.default_permission_level = permissionLevel
    },

    [types.SET_ACTIVE_USERS] (state, { activeUsers }) {
      state.activeUsers = activeUsers
    },

    [types.UPDATE_USER_POSITION] (state, { username, address }) {
      console.log('UPDATE_USER_POSITION', username, address)
      const user = _.find(state.activeUsers, { username: username })
      if (user) {
        if (user.lastReportedPosition) {
          user.lastReportedPosition.address = address
        } else {
          Vue.set(user, 'lastReportedPosition', {
            username,
            address
          })
        }
      }
    },

    [types.UPDATE_FUNCTION] (state, { vma, name, retval, args }) {
      const index = _.findIndex(state.functions, { vma: vma })
      if (index === -1) {
        console.log('UPDATE_FUNCTION creating', vma, name)
        state.functions.push({ vma, name, retval, args })
      } else {
        const f = state.functions[index]
        console.log('UPDATE_FUNCTION updating', vma, name)
        f.name = name
        f.retval = retval
        f.args = args
        Vue.set(state.functions, index, f)
      }

      const symbol = _.find(state.symbols, { vma: vma })
      if (symbol) {
        symbol.name = name
      }
    },

    [types.ADD_STRUCTURE] (state, { name }) {
      state.structTypes.push({
        fields: [],
        is_packed: true,
        name: name
      })
    },

    [types.SET_BRANCHES] (state, { branches }) {
      state.branches = branches
      state.branchesByAddr = {}

      _.each(branches, (branch, i) => {
        branch.id = i

        if (state.branchesByAddr[branch.srcAddr]) {
          state.branchesByAddr[branch.srcAddr].push(branch)
        } else {
          state.branchesByAddr[branch.srcAddr] = [branch]
        }

        if (state.branchesByAddr[branch.targetAddr]) {
          state.branchesByAddr[branch.targetAddr].push(branch)
        } else {
          state.branchesByAddr[branch.targetAddr] = [branch]
        }
      })
    },

    [types.DELETE_STRUCT] (state, { index }) {
      state.structTypes.splice(index, 1)
    }
  },
  actions: actions,

  // return states
  getters: {
    getShortName: (state) => () => {
      return state.shortName
    },
    getShortName2: (state) => () => {
      return state.shortName2
    },
    getUploadFileState: (state) => {
      return state.uploadFileSwitch
    },
    binaryBytes: (state) => () => {
      return state.binaryBytes
    },
    binaryInfo: (state) => () => {
      return state.binary
    },
    isActiveUser: (state) => {
      return state.user && !state.user.is_lazy_user
    },
    functionsByAddress: function (state) {
      return _.keyBy(state.functions, 'vma')
    },
    commentsByAddress: function (state) {
      return _.keyBy(state.comments, 'vma')
    },
    selectedAddress: (state, getters) => {
      if (!state.selectedDu) {
        return 0
      }
      return state.selectedDu.vma
    },
    otherUsers: (state, getters) => {
      return _.reject(state.activeUsers.slice(0, 10), function (user) {
        return user.username === state.user.username
      })
    },
    branchesInRange: (state, getters) => (low, high) => {
      if ((high - low) > 1000) {
        // In cases where we're over a big range, might as well goto filtering branch list
        return _.filter(state.branches, (branch) => {
          if (branch.srcAddr > low && branch.srcAddr < high) {
            return true
          }
          if (branch.targetAddr > low && branch.targetAddr < high) {
            return true
          }
          return false
        })
      }

      const matchingBranches = []
      for (let i = low; i <= high; i++) {
        if (state.branchesByAddr[i]) {
          _.each(state.branchesByAddr[i], (branch) => { matchingBranches.push(branch) })
        }
      }
      const matching = _.uniqBy(matchingBranches, 'id')

      return matching
    },
    // dusByRange: (state, getters) => (start, length) => {
    //   const dus = state.displayUnits.slice(start, start + length)
    //   for (let i = 0; i < length; i++) {
    //     if (dus[i] === undefined) {
    //       Vue.set(dus, i, { dummy: true })
    //     }
    //   }

    //   return dus
    // },

    dusByRange: (state, getters) => (start, length) => {
      // console.log(`dusByRange(${start}, ${length})`)
      if (start < 0) { start = 0 }
      // start is the lda of the du we want, length is how many dus we want

      // find what chunk we're in
      const chunk = Math.floor(start / CHUNK_SIZE)
      const currChunk = state.displayUnitChunks[chunk]
      if (!currChunk) { return [] }

      // find the offset of the chunk we're in
      const offset = start % CHUNK_SIZE

      const dus = currChunk.slice(offset, offset + length)

      const nextChunkElems = length - dus.length // elements we need to get from the next chunk
      if (nextChunkElems > 0) {
        const nextChunk = state.displayUnitChunks[chunk + 1]
        if (nextChunk) {
          dus.push(...state.displayUnitChunks[chunk + 1].slice(0, nextChunkElems))
        }
      }

      // we reached the end of our dus, append padding if needed
      const padding = length - dus.length
      for (let i = 0; i < padding; i++) {
        dus.push({ dummy: true })
      }

      return dus
    },

    parcelByVma: (state, getters) => (vma) => {
      for (let i = 0; i < state.parcels.length; i++) {
        const parcel = state.parcels[i]
        // console.log(`${parcel.vma_start} <= ${vma} <= ${parcel.vma_end} | ${parcel.vma_start <= vma && vma <= parcel.vma_end}`)
        if (parcel.vma_start <= vma && vma <= parcel.vma_end) {
          return parcel
        }
      }
      return undefined
    }
  }
})
