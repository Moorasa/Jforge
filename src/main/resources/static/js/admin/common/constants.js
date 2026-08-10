"undefined" == typeof RESOURCE_NODE_TYPE && ((RESOURCE_NODE_TYPE = {}),(function(resourceNodeType) {

	resourceNodeType.ROOT = "root";
	resourceNodeType.SYSTEM = "system";
	resourceNodeType.MENU = "menu";
	resourceNodeType.PAGE = "page";
	resourceNodeType.FUNCTION = "func";

})(RESOURCE_NODE_TYPE));


"undefined" == typeof PostMessageType && ((PostMessageType = {}),(function(postMessageType) {
	
	postMessageType.SET_HEIGHT = "CustomEvent:set height";
	postMessageType.RENDER_COMPLETE = "CustomEvent:render complete";
	postMessageType.CLOSE_MY_POPUP = "CustomEvent:close my popup";
	postMessageType.CREATE_SNACKBAR = "CustomEvent:create snackbar";
	postMessageType.SESSION_RENEW = "CustomEvent:session renew";
	postMessageType.SUBMIT_SELECTION = "CustomEvent:submit selection";
	postMessageType.SYNC_VIEW_STATE = "CustomEvent:sync view state";
	postMessageType.DUAL_LAYOUT_VIEW_TYPE_CHANGE = "CustomEvent:dual layout view type change";

})(PostMessageType));


"undefined" == typeof ViewType && ((ViewType = {}),(function(viewType) {

	// 외부 접근 상수값 정의
	viewType.TABLE = "C4500001";
	viewType.CARD = "C4500002";
	viewType.TREE = "C4500003";
	viewType.FORM = "C4500004";

})(ViewType));

"undefined" == typeof TableType && ((TableType = {}),(function(tableType) {

	// 외부 접근 상수값 정의
	tableType.DEFAULT = "";
	tableType.NO_PAGINATION = "no_pagination";
	tableType.MORE = "more";
	
})(TableType));

"undefined" == typeof PaginationType && ((PaginationType = {}),(function(paginationType) {

	// 외부 접근 상수값 정의
	paginationType.PAGE = "page";
	paginationType.MORE = "more";
	
})(PaginationType));


"undefined" == typeof PopupType && ((PopupType = {}),(function(PopupType) {

	// 외부 접근 상수값 정의
	PopupType.button = {
		NONE: "none",
		OK: "ok",
		OK_CANCEL: "ok cancel"
	}
	PopupType.width = {
		SMALL: "none",
		MEDIUM: "ok",
		LARGE: "ok cancel"
	}
	
})(PopupType));


"undefined" == typeof LayoutType && ((LayoutType = {}),(function(LayoutType) {

	// 외부 접근 상수값 정의
	LayoutType.BASIC = "C3100001"; // 납품처 관리자 대시보드 기본 (DDD single) 레이아웃 
	LayoutType.DUAL = "C3100002"; // 납품처 관리자 대시보드 2단 레이아웃

})(LayoutType));


