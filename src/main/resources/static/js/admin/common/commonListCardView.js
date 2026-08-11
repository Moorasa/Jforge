/* ===============================================================================================

Name : JWorks_JSCommonListCardView.js

Description :
	JWORKS 프론트엔드 모듈 Card View에서 공통으로 사용하는 유틸리티 파일입니다.

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.JWorks_JSCommonListCardView = window.JWorks_JSCommonListCardView || {};
(function(cardView) {
	"use strict";

	if (cardView.__defined) {
		return;
	}
	cardView.__defined = true;


	let $container = null;

	let apiInfo = null;

	const paginationInfo = {
		currPage: 1,
		countPerPage: Constants.DEFAULT_CARD_VIEW_COUNT_PER_PAGE,
		totalCount: 0
	}
	
	const sorts = [
	];

	/**
	 * 테이블의 공통 이벤트 핸들러를 초기화
	 * @param {object} options - 설정 객체
	 * @param {jQuery} options.$container - 테이블을 감싸는 컨테이너의 제이쿼리 객체
	 * @param {function} [options.searchCallback] - 검색 시 실행될 콜백 함수
	 * @param {function} [options.sortCallback] - 정렬 시 실행될 콜백 함수. {item, order} 객체를 인자로 받음.
	 * @param {function} [options.categoryChangeCallback] - 카테고리 변경 시 실행될 콜백 함수
	*/
	cardView.init = function(options) {
		if (!options || !options.$container || !options.$container.length) {
			console.error("cardView.init: 필수 옵션인 $container가 유효하지 않습니다.");
			return;
		}

		$container = options.$container;
		
		apiInfo = options.apiInfo

		// 1. 검색 기능 초기화
		if (typeof options.searchCallback === 'function') {
			const $searchInput = $container.find("section.search .input");
			const $searchIcon = $container.find("section.search .search-icon");

			// 엔터 키로 검색
			$searchInput.on("keydown", function(event) {
				if (event.keyCode === 13) {
					event.preventDefault();
					options.searchCallback();
				}
			});

			// 아이콘 클릭으로 검색
			$searchIcon.on("click", options.searchCallback);

			// contenteditable 플레이스홀더 문제 해결
			$searchInput.on("input", function() {
				if ($(this).text().trim() === '') {
					$(this).empty();
				}
			});
		}

		// 2. 정렬 기능 초기화
		if (typeof options.sortCallback === 'function') {
			$container.on("click", ".sort-asc, .sort-desc", function() {
				const $this = $(this);
				// [수정] data-item은 클릭된 아이콘의 부모인 div에 있다고 가정합니다.
				const item = $this.closest("div[data-item]").data("item");
				const order = $this.hasClass("sort-asc") ? "asc" : "desc";
				if (item) {
					options.sortCallback({ item, order });
				}
			});
		}
		
		// 3. 카테고리 기능 초기화
		if (typeof options.categoryChangeCallback === 'function') {
			$container.on("change", "section.category select", options.categoryChangeCallback);
		}

		// 4. 체크박스 기능 초기화
		$container.on("change", "#select-all, .row-checkbox", function() {
			if ($(this).is("#select-all")) {
				$container.find(".row-checkbox").prop("checked", $(this).prop("checked"));
			}
			const checkedCount = $container.find(".row-checkbox:checked").length;
			const totalCount = $container.find(".row-checkbox").length;
			$container.find("#select-all").prop("checked", totalCount > 0 && totalCount === checkedCount);

			window.parent.dispatchEvent(new CustomEvent('selectionChange', {
				detail: { hasChecked: checkedCount > 0 }
			}));
		});
		
		if("undefined" === typeof apiInfo) {
		}
		else {
			registEvent();

			// 배열을 비움
			sorts.length = 0;

			// 기본값으로 초기화
			paginationInfo.currPage = 1;
			paginationInfo.countPerPage = Constants.DEFAULT_CARD_VIEW_COUNT_PER_PAGE;
			JWorks_JSPagination.init(paginationEventCallback);
		}
	
	}

	function registEvent() {
	}

	function paginationEventCallback(goPage) {
		paginationInfo.currPage = goPage;
	
		cardView.getList();	
	}

	cardView.setCountPerPage = function(count) {
		// 문자열인 경우 숫자로 변경
		paginationInfo.countPerPage = +count;
	}

	cardView.getList = function() {
	
		const $filter = $container.find(".filter");
		let filters = [];
		$filter.children().each(function(index, item) {
			const $item = $(item);

			if ($item.prop("tagName") === "SELECT") {
				const $selectedOption = $item.find("option:selected");
				const filterValue = $item.val();

				const actualFilterName = $selectedOption.data("filter-name") || $item.attr("name");

				if (filterValue) {
					filters.push({
						name: actualFilterName,
						value: filterValue,
					});
				}
			} else if ($item.hasClass("filter-datepicker")) {
				const $startInput = $item.find(".datepicker-start");
				const $endInput = $item.find(".datepicker-end");

				if ($startInput.val()) {
					filters.push({
						name: $startInput.attr("name"),
						value: $startInput.val(),
					});
				}
				if ($endInput.val()) {
					filters.push({
						name: $endInput.attr("name"),
						value: $endInput.val(),
					});
				}
			}
		});
		const searchColumns = [
			"name","id"
		];
		{
			// 배열을 비움
			sorts.length = 0;
			// 배열을 채움
			sorts.push(apiInfo.defaultSort);
		}
		const data = {
			...{
				category: $filter.val(),
				searchKeyword: $container.find("section.search .input").text(),
				searchColumns: searchColumns,
				filters: filters,
				countPerPage: paginationInfo.countPerPage.toString(),
				currPage: paginationInfo.currPage.toString(),
				sorts: sorts
			}, ...apiInfo.param
		}

		// API 호출
		$.ajax({
				url: apiInfo.url,
				type: "POST",
				cache: false,
				contentType: "application/json",
				data: JSON.stringify(data),
				dataType: "JSON",
				success: function(result,status,xhr) {
					getListPostAction(result);
				},
				error: function(xhr, status, error) {
					console.log(xhr);
					console.log(status);
				},
				timeout: Constants.DEFAULT_AJAX_TIMEOUT
		});
	
	}

	cardView.setCurrPage = function(page) {
		paginationInfo.currPage = page;
	}

	cardView.getCurrPage = function() {
		return paginationInfo.currPage;
	}

	function getListPostAction(res) {
		if(res.code == 200) {
			console.log(res);
		}
		else {
			alert(res.msg);
			return;
		}

		// 삭제 후 저장된 페이지가 비어 있으면 한 페이지 앞으로 보정한다.
		if (res.data && +res.data.totalCount > 0 && (!res.data.list || res.data.list.length === 0) && paginationInfo.currPage > 1) {
			paginationInfo.currPage = paginationInfo.currPage - 1;
			cardView.getList();
			return;
		}

		render(res.data);

		apiInfo.renderCallback(res);
	}

	function render(data) {
		const countPerPage = paginationInfo.countPerPage == 0 ? Constants.MIN_CARD_VIEW_COUNT_PER_PAGE : paginationInfo.countPerPage; 

		// totalCount 보정 로직 (검색어가 없다면 dummy 계산을 위해 +1)
//		const hasRoot = data.list.some(item => item.id === 'ROOT');
		const hasRoot = !$("#searchKeyword").val();
		
		if (hasRoot) {
			paginationInfo.totalCount = +data.totalCount + 1;
		} else {
			paginationInfo.totalCount = +data.totalCount;
		}
		
		// total
		$container.find("section.total .count").text((+data.totalCount).toLocaleString());
			
		const $layoutBody = $container.find(".layout-body");
		$layoutBody.empty();
		// 데이터가 없는 경우 
		if(paginationInfo.totalCount === 0) {
			$layoutBody.append(`
				<div class="empty-case">
					<img src="/images/admin/icon-no-data.png" alt="empty icon" />
					<p class="main-text">${apiInfo.emptyMainText}</p>
					<p class="sub-text">${apiInfo.emptySubText}</p>
				</div>
			`);
		}
		// 데이터가 있는 경우 
		else {
			// tbody
			// 더미 카드를 해당 페이지에서 부족한 만큼 추가
			const totalPage = Math.ceil(paginationInfo.totalCount / countPerPage);
			const isLastPage = totalPage === paginationInfo.currPage ? true : false;
			if(isLastPage) {
				const remainder = paginationInfo.totalCount % countPerPage;
				if(remainder > 0) {
					appendStr = `<div class="card dummy"></div>`;
					appendStr = appendStr.repeat(countPerPage - remainder);
					$layoutBody.append(appendStr);
				}
				else {
					// do nothing
				}
			}
/***
			// 더미 카드를 해당 줄의 부족한 만큼만 추가
			const gapWidth = parseFloat($("#card-view .layout-body").css("gap"));
			const remSize = parseFloat($("html").css("font-size"));
			//const remSize = parseFloat(getComputedStyle(document.documentElement).fontSize);
			const cardWidth = 15.2 * remSize;
			const cardBodyWidth = parseFloat($("#card-view .layout-body").outerWidth());
			const cardPerRow = Math.floor((cardBodyWidth + gapWidth) / (cardWidth + gapWidth));
			const remainder = paginationInfo.totalCount % cardPerRow;
			if(remainder > 0) {
				appendStr = `<div class="card dummy"></div>`;
				appendStr = appendStr.repeat(cardPerRow - remainder);
				$layoutBody.append(appendStr);
			}
***/
		}
		
		// pagination
		JWorks_JSPagination.setPage(paginationInfo);

	}


})(window.JWorks_JSCommonListCardView);
