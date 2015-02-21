less.functions.functionRegistry.addMultiple({
    add: function(a, b) {
        return new (less.tree.Dimension)(a.value + b.value);
    },
    increment: function(a) {
        return new (less.tree.Dimension)(a.value + 1);
    }
});