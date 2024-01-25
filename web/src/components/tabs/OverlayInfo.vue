<template>
    <div v-if="loading">
        Generating call graph. Depending on the size of the binary, this could take 30 seconds to a minutes.
    </div>
    <div v-else-if="clickType == null">
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
    <div v-else-if="clickType == 'error'">
        <div><b>API Error</b></div>
    </div>
</template>

<script>
export default {
    name: 'OverlayInfo',
    props: ['loading', 'clickType', 'clickedNodeKey', 'clickedNodeMC', 'clickedNodeMO', 'clickedNodeOffset', 'clickedLinkKey', 'clickedLinkSource', 'clickedLinkTarget', 'clickedLinkWeight'],
}
</script>

<style scoped></style>