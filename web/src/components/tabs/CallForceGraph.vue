<template>
  <div>
    <div class="forceGraphHolder" id="forceGraphHolder"></div>
    <OverlayInfo class="overlay-container" :graphLoaded="this.graphLoaded" :clickType="this.clickType"
      :clickedNodeKey="this.clickedNodeKey" :clickedNodeMC="this.clickedNodeMC" :clickedNodeMO="this.clickedNodeMO"
      :clickedNodeOffset="this.clickedNodeOffset" :clickedLinkKey="this.clickedLinkKey"
      :clickedLinkSource="this.clickedLinkSource" :clickedLinkTarget="this.clickedLinkTarget" />
    <Popup v-if="buttonTrigger" :TogglePopup="() => this.togglePopup()" :onNodeClick="setClickedNode"
      :onLinkClick="handleLinkClick" :crossLinkObject="crossLinkObject" :node="popupNode" />
    <SearchBox class="search-container" @changeSelectedNode="changeSelectedNode($event)"
      :functionNameList="this.functionNameList" :graphLoaded="this.graphLoaded" />
  </div>
</template>

<script>
import ForceGraph3D from '3d-force-graph'
import gdot from "./gdot.json"
import * as THREE from "three"
import OverlayInfo from './OverlayInfo.vue'
import Popup from './Popup.vue'
import SearchBox from './SearchBox.vue'
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
    OverlayInfo,
    Popup,
    SearchBox,
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
      graphLoaded: false,
      functionNameList: [],
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
        this.graphLoaded = false
        this.clickType = 'error'
      }
    },
    initializeGraph() {
      // this.graphLoaded = true
      const NODE_R = 6

      this.getJson().then(() => {
        this.highlightNodes = new Set()
        this.highlightLinks = new Set()
        this.hoverNode = null
        this.selectedNodes = new Set()
        let height = document.getElementById("forceGraph").scrollHeight
        let width = document.getElementById("forceGraph").scrollWidth
        this.gData = this.crossLinkObject(this.jsonData)
        // this.gData = this.crossLinkObject(gdot)

        this.graph = ForceGraph3D()
          (document.getElementById("forceGraphHolder"))
          .backgroundColor("#010011")
          .graphData(this.gData)
          .width(width)
          .height(height)
          .nodeId("key")
          .nodeLabel(node => node.attributes.label)
          .nodeRelSize(NODE_R)
          .nodeAutoColorBy(node => node.attributes.modularity_class)
          .nodeThreeObject(node => this.selectedNodes.has(node) && node.attributes.MemoryObject !== "null"
            ? new THREE.Mesh(             // Memory object selected
              new THREE.BoxGeometry(15, 15, 15),
              new THREE.MeshLambertMaterial({
                color: "#FFFF00",
                transparent: true,
                // opacity: 0.75,
                emissive: "#555555",
              })
            )
            : this.selectedNodes.has(node)
              ? new THREE.Mesh(           // Node selected
                new THREE.SphereGeometry(6, 8, 8),
                new THREE.MeshLambertMaterial({
                  color: "#FFFF00",
                  transparent: true,
                  // opacity: 0.75,
                  emissive: "#555555",
                })
              )
              : node === this.hoverNode && node.attributes.MemoryObject !== "null"
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
          .onNodeRightClick((node, event) => { this.handleNodeRightClick(node, event) })
          .onNodeHover(node => { this.handleNodeHover(node) })
          .onLinkHover(link => { this.handleLinkHover(link) })
          .onBackgroundClick(() => { this.clickType = null })
          .onBackgroundRightClick(() => {
            this.clickType = null
            this.selectedNodes = new Set()
            this.rerenderGraph()
          })
        this.graphLoaded = true
      })
    },
    crossLinkObject(json) {
      this.functionNameList = []
      json.nodes.forEach((node) => {
        this.functionNameList.push(node.key)
      })
      // console.log(this.functionNameList)
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
      // console.log(node)
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
    handleNodeRightClick(node, event) {
      if (event.ctrlKey) {
        this.selectedNodes.has(node) ? this.selectedNodes.delete(node) : this.selectedNodes.add(node);
      } else {
        // const untoggle = this.selectedNodes.has(node) && this.selectedNodes.size === 1;
        this.selectedNodes.clear();
        this.selectedNodes.add(node);
        // !untoggle && this.selectedNodes.add(node);
      }

      this.clickType = 'node'
      this.setClickedNode(node)
      this.rerenderGraph()
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
    },
    changeSelectedNode(input) {
      let node = this.gData.nodes.filter((node) => node.key == input)[0]
      if (node) {
        this.selectedNodes.add(node)
        this.clickType = 'node'
        this.setClickedNode(node)
        this.rerenderGraph()
        const distance = 300;
        const distRatio = 1 + distance / Math.hypot(node.x, node.y, node.z);

        const newPos = node.x || node.y || node.z
          ? { x: node.x * distRatio, y: node.y * distRatio, z: node.z * distRatio }
          : { x: 0, y: 0, z: distance };

        this.graph.cameraPosition(
          newPos,
          node,
          2000
        );
      } else {
        this.clickType = 'notFound'
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
  z-index: 99;
}

.search-container {
  position: absolute;
  bottom: 0;
  left: 0;
  margin: 10px;
  z-index: 99;
}
</style>
