<template>
    <div>
        <div>Left-click: view node/link info</div>
        {{ clickedNode }}
        <div>Reft-click: focus on node</div>
    </div>
    <!-- <div v-if="">
            <div>Key: {clicked.clicked ? clicked.clicked.key : "null"}</div>
        <div>Source: {clicked.clicked ? clicked.clicked.source.key : "null"}</div>
        <div>Target: {clicked.clicked ? clicked.clicked.target.key : "null"}</div>
        <div>Weight: {clicked.clicked ? clicked.clicked.attributes.weight : "null"}</div>
    </div>
    <div v-if="">
            <div>Key: {clicked.clicked ? clicked.clicked.key : "null"}</div>
        <div>Modularity class: {clicked.clicked ? clicked.clicked.attributes.modularity_class : "null"}</div>
        <div>Memory Object: {clicked.clicked ? clicked.clicked.attributes.MemoryObject : "null"}</div>
        <div>Offset: {clicked.clicked ? clicked.clicked.attributes.Offset : "null"}</div>
    </div> -->
</template>

<script>
export default {
    name: 'OverlayInfo',
    props: ['clickedNode', 'clickedLink'],
    mounted() {
        this.init();
    },
    methods: {
        init() {
            console.log("overlay info")
            console.log(clickedNode)
        }
    }
}
</script>

<style></style>