<template>
    <div v-if="graphLoaded" class="autocomplete">
        <ul v-show="isOpen" class="autocomplete-results">
            <li v-for="(result, i) in results" :key="i" @click="setResult(result)" class="autocomplete-result"
                :class="{ 'is-active': i === arrowCounter }">
                {{ result }}
            </li>
        </ul>
        <div class="container">
            <input class="input" type="text" v-model="input" @input="onChange" @keydown.down="onArrowDown"
                @keydown.up="onArrowUp" @keydown.enter="onEnter" placeholder="Search functions...">
            <span class="icon" type="submit"><i class="fa fa-search"></i></span>
        </div>
    </div>
</template>

<script>
export default {
    name: 'SearchBox',
    props: {
        functionNameList: {
            type: Array,
            required: false,
            default: () => [],
        },
        graphLoaded: {
            type: Boolean,
            default: true,
        }
    },
    data() {
        return {
            input: '',
            results: [],
            isOpen: false,
            arrowCounter: -1,
        }
    },
    mounted() {
        document.addEventListener('click', this.handleClickOutside);
    },
    destroyed() {
        document.removeEventListener('click', this.handleClickOutside);
    },
    methods: {
        filterResults() {
            this.results = this.functionNameList.filter(functionName => functionName.toLowerCase().indexOf(this.input.toLowerCase()) > -1);
            if (this.results.length > 0) {
                this.isOpen = true;
            } else {
                this.isOpen = false;
            }
        },
        onChange() {
            this.filterResults();
        },
        setResult(result) {
            this.input = result;
            this.isOpen = false;
            this.$emit('changeSelectedNode', this.input)
        },
        handleClickOutside(event) {
            if (!this.$el.contains(event.target)) {
                this.arrowCounter = -1;
                this.isOpen = false;
            }
        },
        onArrowDown() {
            if (this.arrowCounter < this.results.length) {
                this.arrowCounter = this.arrowCounter + 1;
            }
        },
        onArrowUp() {
            if (this.arrowCounter > 0) {
                this.arrowCounter = this.arrowCounter - 1;
            }
        },
        onEnter() {
            this.input = this.results[this.arrowCounter];
            this.arrowCounter = -1;
            this.isOpen = false;
            this.$emit('changeSelectedNode', this.input)
        }
    },
}
</script>

<style scoped>
.container {
    width: 300px;
    vertical-align: middle;
    position: relative;
    padding: 0;
    color: white;
}

.container input {
    width: 45px;
    height: 45px;
    opacity: 0.5;
    border-radius: 25px;
    float: left;
    padding-left: 45px;
    transition: width .50s ease, opacity .50s, padding-right .50s;
    background-color: black;
    border: 1px solid white;
    color: white;
}

.icon {
    position: absolute;
    top: 10px;
    left: 17px;
    z-index: 1;
    opacity: 0.5;
    transition: opacity .50s;
}

.container input:focus,
.container:hover input {
    padding-right: 25px;
    opacity: 1;
    width: 300px;
}

.container:hover .icon,
.container input:focus+.icon {
    opacity: 1;
}

.autocomplete {
    width: 300px;
}

.autocomplete-results {
    padding: 0;
    margin-bottom: 5px;
    border: 1px solid #eeeeee;
    height: 120px;
    overflow: auto;
    color: white;
    background-color: black;
    scrollbar-color: gray black;
    scrollbar-width: thin;
    white-space: nowrap;
}

.autocomplete-result {
    list-style: none;
    text-align: left;
    padding: 4px 10px;
    cursor: pointer;
}

.autocomplete-result.is-active,
.autocomplete-result:hover {
    background-color: #4AAE9B;
}
</style>