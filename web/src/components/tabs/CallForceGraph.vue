<template>
    <div class="forceGraphHolder" id="forceGraphHolder"></div>
</template>

<script>
import ForceGraph3D from '3d-force-graph';

export default {
  name: 'CallForceGrpah',
  data() {
  },
  mounted() {
    this.createGrpah();
  },
  methods: {
    createGrpah() {
      const N = 300;
      const gData = {
        nodes: [...Array(N).keys()].map(i => ({ id: i })),
        links: [...Array(N).keys()]
          .filter(id => id)
          .map(id => ({
            source: id,
            target: Math.round(Math.random() * (id-1))
          }))
      };

      const Graph = ForceGraph3D()
        (document.getElementById("forceGraphHolder"))
          .graphData(gData);
    }
  }
}
</script>

<style>
</style>
