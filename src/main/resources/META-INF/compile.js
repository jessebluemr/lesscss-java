// remove console listener
less.logger._listeners = [];

function compile(input, compress) {
    var out = "";
    var error;
    var messages = [];
    var logger = {
        debug: function(msg) {
            messages.push("DEBUG:" + msg);
        },
        info: function(msg) {
            messages.push("INFO:" + msg);
        },
        warn: function(msg) {
            messages.push("WARN:" + msg);
        },
        error: function(msg) {
            messages.push("ERROR:" + msg);
        }
    };
    less.logger.addListener(logger);

    less.render(input, {
        compress: compress,
        onReady: false
    }, function(err, output) {
        if (err) {
            error = err;
            return;
        }
        out = output;
    });
    less.logger.removeListener(logger);
    if (error) {
        var msg = "error:";
        msg += error["message"];
        msg += "(line:";
        msg += error["line"];
        msg += ",column:";
        msg += error["column"];
        msg += ",index:";
        msg += error["index"];
        msg += ")";
        return msg;
    }
    return out.css + "|MESSAGES|" + messages.join("\n");
}