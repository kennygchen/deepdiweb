<template>
  <div style="position: absolute; top: 0; bottom: 0; left: 0; right: 0;">
    <canvas
      id="branch-lines"
      style="position: absolute; top: 0; bottom: 0; left: 0; right: 0;"
    />
  </div>
</template>

<script>
import { BranchLinePanel } from '../lib/branchLines'
import _ from 'lodash'

export default {
  name: 'BranchLines',
  props: ['renderedDus'],
  data () {
    return {
      branchLines: null
    }
  },
  computed: {
    selectedAddress: function () {
      return this.$store.getters.selectedAddress
    },
    branches: function () {
      return this.$store.state.branches
    },
    branchesInRange: function () {
      // TODO: Optimize this search across all branches.
      const self = this

      const validDusInRange = _.reject(this.renderedDus, 'dummy')
      if (validDusInRange.length < 2) {
        return []
      }

      const low = validDusInRange[0].vma
      const high = validDusInRange[validDusInRange.length - 1].vma

      /* Get branches which could be in the overlapping range */
      const b = _(this.$store.getters.branchesInRange(low, high)).map('id').map(i => {
        return this.$store.state.branches[i]
      }).value()

      const mappedBranches = _.map(b, (link) => {
        return {
          srcAddr: link.srcAddr,
          targetAddr: link.targetAddr,
          from: self.getLineOffset(parseInt(link.srcAddr)),
          to: self.getLineOffset(parseInt(link.targetAddr))
        }
      })

      return mappedBranches
    }
  },
  watch: {
    branchesInRange () {
      this.branchLines.setBranches(this.branchesInRange)
      this.branchLines.draw()
    }
  },

  mounted: function () {
    this.branchLines = new BranchLinePanel('#branch-lines', [])
  },
  methods: {
    getLineOffset (addr) {
      if (addr < this.renderedDus[0].vma) {
        return -1
      }
      if (addr > this.renderedDus[this.renderedDus.length - 1].vma) {
        return 1000000
      }
      for (let i = this.renderedDus.length - 1; i >= 0; i--) {
        if (this.renderedDus[i].vma === addr) {
          return i
        }
      }
      return 0
    }
  }
}
</script>
