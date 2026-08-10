/* ===============================================================================================

Name : MagicIAM_JSCommonListTableView.js

Description :
	JWORKS 프론트엔드 모듈 Table View에서 공통으로 사용하는 유틸리티 파일입니다.

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.MagicIAM_JSCommonListTableView = window.MagicIAM_JSCommonListTableView || {};
(function(tableView) {
	"use strict";

	if (tableView.__defined) {
		return;
	}
	tableView.__defined = true;


	let $container = null;

	let apiInfo = null;

	let callbacks = null; // 이벤트 콜백 함수들

	let selectionType = 'checkbox';
	
	let tableType = null;

	let isPopupMode = false;

	// 문서에 적용
	let sheet = new CSSStyleSheet();

	const paginationInfo = {
		currPage: 1,
		countPerPage: Constants.DEFAULT_TABLE_VIEW_COUNT_PER_PAGE,
		totalCount: 0
	}

	const sorts = [
	];

	/**
	 * 테이블의 공통 이벤트 핸들러를 초기화
	 * @param {object} options - 설정 객체
	*/
	tableView.init = function(options) {

		if (!options || !options.$container || !options.$container.length) {
			console.error("tableView.init: 필수 옵션인 $container가 유효하지 않습니다.");
			return;
		}

		$container = options.$container;
		apiInfo = options.apiInfo;
		callbacks = options.callbacks || {};
		tableType = options.tableType || {};
		selectionType = options.selectionType || 'checkbox';

		document.adoptedStyleSheets = [sheet];

		if (selectionType === 'checkbox') {
			// 체크박스 기능 초기화
			$container.on("change", "#select-all, .row-checkbox", function() {
				if ($(this).is("#select-all")) {
					$container.find(".row-checkbox:not(:disabled)").prop("checked", $(this).prop("checked"));
				}
				const checkedCount = $container.find(".row-checkbox:not(:disabled):checked").length;
				const totalCount = $container.find(".row-checkbox:not(:disabled)").length;
				$container.find("#select-all").prop("checked", totalCount > 0 && totalCount === checkedCount);

				window.parent.dispatchEvent(new CustomEvent('selectionChange', {
					detail: { hasChecked: checkedCount > 0 }
				}));
			});
		}

		if ("undefined" === typeof apiInfo) {
		}
		else {
			registEvent();

			// 배열을 비움
			sorts.length = 0;

			// 기본값으로 초기화
			paginationInfo.currPage = 1;
			paginationInfo.countPerPage = Constants.DEFAULT_TABLE_VIEW_COUNT_PER_PAGE;
			waitAndPaginationInit(0, 30);
		}

		// 부모(Popup)로부터 메시지 수신 대기
		window.addEventListener("message", function(event) {
			if (window.location.origin === event.origin) {
				// pass
			}
			else {
				console.log("error : not same origin");
				return;
			}

			if (event.data.type === 'SET_EMPTY_MODE' && event.data.mode === 'popup') {
				isPopupMode = true;

				if ($container) {
					const $emptyCase = $container.find(".empty-case");
					if ($emptyCase.length > 0) {
						$emptyCase.addClass("popup");
					}
				}
			}
		});
		
	}

	function waitAndPaginationInit(retryCount, maxRetryCount) {

		retryCount = retryCount || 0;
		maxRetryCount = maxRetryCount || 30;

		// 객체가 생성되지 않은 경우
		if(typeof window.MagicIAM_JSPagination === "undefined") {
			if (retryCount >= maxRetryCount) {
				console.error("Pagination 초기화 실패:");
				if (typeof callbacks === "function") {
					callbacks(false);
				}
				return;
			}

			setTimeout(function() {
				waitAndPaginationInit(retryCount + 1, maxRetryCount);
			}, 100);
		}
		// 객체가 생성된 경우
		else {
			MagicIAM_JSPagination.init(paginationEventCallback);
		}

	}

	function registEvent() {
		// 오름차순 아이콘 클릭
		$container
			.off("click", "thead .sort-asc")
			.on("click", "thead .sort-asc", function() {
				// 배열을 비움
				sorts.length = 0;
				// 배열을 채움
				sorts.push({
					item: $(this).closest('td').attr("data-name"),
					order: "asc"
				});
	
				paginationInfo.currPage = 1;
	
				tableView.getList();
			});
		// 내림차순 아이콘 클릭
		$container
			.off("click", "thead .sort-desc")
			.on("click", "thead .sort-desc", function() {
				// 배열을 비움
				sorts.length = 0;
				// 배열을 채움
				sorts.push({
					item: $(this).closest('td').attr("data-name"),
					order: "desc"
				});
	
				paginationInfo.currPage = 1;
	
				tableView.getList();
			});
	}

	function paginationEventCallback(goPage) {
		paginationInfo.currPage = goPage;

		tableView.getList();
	}

	tableView.setCountPerPage = function(count) {
		// 문자열인 경우 숫자로 변경
		paginationInfo.countPerPage = +count;
	}

	// datepicker 유효성 검사 및 조회
	tableView.getDatepickerFilterList = function() {
		const $datepickerWrapper = $container.find('.filter-datepicker');

		if ($datepickerWrapper.length > 0) {
			const startDateVal = $datepickerWrapper.find('.datepicker-start').val();
			const endDateVal = $datepickerWrapper.find('.datepicker-end').val();

			if (!startDateVal || !endDateVal) {
				return;
			}

			// 유효성 검사
			let startDate = new Date(startDateVal);
			let endDate = new Date(endDateVal);
			let today = new Date();

			if (startDate > endDate) {
				$(".datepicker-start").val("");
				$(".datepicker-end").val("");
				JWORKS_JSSnackBar.create("시작일자는 종료일자보다 클 수 없습니다.");
				return;
			}
			if (endDate > today) {
				$(".datepicker-start").val("");
				$(".datepicker-end").val("");
				JWORKS_JSSnackBar.create("종료일자는 오늘보다 클 수 없습니다.");
				return;
			}
		}
		paginationInfo.currPage = 1;
		tableView.getList();
	}

	// API 요청에 필요한 필터, 검색어, 정렬 등의 파라미터를 반환
	// 엑셀 다운로드 기능 개발을 위해 getList()에서 분리
	tableView.getCurrentSearchData = function() {
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

		const searchColumns = ["name", "id"];

		if (sorts.length > 0) {
			// pass
		}
		else {
			// 배열을 비움
			sorts.length = 0;
			// 배열을 채움
			sorts.push(apiInfo.defaultSort);
		}

		const data = {
			...{
				category: $filter.val(),
				searchKeyword: $container.find("section.search .input").text().replace(/^\s+|\s+$/g, ''),
				searchColumns: searchColumns,
				filters: filters,
				countPerPage: paginationInfo.countPerPage == 0 ? Constants.MAX_COUNT_PER_PAGE.toString() : paginationInfo.countPerPage.toString(),
				currPage: paginationInfo.currPage.toString(),
				sorts: sorts
			}, ...apiInfo.param
		};

		return data;
	}

	// 엑셀 다운로드
	tableView.excelDownloadCallback = function($button, extraParams) {
		// TODO : 아이콘 클릭 시 img src 변경
//		const defaultIcon = "/images/admin/icon-excel-download-btn.png";
//		const loadingIcon = "/images/admin/icon-excel-download-btn-on.png";
//		const $img = $button.find('img');
		
		const data = tableView.getCurrentSearchData();
		const downloadUrl = apiInfo.excelDownloadUrl;
		
		if (!downloadUrl) {
			console.error("엑셀 다운로드 URL이 지정되지 않았습니다. (apiInfo.excelUrl)");
			JWORKS_JSSnackBar.create("엑셀 다운로드 기능이 설정되지 않았습니다.");
			return;
		}
		
//		$img.attr('src', loadingIcon);

		$.ajax({
			url: downloadUrl,
			type: "POST",
			contentType: "application/json",
			data: JSON.stringify(data),
			xhrFields: {
				responseType: 'blob'
			},
			success: function(blob, status, xhr) {
				// 파일명 추출
				const disposition = xhr.getResponseHeader('Content-Disposition');
				let filename = "AuditReport.xlsx"; // 기본 파일명
				if (disposition && disposition.indexOf('attachment') !== -1) {
					const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
					const matches = filenameRegex.exec(disposition);
					if (matches != null && matches[1]) {
						filename = matches[1].replace(/['"]/g, '');
					}
				}

				// a링크 > 클릭 이벤트 발생 > 다운로드 실행
				const url = window.URL.createObjectURL(blob);
				const a = document.createElement('a');
				a.href = url
				a.download = decodeURIComponent(filename); // UTF-8 디코딩
				document.body.appendChild(a);
				a.click();
				a.remove(); // 링크 제거
				window.URL.revokeObjectURL(url); // 임시 URL 해제
			},
			error: function(xhr, status, error) {
				console.error("Excel Download Error:", error);
				JWORKS_JSSnackBar.create("엑셀 다운로드 중 오류가 발생했습니다.");
			}
//		}).always(function() {
//			// 3. 요청이 성공하든, 실패하든, 완료되면 항상 아이콘을 '기본' 상태로 되돌림
//			$img.attr('src', defaultIcon);
		});
	}
	
	// 엑셀 업로드
	tableView.excelUploadCallback = function(extraParams) {
		// 1. 파일 input 요소를 동적으로 생성
		const input = document.createElement("input");
		input.type = "file";
		input.accept = ".xlsx,.xls"; // .xls, .xlsx 확장자만 선택 가능하도록 제한

		// 2. 파일이 선택되었을 때 실행될 로직 (onchange 이벤트 리스너)
		input.onchange = function(e) {
			const file = e.target.files && e.target.files[0];

			if (!file) {
				return;
			}
			
			JWORKS_JSConfirm.setOption({
				cancelButtonText: "취소",
				okButtonText: "확인"
			});

			// 업로드 확인 창 띄우기
			JWORKS_JSConfirm.start("", "엑셀 파일을 업로드하시겠습니까?", function(result) {
				if (result) {
					// FormData 객체 생성
					const formData = new FormData();
					formData.append("file", file); // 서버의 @RequestParam("file")과 이름 일치

					// 서버로 파일 전송 (AJAX)
					$.ajax({
						url: apiInfo.excelUploadUrl,
						type: "POST",
						data: formData,

						// 파일 전송 시 필수 옵션
						contentType: false, // jQuery가 Content-Type 헤더를 자동으로 설정하는 것을 방지
						processData: false, // 데이터를 쿼리 문자열로 변환하는 것을 방지

						success: function(response) {
							if (response.code === 200) {
								JWORKS_JSAlert.start("", '<strong>업로드 성공</strong><br><br>' + response.msg, function() {
									location.reload();
								});
							} else {
								const errorMessages = Array.isArray(response.msg) ? response.msg.join('<br>') : response.msg;
								JWORKS_JSAlert.start("", '<strong>업로드 실패</strong><br><br>' + errorMessages, function() { });
							}
						},
						error: function(err) {
							console.error("업로드 실패:", err);
							JWORKS_JSAlert.start("", "업로드 중 오류가 발생했습니다.", function() { });
						}
					});
				}

			});
		};

		// 3. 생성된 input 요소를 클릭하여 파일 선택창을 띄움
		input.click();
	}

	// CSV 업로드
	tableView.csvUploadCallback = function(extraParams) {
		// 1. 파일 input 요소를 동적으로 생성
		const input = document.createElement("input");
		input.type = "file";
		input.accept = ".csv"; // .csv 확장자만 선택 가능하도록 제한

		// 2. 파일이 선택되었을 때 실행될 로직 (onchange 이벤트 리스너)
		input.onchange = function(e) {
			const file = e.target.files && e.target.files[0];

			if (!file) {
				return;
			}
			
			JWORKS_JSConfirm.setOption({
				cancelButtonText: "취소",
				okButtonText: "확인"
			});

			// 업로드 확인 창 띄우기
			JWORKS_JSConfirm.start("", "CSV 파일을 업로드하시겠습니까?", function(result) {
				if (result) {
					// FormData 객체 생성
					const formData = new FormData();
					formData.append("file", file); // 서버의 @RequestParam("file")과 이름 일치

					// 서버로 파일 전송 (AJAX)
					$.ajax({
						url: apiInfo.csvUploadUrl,
						type: "POST",
						data: formData,

						// 파일 전송 시 필수 옵션
						contentType: false, // jQuery가 Content-Type 헤더를 자동으로 설정하는 것을 방지
						processData: false, // 데이터를 쿼리 문자열로 변환하는 것을 방지

						success: function(response) {
							if (response.code === 200) {
								JWORKS_JSAlert.start("", '<strong>업로드 성공</strong><br><br>' + response.msg, function() {
									location.reload();
								});
							} else {
								const errorMessages = Array.isArray(response.msg) ? response.msg.join('<br>') : response.msg;
								JWORKS_JSAlert.start("", '<strong>업로드 실패</strong><br><br>' + errorMessages, function() { });
							}
						},
						error: function(err) {
							console.error("업로드 실패:", err);
							JWORKS_JSAlert.start("", "업로드 중 오류가 발생했습니다.", function() { });
						}
					});
				}

			});
		};

		// 3. 생성된 input 요소를 클릭하여 파일 선택창을 띄움
		input.click();
	}

	// 속성 chips 렌더링
	tableView.renderAttributeChip = function(jsonString) {
		if (!jsonString || jsonString === "{}") {
			return "";
		}

		try {
			const attributes = JSON.parse(jsonString);
			const keys = Object.keys(attributes);

			if (keys.length === 0) return "";

			const firstKey = keys[0];
			const firstValue = attributes[firstKey] || '-';
			const fullTooltip = keys.map(k => `${k} : ${attributes[k]}`).join(', ');

			let html = `
	            <div class="attribute-chip-container" title="${fullTooltip}">
	                <div class="chip">
	                    <span class="key font-body-01">${firstKey}</span>
	                    <span class="value font-body-01">${firstValue || '-'}</span>
	                </div>
	        `;

			if (keys.length > 1) {
				html += `<span class="more-indicator font-body-02">...</span>`;
			}

			html += `</div>`;
			return html;

		} catch (e) {
			console.error("JSON Parsing Error : ", e);
			return "<span>Error</span>";
		}
	}

	tableView.getList = function() {
		const data = tableView.getCurrentSearchData();

		// API 호출
		$.ajax({
			url: apiInfo.url,
			type: "POST",
			cache: false,
			contentType: "application/json",
			data: JSON.stringify(data),
			dataType: "JSON",
			success: function(result, status, xhr) {
				getListPostAction(result);
			},
			error: function(xhr, status, error) {
				console.log(xhr);
				console.log(status);
				console.log(xhr.responseText);
			},
			timeout: Constants.DEFAULT_AJAX_TIMEOUT
		});

	}

	tableView.setCurrPage = function(page) {
		paginationInfo.currPage = page;
	}

	tableView.getCurrPage = function() {
		return paginationInfo.currPage;
	}

	function getListPostAction(res) {
		if (res.code == 200) {
			console.log(res);
		}
		else {
			alert(res.msg);
			return;
		}

		// 삭제 후 저장된 페이지가 비어 있으면 한 페이지 앞으로 보정한다.
		if (res.data && +res.data.totalCount > 0 && (!res.data.list || res.data.list.length === 0) && paginationInfo.currPage > 1) {
			paginationInfo.currPage = paginationInfo.currPage - 1;
			tableView.getList();
			return;
		}

		render(res.data);

		apiInfo.renderCallback(res);
	}

	function displayHide(index) {
		// 규칙 추가
		sheet.insertRule(`
			section#table-view table colgroup col:nth-child(${index}) {
		    	display: none;
			}
		`, 0);
		sheet.insertRule(`
			section#table-view table thead td:nth-child(${index}) {
		    	display: none;
			}
		`, 0);
		sheet.insertRule(`
			section#table-view table tbody td:nth-child(${index}) {
		    	display: none;
			}
		`, 0);
	}

	function render(data) {
		const countPerPage = paginationInfo.countPerPage == 0 ? Constants.MIN_TABLE_VIEW_COUNT_PER_PAGE : paginationInfo.countPerPage; 
		 
		// totalCount 숫자로 변환하여 저장
		paginationInfo.totalCount = +data.totalCount;

		// total
		{
			$container.find("section.total .count").text(paginationInfo.totalCount.toLocaleString());
		}

		const $layoutBody = $container.find(".layout-body");
		$layoutBody.empty();
		// 데이터가 없는 경우 
		if (paginationInfo.totalCount === 0) {
			const popupClass = isPopupMode ? ' popup' : '';
			
			$layoutBody.append(`
				<div class="empty-case${popupClass}">
					<img src="/images/admin/icon-no-data.png" alt="empty icon" />
					<p class="main-text font-heading-05">${apiInfo.emptyMainText}</p>
					<p class="sub-text font-heading-09">${apiInfo.emptySubText}</p>
				</div>
			`);
		}
		// 데이터가 있는 경우 
		else {
			let classAttr = "";
		    classAttr = ` class="${tableType}"`;
			$layoutBody.append(`
				<table${classAttr}>
					<thead></thead>
					<tbody></tbody>
				</table>
			`);

			// thead
			{
				let appendStr = "<tr>";
				data.header.forEach(function(item, index) {
					// 라디오 모드일 때는 checkbox 컬럼을 빈 td로 렌더링
					if (selectionType === 'radio' && item.name === 'checkbox' && item.displayYn == "Y") {
						appendStr += `<td></td>`;
						return; // 빈 td만 추가하고 다음 항목으로
					}
					
					// display 하는 경우
					if (item.displayYn == "Y") {
						// sort 해당 컬럼인 경우
						if (item.sortYn == "Y") {
							appendStr += `
								<td data-name="${item.name}">
									<div>
										<span>${item.displayName}</span>
										<div class="sort-icon">
							`;
							const sortItem = sorts.find(sort => sort.item === item.name);
							// 현재 sort 되어 있는 경우
							if (sortItem) {
								if (sortItem.order === "asc") {
									appendStr += `<img class="sort-asc" alt="오름차순" src="/images/admin/icon-sort-asc.png">`;
									appendStr += `<img class="sort-desc" alt="오름차순" src="/images/admin/icon-sort-desc-disable.png">`;
								}
								else if (sortItem.order === "desc") {
									appendStr += `<img class="sort-asc" alt="오름차순" src="/images/admin/icon-sort-asc-disable.png">`;
									appendStr += `<img class="sort-desc" alt="오름차순" src="/images/admin/icon-sort-desc.png">`;
								}
								else {
									// unreachable
									console.log(`unreachable sort info : ${JSON.stringify(sortItem)}`)
								}
							}
							// 현재 sort 되어 있지 않은 경우
							else {
								appendStr += `<img class="sort-asc" alt="오름차순" src="/images/admin/icon-sort-asc-disable.png">`;
								appendStr += `<img class="sort-desc" alt="오름차순" src="/images/admin/icon-sort-desc-disable.png">`;
							}
							appendStr += `
										</div>
									</div>
								</td>
							`;
						}
						// sort 해당 컬럼이 아닌 경우
						else {
							appendStr += `
								<td data-name="${item.name}">
									<div>
										<span>${item.displayName}</span>
									</div>
								</td>
							`;
						}
					}
					// display 않는 경우
					else {
						// no action
						appendStr += `
							<td data-name="${item.name}">
								<div>
									<span>${item.displayName}</span>
								</div>
							</td>
						`;
						displayHide(index + 1);
					}
				});
				appendStr += "</tr>";

				$container.find("table thead").empty();
				$container.find("table thead").append(appendStr);
			}

			// tbody
			$container.find("table tbody").empty();
			const totalPage = Math.ceil(paginationInfo.totalCount / countPerPage);
			const isLastPage = totalPage === paginationInfo.currPage ? true : false;
			const remainder = paginationInfo.totalCount % countPerPage;
			if (isLastPage && remainder > 0) {
				// 실제 렌더링된 컬럼 수 계산 (displayYn이 "Y"인 항목만)
				const visibleHeaderCount = data.header.filter(function(item) {
					return item.displayYn == "Y";
				}).length;
				
				// 나머지 빈 row
				let appendStr = "<tr>";
//				appendStr += "<td></td>".repeat(visibleHeaderCount);
				// displayHide(n) CSS가 빈 row에서 visible 컬럼 수보다 하나 더 숨기는 것을 방지
				appendStr += "<td></td>".repeat(data.header.length);
				appendStr += "</tr>";
				appendStr = appendStr.repeat(countPerPage - remainder);
				$container.find("table tbody").append(appendStr);
			}
		}

		// pagination
		MagicIAM_JSPagination.setPage(paginationInfo);
		
		// 버튼 비활성화
		MagicIAM_JSCommonUtils.updateSelectionState($container);

	}

})(window.MagicIAM_JSCommonListTableView);
