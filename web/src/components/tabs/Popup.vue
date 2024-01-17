<template>
    <div class="popup">
        <div class="popup-inner">
            <button class="popup-close" @click="TogglePopup()">✖</button>
            <div class="forceGraphHolder" id="forceGraphHolder"></div>
        </div>
    </div>
</template>

<script>
import ForceGraph3D from '3d-force-graph';
import gdot from "./gdot.json";

export default {
    props: ['TogglePopup'],
    data() {
        return {
            graph: null,
        }
    },
    mounted() {
        this.initializeGraph();
    },
    methods: {
        initializeGraph() {
            const NODE_R = 6;
            let height = document.getElementById("forceGraph").scrollHeight;
            let width = document.getElementById("forceGraph").scrollWidth;
            let gData = gdot;

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
    z-index: 99;
    background-color: rgba(0, 0, 0, 0.2);
    display: flex;
    align-items: center;
    justify-content: center;
}

.popup-inner {
    background-color: #FFF;
    padding: 32px;
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