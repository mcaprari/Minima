if (!window.console)
	window.console = { log: function() {} };
	
$(function() {
	console.log('[Index] ready');
			
	var mode = (window.WebSocket) ? 'websocket' : 'comet';
	
	var readonly = ($('#minima-read-only').val() == "true");
	if (readonly) 
		$('#readonly-notice').show();
	
	var ws_location = document.location.toString()
		.replace('http://', 'ws://')
		.replace('/index','/websocket');
	
	var comet_location = document.location.toString()
		.replace('/index', '/comet');
	
	var client = new MinimaClient({
		mode: mode,
		web_socket_location: ws_location,
		comet_location: comet_location
	});
	
	var view = new ViewBoard({ 
		content_matcher: '#content',
		readonly: readonly
	});
	
	view.resize($(window).width());
	var controller = new MinimaController({
		client: client,
		view: view
	});
	
	$(window).resize(function() {
    	view.resize($(this).width());
	});
	
	controller.start();
});