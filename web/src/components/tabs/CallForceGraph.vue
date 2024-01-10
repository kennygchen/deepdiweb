<template>
  <div>
    <div class="forceGraphHolder" id="forceGraphHolder"></div>
    <OverlayInfo class="overlay-container" :clickType="this.clickType" :clickedNodeKey="this.clickedNodeKey"
      :clickedNodeMC="this.clickedNodeMC" :clickedNodeMO="this.clickedNodeMO" :clickedNodeOffset="this.clickedNodeOffset"
      :clickedLinkKey="this.clickedLinkKey" :clickedLinkSource="this.clickedLinkSource"
      :clickedLinkTarget="this.clickedLinkTarget" :clickedLinkWeight="this.clickedLinkWeight" />
  </div>
</template>

<script>
import ForceGraph3D from '3d-force-graph';
import gdot from "./gdot.json";
import * as THREE from "three";
import OverlayInfo from './OverlayInfo.vue';
import { getJsonFromBinary } from '../../api/oda'

export default {
  name: 'CallForceGrpah',
  components: {
    OverlayInfo: () => import('./OverlayInfo.vue'),
  },
  data() {
    return {
      graph: null,
      clickType: null,
      clickedNodeKey: null,
      clickedNodeMC: null,
      clickedNodeMO: null,
      clickedNodeOffset: null,
      clickedLinkKey: null,
      clickedLinkSource: null,
      clickedLinkTarget: null,
      clickedLinkWeight: null,
    }
  },
  mounted() {
    this.initializeGraph();
  },
  methods: {
    async getJson() {
      try {
        this.jsonData = await getJsonFromBinary("bzip2")
        this.graph.graphData(this.jsonData)
        // console.log(this.jsonData)
        // this.graph.graphData(await getJsonFromBinary("bzip2"))
      } catch (e) {
        console.error("Error get Json from binary:", e);
      }
    },
    randomData(N) {
      return {
        nodes: [...Array(N).keys()].map(i => ({ key: i, attributes: { label: i, modularity_class: 1, MemoryObject: null } })),
        links: [...Array(N).keys()]
          .filter(id => id)
          .map(id => ({
            source: id,
            key: id,
            target: Math.round(Math.random() * (id - 1))
          }))
      }
    },
    initializeGraph() {
      // this.getJson().then(() => {});
      const NODE_R = 6;
      let height = document.getElementById("forceGraph").scrollHeight;
      let width = document.getElementById("forceGraph").scrollWidth;
      const gData = gdot;

      // cross-link node objects
      gData.links.forEach((link) => {
        const source = gData.nodes.find((node) => node.key === link.source);
        const target = gData.nodes.find((node) => node.key === link.target);

        !source.neighbors && (source.neighbors = []);
        !target.neighbors && (target.neighbors = []);
        source.neighbors.push(target);
        target.neighbors.push(source);
        !source.links && (source.links = []);
        !target.links && (target.links = []);
        source.links.push(link);
        target.links.push(link);
      });
      // console.log(gData)

      const highlightNodes = new Set();
      const highlightLinks = new Set();
      let hoverNode = null;

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
        .nodeThreeObject(node => node === hoverNode && node.attributes.MemoryObject !== "null"
          ? new THREE.Mesh(         // Memory object on hover
            new THREE.BoxGeometry(15, 15, 15),
            new THREE.MeshLambertMaterial({
              color: node.color,
              transparent: true,
              // opacity: 0.75,
              emissive: "#555555",
            })
          )
          : highlightNodes.has(node) && node.attributes.MemoryObject !== "null"
            ? new THREE.Mesh(       // Memory object neighbors
              new THREE.BoxGeometry(15, 15, 15),
              new THREE.MeshLambertMaterial({
                color: node.color,
                transparent: true,
                // opacity: 0.75,
                emissive: "#555555",
              })
            )
            : node === hoverNode
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
                : highlightNodes.has(node)
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
        .linkDirectionalParticles(link => highlightLinks.has(link) ? 4 : 0)
        .linkDirectionalParticleWidth(2)
        .linkDirectionalParticleSpeed(0.005)
        .linkWidth(link => highlightLinks.has(link) ? 4 : 1.5)
        .onNodeHover(node => {
          // no state change
          if ((!node && !highlightNodes.size) || (node && hoverNode === node)) return;
          highlightNodes.clear();
          highlightLinks.clear();
          if (node) {
            highlightNodes.add(node);
            node.neighbors.forEach(neighbor => highlightNodes.add(neighbor));
            node.links.forEach(link => highlightLinks.add(link));
          }

          hoverNode = node || null;

          this.rerenderGraph();
        })
        .onLinkHover(link => {
          highlightNodes.clear();
          highlightLinks.clear();

          if (link) {
            highlightLinks.add(link);
            highlightNodes.add(link.source);
            highlightNodes.add(link.target);
          }

          this.rerenderGraph();
        })
        .onNodeClick(node => {
          this.clickType = 'node';
          this.clickedNodeKey = node.key;
          this.clickedNodeMC = node.attributes.modularity_class;
          this.clickedNodeMO = node.attributes.MemoryObject;
          this.clickedNodeOffset = node.attributes.Offset;
          // console.log(this.clickedNodeKey);
          // console.log(this.clickedNodeMC);
          // console.log(this.clickedNodeMO);
          // console.log(this.clickedNodeOffset);
        })
        .onLinkClick(link => {
          this.clickType = 'link';
          this.clickedLinkKey = link.key
          this.clickedLinkSource = link.source.key
          this.clickedLinkTarget = link.target.key
          this.clickedLinkWeight = link.attributes.weight
          // console.log(this.clickedLinkKey);
          // console.log(this.clickedLinkSource);
          // console.log(this.clickedLinkTarget);
          // console.log(this.clickedLinkWeight);
        })
        .onBackgroundClick(() => { this.clickType = null });
    },
    rerenderGraph() {
      this.graph
        .linkWidth(this.graph.linkWidth())
        .linkDirectionalParticles(this.graph.linkDirectionalParticles())
        .nodeThreeObject(this.graph.nodeThreeObject());
    },
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
  width: 550px;
  height: auto;
  border-radius: 25px;
  margin: 10px;
  padding: 15px;
  background: black;
  border: 1px solid white;
  color: white;
  opacity: 85%;
  z-index: 5;
}
</style>
