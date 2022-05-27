//jqGrid config
$.jgrid.defaults.width = 1000;
$.jgrid.defaults.responsive = true;
$.jgrid.defaults.styleUI = 'Bootstrap';

window.T = {};

var url = function(name) {
	var reg = new RegExp("(^|&)"+ name +"=([^&]*)(&|$)");
	var r = window.location.search.substr(1).match(reg);
	if(r!=null)return  unescape(r[2]); return null;
};
T.p = url;

$.ajaxSetup({
	dataType: "json",
	contentType: "application/json",
	cache: false
});

function hasPermission(permission) {
    if (window.parent.permissions.indexOf(permission) > -1) {
        return true;
    } else {
        return false;
    }
}

window.alert = function(msg, callback){
	parent.layer.alert(msg, function(index){
		parent.layer.close(index);
		if(typeof(callback) === "function"){
			callback("ok");
		}
	});
}

window.confirm = function(msg, callback){
	parent.layer.confirm(msg, {btn: ['Confirm','Cancel']},
	function(){
		if(typeof(callback) === "function"){
			callback("ok");
		}
	});
}

function getSelectedRow() {
    var grid = $("#jqGrid");
    var rowKey = grid.getGridParam("selrow");
    if(!rowKey){
    	alert("Please select one");
    	return ;
    }
    
    var selectedIDs = grid.getGridParam("selarrrow");
    if(selectedIDs.length > 1){
    	alert("Can only choose one");
    	return ;
    }
    
    return selectedIDs[0];
}

function getSelectedRows() {
    var grid = $("#jqGrid");
    var rowKey = grid.getGridParam("selrow");
    if(!rowKey){
    	alert("Please select one");
    	return ;
    }
    
    return grid.getGridParam("selarrrow");
}