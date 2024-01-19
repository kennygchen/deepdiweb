<template>
  <div>
    <div class="forceGraphHolder" id="forceGraphHolder"></div>
    <OverlayInfo class="overlay-container" :loading="this.loading" :clickType="this.clickType"
      :clickedNodeKey="this.clickedNodeKey" :clickedNodeMC="this.clickedNodeMC" :clickedNodeMO="this.clickedNodeMO"
      :clickedNodeOffset="this.clickedNodeOffset" :clickedLinkKey="this.clickedLinkKey"
      :clickedLinkSource="this.clickedLinkSource" :clickedLinkTarget="this.clickedLinkTarget" />
    <Popup v-if="buttonTrigger" :TogglePopup="() => this.togglePopup()" :onNodeClick="setClickedNode"
      :onLinkClick="handleLinkClick" :crossLinkObject="crossLinkObject" :node="popupNode" />
  </div>
</template>

<script>
import ForceGraph3D from '3d-force-graph'
import gdot from "./gdot.json"
import * as THREE from "three"
import OverlayInfo from './OverlayInfo.vue'
import Popup from './Popup.vue'
import { getJsonFromBinary } from '../../api/oda'
import { ref } from 'vue'

export default {
  name: 'CallForceGrpah',
  setup() {
    const popupNode = ref(node)

    return {
      popupNode
    }
  },
  components: {
    OverlayInfo: () => import('./OverlayInfo.vue'),
    Popup: () => import('./Popup.vue'),
  },
  data() {
    return {
      clickType: null,
      clickedNodeKey: null,
      clickedNodeMC: null,
      clickedNodeMO: null,
      clickedNodeOffset: null,
      clickedLinkKey: null,
      clickedLinkSource: null,
      clickedLinkTarget: null,
      // clickedLinkWeight: null,
      buttonTrigger: false,
      loading: true
    }
  },
  mounted() {
    this.initializeGraph()
  },
  methods: {
    async getJson() {
      try {
        let shortName = this.$store.state.shortName
        this.jsonData = await getJsonFromBinary(shortName)
      } catch (e) {
        console.error("Error get JSON from binary:", e)
        this.loading = false
        this.clickType = 'error'
      }
    },
    initializeGraph() {
      // this.loading = true
      const NODE_R = 6

      this.getJson().then(() => {
        this.highlightNodes = new Set()
        this.highlightLinks = new Set()
        this.hoverNode = null
        let height = document.getElementById("forceGraph").scrollHeight
        let width = document.getElementById("forceGraph").scrollWidth
        let gData = this.crossLinkObject(this.jsonData)
        // let gData = this.crossLinkObject(gdot)

        this.graph = ForceGraph3D()
          (document.getElementById("forceGraphHolder"))
          .backgroundColor("#010011")
          .graphData(gData)
          .width(width)
          .height(height)
          .nodeId("key")
          .nodeLabel(node => node.attributes.label)
          .nodeRelSize(NODE_R)
          .nodeAutoColorBy(node => node.attributes.modularity_class)
          .nodeThreeObject(node => node === this.hoverNode && node.attributes.MemoryObject !== "null"
            ? new THREE.Mesh(         // Memory object on hover
              new THREE.BoxGeometry(15, 15, 15),
              new THREE.MeshLambertMaterial({
                color: node.color,
                transparent: true,
                // opacity: 0.75,
                emissive: "#555555",
              })
            )
            : this.highlightNodes.has(node) && node.attributes.MemoryObject !== "null"
              ? new THREE.Mesh(       // Memory object neighbors
                new THREE.BoxGeometry(15, 15, 15),
                new THREE.MeshLambertMaterial({
                  color: node.color,
                  transparent: true,
                  // opacity: 0.75,
                  emissive: "#555555",
                })
              )
              : node === this.hoverNode
                ? new THREE.Mesh(     // node on hover
                  new THREE.SphereGeometry(6, 8, 8),
                  new THREE.MeshLambertMaterial({
                    color: node.color,
                    transparent: true,
                    // opacity: 0.75,
                    emissive: "#555555",
                  })
                )
                : node.attributes.MemoryObject !== "null"
                  ? new THREE.Mesh(     // Memory object
                    new THREE.BoxGeometry(15, 15, 15),
                    new THREE.MeshLambertMaterial({
                      color: node.color,
                      transparent: true,
                      // opacity: 0.75,
                      // emissive: "#a1a1a1",
                    })
                  )
                  : this.highlightNodes.has(node)
                    ? new THREE.Mesh(     // node neighbors
                      new THREE.SphereGeometry(6, 8, 8),
                      new THREE.MeshLambertMaterial({
                        color: node.color,
                        transparent: true,
                        // opacity: 0.75,
                        emissive: "#555555",
                      })
                    )
                    : false
          )
          .linkSource("source")
          .linkTarget("target")
          .linkOpacity(0.25)
          .linkDirectionalParticles(link => this.highlightLinks.has(link) ? 4 : 0)
          .linkDirectionalParticleWidth(2)
          .linkDirectionalParticleSpeed(0.005)
          .linkWidth(link => this.highlightLinks.has(link) ? 4 : 1.5)
          .onNodeClick(node => { this.handleNodeClick(node) })
          .onLinkClick(link => { this.handleLinkClick(link) })
          .onNodeHover(node => { this.handleNodeHover(node) })
          .onLinkHover(link => { this.handleLinkHover(link) })
          .onBackgroundClick(() => { this.clickType = null })
        this.loading = false
      })
    },
    crossLinkObject(json) {
      json.links.forEach((link) => {
        const source = json.nodes.find((node) => node.key === link.source)
        const target = json.nodes.find((node) => node.key === link.target)

        !source.neighbors && (source.neighbors = [])
        !target.neighbors && (target.neighbors = [])
        source.neighbors.push(target)
        target.neighbors.push(source)
        !source.links && (source.links = [])
        !target.links && (target.links = [])
        source.links.push(link)
        target.links.push(link)
      })
      return json
    },
    handleNodeHover(node) {
      // no state change
      if ((!node && !this.highlightNodes.size) || (node && this.hoverNode === node)) return
      this.highlightNodes.clear()
      this.highlightLinks.clear()
      if (node) {
        this.highlightNodes.add(node)
        if (node.neighbors) {
          node.neighbors.forEach(neighbor => this.highlightNodes.add(neighbor))
        }
        if (node.links) {
          node.links.forEach(link => this.highlightLinks.add(link))
        }
      }

      this.hoverNode = node || null

      this.rerenderGraph()
    },
    handleLinkHover(link) {
      this.highlightNodes.clear()
      this.highlightLinks.clear()

      if (link) {
        this.highlightLinks.add(link)
        this.highlightNodes.add(link.source)
        this.highlightNodes.add(link.target)
      }

      this.rerenderGraph()
    },
    handleNodeClick(node) {
      this.clickType = 'node'
      this.setClickedNode(node)
      if (node.attributes.MemoryObject !== "null") {
        this.popupNode = node
        this.togglePopup()
      }
    },
    setClickedNode(node) {
      this.clickedNodeKey = node.key
      this.clickedNodeMC = node.attributes.modularity_class
      this.clickedNodeMO = node.attributes.MemoryObject
      this.clickedNodeOffset = node.attributes.Offset
    },
    handleLinkClick(link) {
      this.clickType = 'link'
      this.clickedLinkKey = link.key
      this.clickedLinkSource = link.source.key
      this.clickedLinkTarget = link.target.key
      // this.clickedLinkWeight = link.attributes.weight
    },
    rerenderGraph() {
      this.graph
        .linkWidth(this.graph.linkWidth())
        .linkDirectionalParticles(this.graph.linkDirectionalParticles())
        .nodeThreeObject(this.graph.nodeThreeObject())
    },
    togglePopup() {
      this.buttonTrigger = !this.buttonTrigger
      if (this.buttonTrigger) {
        this.graph.pauseAnimation()
      } else {
        this.graph.resumeAnimation()
      }
    }
  }
}
</script>

<style scoped>
.container {
  width: 100%;
  height: 100%;
  position: relative;
}

.overlay-container {
  position: absolute;
  top: 0;
  left: 0;
  height: auto;
  border-radius: 25px;
  margin: 10px;
  padding: 15px;
  background: black;
  border: 1px solid white;
  color: white;
  opacity: 70%;
  z-index: 99;
}
</style>
