// create table view data object
var data = [
	{title:'Facebook', hasChild:true, test:'../examples/facebook.js'},
	{title:'YQL', hasChild:true, test:'../examples/yql.js'},
	{title:'Twitter', hasChild:true, test:'../examples/twitter.js', title_image:'../images/twitter_logo_header.png'},
	{title:'Foursquare', hasChild:true, test:'../examples/foursquare.js', title_image:'../images/light-poweredby-foursquare.png'},
	{title:'SOAP', hasChild:true, test:'../examples/soap.js'},
	{title:'RSS', hasChild:true, test:'../examples/rss.js', barColor:'#b40000'},

];

// create table view
var tableview = Titanium.UI.createTableView({
	data:data
});

// create table view event listener
tableview.addEventListener('click', function(e)
{
	if (e.rowData.test)
	{
		var win = Titanium.UI.createWindow({
			url:e.rowData.test,
			title:e.rowData.title
		});
		if (e.rowData.barColor)
		{
			win.barColor = e.rowData.barColor;
		}
		if (e.rowData.title_image)
		{
			win.titleImage = e.rowData.title_image;
		}
		Titanium.UI.currentTab.open(win,{animated:true})
	}
});

// add table view to the window
Titanium.UI.currentWindow.add(tableview);
