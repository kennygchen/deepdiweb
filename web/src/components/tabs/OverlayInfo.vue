<template>
    <div v-if="clickType == null">
        <div>Left-click: view node/link info</div>
    </div>
    <div v-else-if="clickType == 'node'">
        <div>Key: {{ clickedNodeKey }}</div>
        <div>Modularity class: {{ clickedNodeMC }}</div>
        <div>Memory Object: {{ clickedNodeMO }}</div>
        <div>Offset: {{ clickedNodeOffset }}</div>
    </div>
    <div v-else-if="clickType == 'link'">
        <div>Key: {{ clickedLinkKey }}</div>
        <div>Source: {{ clickedLinkSource }}</div>
        <div>Target: {{ clickedLinkTarget }}</div>
        <div>Weight: {{ clickedLinkWeight }}</div>
    </div>
</template>

<script>
export default {
    name: 'OverlayInfo',
    props: ['clickType', 'clickedNodeKey', 'clickedNodeMC', 'clickedNodeMO', 'clickedNodeOffset', 'clickedLinkKey', 'clickedLinkSource', 'clickedLinkTarget', 'clickedLinkWeight'],
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