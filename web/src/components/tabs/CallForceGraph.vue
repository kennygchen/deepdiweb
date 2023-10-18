<template>
  <div class="forceGraphHolder" id="forceGraphHolder"></div>
</template>

<script>
import ForceGraph3D from '3d-force-graph';
import gdot from "./gdot.json";
import * as THREE from "three";

export default {
  name: 'CallForceGrpah',
  data() {
    return {
      graph: null
    }
  },
  mounted() {
    this.initializeGraph();
  },
  methods: {
    randomData(N) {
      return {
        nodes: [...Array(N).keys()].map(i => ({ id: i })),
        links: [...Array(N).keys()]
          .filter(id => id)
          .map(id => ({
            source: id,
            target: Math.round(Math.random() * (id - 1))
          }))
      }
    },
    initializeGraph() {
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

      const highlightNodes = new Set();
      const highlightLinks = new Set();
      let hoverNode = null;

      this.graph = ForceGraph3D()
        (document.getElementById("forceGraphHolder"))
        .graphData(gData)
        .width(width)
        .height(height)
        .nodeId("key")
        .nodeLabel(node => node.attributes.label)
        .nodeRelSize(NODE_R)
        .nodeAutoColorBy(node => node.attributes.modularity_class)
        .nodeThreeObject(node => node === hoverNode && node.attributes.MemoryObject !== "null"
          ? new THREE.Mesh(
            new THREE.BoxGeometry(15, 15, 15),
            new THREE.MeshLambertMaterial({
              color: node.color,
              transparent: false,
              // opacity: 0.75,
              emissive: "#ababab",
            })
          )
          : highlightNodes.has(node) && node.attributes.MemoryObject !== "null"
            ? new THREE.Mesh(
              new THREE.BoxGeometry(15, 15, 15),
              new THREE.MeshLambertMaterial({
                color: node.color,
                transparent: false,
                // opacity: 0.75,
                emissive: "#ababab",
              })
            )
            : node === hoverNode
              ? new THREE.Mesh(
                new THREE.SphereGeometry(6, 8, 8),
                new THREE.MeshLambertMaterial({
                  color: node.color,
                  transparent: false,
                  // opacity: 0.75,
                  emissive: "#ababab",
                })
              )
              : node.attributes.MemoryObject !== "null"
                ? new THREE.Mesh(
                  new THREE.BoxGeometry(15, 15, 15),
                  new THREE.MeshLambertMaterial({
                    color: node.color,
                    transparent: true,
                    opacity: 0.75,
                    emissive: "#a1a1a1",
                  })
                )
                : highlightNodes.has(node)
                  ? new THREE.Mesh(
                    new THREE.SphereGeometry(6, 8, 8),
                    new THREE.MeshLambertMaterial({
                      color: node.color,
                      transparent: false,
                      // opacity: 0.75,
                      emissive: "#ababab",
                    })
                  )
                  : false
        )
        .linkSource("source")
        .linkTarget("target")
        .linkColor(link => link.source.color)
        .linkDirectionalParticles(link => highlightLinks.has(link) ? 4 : 0)
        .linkDirectionalParticleWidth(2)
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
        });
    },
    rerenderGraph() {
      console.log("rerender")
      this.graph
        .linkWidth(this.graph.linkWidth())
        .linkDirectionalParticles(this.graph.linkDirectionalParticles());
    },
  }
}
</script>

<style></style>
