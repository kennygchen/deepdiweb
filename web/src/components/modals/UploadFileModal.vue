<template>
  <div>
    <b-modal
      ref="fileUploadModal"
      size="lg"
      :ok-disabled="!formValid"
      @ok="ok"
    >
      <div slot="modal-title">
        <i class="fa fa-lg fa-upload" /> Open File
      </div>
      <div
        class="alert alert-info"
        role="alert"
      >
        <p>
          You can upload any kind of binary to disassemble, including ELF, PE, and raw files.
        </p>
      </div>
      <form v-if="!loading">
        <div class="input-group mb-3">
          <b-form-file
            ref="fileinput"
            v-model="file"
            choose-label="Choose File ..."
            @change="fileSelected"
          />
        </div>

        <div class="input-group mb-3">
          <div class="input-group-prepend">
            <span class="input-group-text">Arch</span>
          </div>
          <select
            v-model="arch"
            @change="archChanged"
          >
            <option
              value="detect"
            >
              Detect from file headers
            </option>
            <option value="arm">
              ARMv7
            </option>
            <option value="x86">
              x86_64
            </option>
          </select>
        </div>

        <div class="input-group mb-3">
          <div class="input-group-prepend">
            <span class="input-group-text">Mode</span>
          </div>
          <select
            v-model="mode"
          >
            <option
              v-for="mode in modes"
              :key="mode"
              :value="mode"
            >
              {{ mode }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label for="projectName">Project Name</label>
          <input
            id="projectName"
            v-model="projectName"
            type="text"
            class="form-control"
          >
        </div>

        <!-- <div class="form-group">
          <label for="projectName">Sharing Mode</label>
          <select
            id="sharingMode"
            v-model="defaultSharingMode"
            class="form-control"
          >
            <option value="read">
              Public Read (Everyone with a link can view)
            </option>
            <option value="edit">
              Public Edit (Everyone with a link can edit)
            </option>
            <option
              v-if="!isActiveUser"
              value="none"
              disabled
            >
              Private (Only you can view) -- Log In To Enable This Option
            </option>
            <option
              v-if="isActiveUser"
              value="none"
            >
              Private (Only you can view)
            </option>
          </select>
        </div> -->
      </form>
      <div v-if="loading">
        <h2 class="text-info">
          Loading ...
        </h2>
        <img
          src="../../assets/deepbits.png"
          style="width: 100%;"
        >
        <!--
        <div style="width:106px; height:94px; display:inline-block; margin-left: 300px;">
          <img src="../../assets/deepbits.png" style="position:absolute; z-image:0;"/>
          <img src="../../assets/deepbits.gif" style="position:absolute; z-image:1;"/>
        -->
      </div>
    </b-modal>
  </div>
</template>

<script>
import { bus, SHOW_FILE_UPLOAD_MODAL, SHOW_CONFIGURE_FILE_MODAL,TEST } from '../../bus'
import { uploadFile } from '../../api/oda'
import { LOAD_BINARY,SET_CODE_DIFF_FILE } from '../../store/mutation-types'

function parseFilename (fullPath) {
  if (fullPath.indexOf('/') > -1) {
    return fullPath.substring(fullPath.lastIndexOf('/') + 1, fullPath.length)
  } else {
    return fullPath.substring(fullPath.lastIndexOf('\\') + 1, fullPath.length)
  }
}

export default {
  name: 'UploadFileModal',
  data () {
    return {
      file: null,
      projectName: null,
      defaultSharingMode: 'read',
      loading: false,
      arch: 'detect',
      modes: [],
      codeDiff_file_index: null
    }
  },
  computed: {
    formValid () {
      return this.file && this.projectName
    },
    isActiveUser () {
      return this.$store.getters.isActiveUser
    },
  },

  
  created () {
    const self = this
    bus.$on(SHOW_FILE_UPLOAD_MODAL, function (num) {
      self.codeDiff_file_index = num
      self.file = null
      self.loading = false
      self.projectName = null
      if (self.$refs.fileinput) {
        self.$refs.fileinput.reset()
      }
      self.$refs.fileUploadModal.show()
    })
  },
  methods: {
    fileSelected (e) {
      const filename = parseFilename(e.target.value)
      this.projectName = filename
    },
    async ok (bvEvt) {
      bvEvt.preventDefault()
      this.loading = true
      const { short_name: shortName } = await uploadFile({
        filedata: this.file,
        projectName: this.projectName,
        arch: this.arch,
        mode: this.mode,
        jobs: this.$store.state.uploadFileSwitch,
        fileOrder: this.codeDiff_file_index
      })

      this.$refs.fileUploadModal.hide()
      //bus.$emit(SHOW_CONFIGURE_FILE_MODAL, { fileInfo })
      // this.$store.commit(LOAD_BINARY, { binaryBytes: new Uint8Array(await this.file.arrayBuffer()) })
      // this.$router.push({ path: `/odaweb/${shortName}` })

      console.log('this.$store.state.uploadFileSwitch ' + this.$store.state.uploadFileSwitch)
      if(this.$store.state.uploadFileSwitch === 1) {
        //bus.$emit(SHOW_CONFIGURE_FILE_MODAL, { fileInfo })
        console.log(`UploadFileModal: set binaryBytes and redirect to /odaweb/${shortName}`)
        this.$store.commit(LOAD_BINARY, { binaryBytes: new Uint8Array(await this.file.arrayBuffer()) })
        this.$router.push({ path: `/odaweb/${shortName}` })
      }else{
        this.$store.commit(SET_CODE_DIFF_FILE,{ index: this.codeDiff_file_index, fileName: this.projectName })
      }
    },
    archChanged (e) {
      switch (e.target.value) {
        case 'detect':
          this.modes = []
          break
        case 'armv7':
          this.modes = ['LE', 'BE']
          this.mode = 'LE'
          break
        case 'x86':
          this.modes = ['x86', 'x64']
          this.mode = 'x86'
          break
        default:
          this.modes = []
      }
    }
  }
}
</script>
