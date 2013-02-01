window = this;
location = {
    protocol : "none",
    hostname :"localhost"
};
document = {
    getElementsByTagName : function(){
        return []
    }
};
doItSave = function(input, compress){
    try{
        return doIt(input, compress);
    }catch(e){
        var msg = "error:";
        msg += e["message"];
        msg += "(line:";
        msg += e["line"];
        msg += ",column:";
        msg += e["column"];
        msg += ",index:";
        msg += e["index"];
        msg += ")";
        return msg;
    }
}