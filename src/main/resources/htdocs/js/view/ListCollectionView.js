ListCollectionView = Backbone.View.extend({
	tagName: 'div',
	className: 'lists-container',
	initialize: function(args) {
		// bind the functions 'add' and 'remove' to the view.
	    _(this).bindAll('addList');
	    this.lists = args.lists;
	    this.notes = args.notes;
	    this.listWidth = 250;
		this._listViews = [];
		this.lists.each(this.addList);				
						
		this.lists.bind('add', this.addList);
	},
	
	resize: function(availableWidth) {
		var newWidth = availableWidth - 145;
		var maxListWidth = 330;
		var minListWidth = 210;
		var cutoffBoardSize = minListWidth * 3;
		var verticalBoradSize = minListWidth;
		
		var calcSize = 0;
		
		if (newWidth < cutoffBoardSize) {
			newWidth = maxListWidth;
			calcSize = maxListWidth;
		}		
		else {
			var calcSize =  (newWidth)/3;
			if (calcSize > maxListWidth) calcSize = maxListWidth;
			if (calcSize < minListWidth) calcSize = minListWidth;
		}
		
		if (this._rendered)
			$(this.el).width(newWidth);
		
		this.listWidth = calcSize;
		
		_(this._listViews).each(function(l) { l.resize(calcSize); });
	},
	
	render: function() {
		this._rendered = true;
		var that = this;
		$(this.el).empty();
		
		_(this._listViews).each(function(listView) {
			$(that.el).append(listView.render().el);
		});
		return this;
	},
	
	addList: function(list) {
		var listView = new ListView({ 
			model: list,
			notes: this.notes,
			width: this.listWidth
		});		
		this._listViews.push(listView);
		
		if (this._rendered)
			$(this.el).append(listView.render().el);
	}
	
});