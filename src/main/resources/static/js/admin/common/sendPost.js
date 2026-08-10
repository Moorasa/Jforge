function sendPost(url, params, target) {
	let form = document.createElement("form");
	form.setAttribute("method", "post");
	form.setAttribute("action", url);
	if(target === undefined)
		;
	else
		form.setAttribute("target", target);
		
	
	document.charset = "utf-8";
	
	for(var key in params) {
		var hiddenInput = document.createElement("input");
		hiddenInput.setAttribute("type", "hidden");
		hiddenInput.setAttribute("name", key);
		hiddenInput.setAttribute("value", params[key]);
		form.appendChild(hiddenInput);
	}
	document.body.appendChild(form);
	form.submit();
}
