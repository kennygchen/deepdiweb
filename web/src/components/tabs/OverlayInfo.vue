<template>
    <div v-if="clickType == null">
        <div><b>Left-click: </b>view node/link info</div>
    </div>
    <div v-else-if="clickType == 'node'">
        <div><b>Key: </b>{{ clickedNodeKey }}</div>
        <div><b>Modularity class: </b>{{ clickedNodeMC }}</div>
        <div><b>Memory Object: </b>{{ clickedNodeMO }}</div>
        <div><b>Offset: </b>{{ clickedNodeOffset }}</div>
    </div>
    <div v-else-if="clickType == 'link'">
        <div><b>Key: </b>{{ clickedLinkKey }}</div>
        <div><b>Source: </b>{{ clickedLinkSource }}</div>
        <div><b>Target: </b>{{ clickedLinkTarget }}</div>
        <div><b>Weight: </b>{{ clickedLinkWeight }}</div>
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

<style scoped></style>