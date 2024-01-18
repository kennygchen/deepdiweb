<template>
    <div class="popup" id="popup">
        <div class="popup-inner">
            <button class="popup-close" @click="TogglePopup()">✖</button>
            <div class="subForceGraphHolder" id="subForceGraphHolder"></div>
        </div>
    </div>
</template>

<script>
import ForceGraph3D from '3d-force-graph'
import gdot from "./gdot.json"
import * as THREE from "three"

export default {
    props: ['TogglePopup', 'onNodeClick', 'onLinkClick', 'node'],
    data() {
        return {
        }
    },
    mounted() {
        this.initializeGraph()
    },
    methods: {
        initializeGraph() {
            const NODE_R = 6;
            this.highlightNodes = new Set()
            this.highlightLinks = new Set()
            this.hoverNode = null
            let height = document.getElementById("popup").scrollHeight * 2.1
            let width = document.getElementById("popup").scrollWidth * 2.5
            let gData = this.crossLinkObject(this.generateSubgraph(this.node))
            // let gData = this.crossLinkObject(this.randomData(10))

            this.graph = ForceGraph3D()
                (document.getElementById("subForceGraphHolder"))
                .backgroundColor("#010011")
                .graphData(gData)
                .width(width * 0.3)
                .height(height * 0.3)
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
                .onNodeClick(node => { this.onNodeClick(node) })
                .onLinkClick(link => { this.onLinkClick(link) })
                .onNodeHover(node => { this.handleNodeHover(node) })
                .onLinkHover(link => { this.handleLinkHover(link) })
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
                node.neighbors.forEach(neighbor => this.highlightNodes.add(neighbor))
                node.links.forEach(link => this.highlightLinks.add(link))
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
        rerenderGraph() {
            this.graph
                .linkWidth(this.graph.linkWidth())
                .linkDirectionalParticles(this.graph.linkDirectionalParticles())
                .nodeThreeObject(this.graph.nodeThreeObject())
        },
        randomData(N) {
            return {
                nodes: [...Array(N).keys()].map(i => ({ key: i, attributes: { label: i, modularity_class: 1, MemoryObject: "null" } })),
                links: [...Array(N).keys()]
                    .filter(id => id)
                    .map(id => ({
                        source: id,
                        key: id,
                        target: Math.round(Math.random() * (id - 1))
                    }))
            }
        },
        generateSubgraph(node) {
            console.log(node)
            const subgraph = {
                nodes: [],
                links: []
            }

            const parentNode = {
                key: node.key,
                color: node.color,
                attributes: {
                    label: node.attributes.label,
                    modularity_class: node.attributes.modularity_class,
                    MemoryObject: node.attributes.MemoryObject,
                    Offset: node.attributes.Offset,
                }
            }
            subgraph.nodes.push(parentNode)

            const neighborsSet = new Set(node.neighbors)
            const linksSet = new Set(node.links)

            for (const neighbor of neighborsSet) {
                const nodeCopy = {
                    key: neighbor.key,
                    color: neighbor.color,
                    attributes: {
                        label: neighbor.attributes.label,
                        modularity_class: neighbor.attributes.modularity_class,
                        MemoryObject: neighbor.attributes.MemoryObject,
                        Offset: neighbor.attributes.Offset,
                    },
                };
                subgraph.nodes.push(nodeCopy);
            }

            for (const link of linksSet) {
                const linkCopy = {
                    key: link.key,
                    source: link.source.key,
                    target: link.target.key,
                    color: link.color,
                    attributes: {
                        weight: link.attributes.weight,
                    },
                };
                subgraph.links.push(linkCopy);
            }
            return subgraph
        }
    }
}
</script>

<style scoped>
.popup {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 98;
    background-color: rgba(0, 0, 0, 0.6);
    display: flex;
    align-items: center;
    justify-content: center;
}

.popup-inner {
    background-color: #FFF;
    padding: 2px;
}

.popup-close {
    position: absolute;
    top: 15px;
    right: 15px;
    border-radius: 50%;
    border-color: white;
    border-style: solid;
    width: 35px;
    height: 35px;
    text-align: center;
    font-size: 20px;
    background: white;
}
</style>