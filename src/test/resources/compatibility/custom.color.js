less.functions.functionRegistry.add("_color", function(str) {
    if (str.value === "evil red") {
        return new (less.tree.Color)("600");
    }
});
