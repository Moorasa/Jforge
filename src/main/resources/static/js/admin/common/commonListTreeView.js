/* ===============================================================================================

Name : JWorks_JSCommonListTreeView.js

Description :
	JWORKS 프론트엔드 모듈 Tree View에서 공통으로 사용하는 유틸리티 파일입니다.

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.JWorks_JSCommonListTreeView = window.JWorks_JSCommonListTreeView || {};
(function(treeView) {
	"use strict";

	if (treeView.__defined) {
		return;
	}
	treeView.__defined = true;


	let $container = null;
	let apiInfo = null;
	let features = null; // 체크박스 등 기능 설정
	let callbacks = null; // 이벤트 콜백 함수들
	let dataMapping = null; // 데이터-UI 매핑 정보
	let orderingState = null;
	let searchOptions = null;
	let searchIndex = [];
	let lastSearch = null;
	let lastMatchCount = 0;

	treeView.init = function(options) {
		if (!options || !options.$container || !options.$container.length) {
			console.error("treeView.init: 필수 옵션인 $container가 유효하지 않습니다.");
			return;
		}

		$container = options.$container;
		apiInfo = options.apiInfo;
		features = options.features || {};
		callbacks = options.callbacks || {};
		dataMapping = options.dataMapping || {};
		searchOptions = options.search || {};
		searchIndex = [];
		lastSearch = null;
		lastMatchCount = 0;

		if (features.ordering && features.ordering.enable) {
			if (options.orderingState) {
				orderingState = options.orderingState;
			} else {
				console.error("treeView.init: 'ordering' 기능이 활성화되었으나, 'orderingState' 객체가 전달되지 않았습니다.");
				// 비상시를 대비해 빈 객체라도 할당
				orderingState = { ordering: {}, orderingUpdate: {}, orderingOrigin: {} };
			}
		}

		registEvent();
	}

	function registEvent() {
		// 접기/펼치기 버튼 클릭 이벤트 (root)
		$container
			.off("click", ".tree-static-root .tree-toggle")
			.on("click", ".tree-static-root .tree-toggle", function(e) {
				e.stopPropagation();
				const $rootHeader = $(this).closest('.tree-static-root');

				$rootHeader.toggleClass('expand');
				$rootHeader.next('.tree-list').slideToggle(200);
			});

		// 접기/펼치기 버튼 클릭 이벤트 (tree)
		$container
			.off("click", ".tree-node .tree-toggle")
			.on("click", ".tree-node .tree-toggle", function(e) {
				e.stopPropagation();
				const $nodeLi = $(this).closest('.tree-node-label');

				$nodeLi.toggleClass('expand');
				$nodeLi.next('.tree-subtree').slideToggle(200);
			});

		// 노드 클릭 이벤트
		$container
			.off("click", ".tree-node, .tree-static-root")
			.on("click", ".tree-node, .tree-static-root", function(e) {
				if ($(e.target).closest(".stop-node-click").length > 0) {
					return;
				}
				e.stopPropagation();
				const $nodeLi = $(this);
				const nodeData = $nodeLi.data('node');

				// 선택된 node 강조
				$(".tree-static-root").removeClass("selected");
				$(".tree-list .tree-node-label").removeClass("selected");
				if($(this).hasClass("tree-static-root"))
					$(".tree-static-root").addClass("selected");
				else {
					$(this).find("> .tree-node-label").addClass("selected");
				}

				// 콜백 함수 실행
				// 1. 사용자/부서/업무/권한 등 그룹 트리 : 상세 화면으로 이동
				// 2. 업무추가 팝업의 업무 트리 : 선택 강조 효과, 체크박스
				if (callbacks.onNodeClick && typeof callbacks.onNodeClick === 'function') {
					callbacks.onNodeClick(nodeData, e);
				}
			});

		$container
			.off("click", ".tree-checkbox")
			.on("click", ".tree-checkbox", function(e) {
				e.stopPropagation();
				const $checkbox = $(this);
				const nodeData = $checkbox.closest('.tree-node').data('node');

				if ($checkbox.hasClass('root-checkbox')) {
					// 루트 : '전체 선택/해제'를 직접 수행
					const isChecked = $checkbox.prop('checked');
					$container.find('.tree-node .tree-checkbox:not(:disabled)').prop('checked', isChecked);
				}

				// 일반 노드 ： 콜백 함수 실행
				// 1. 사용자/부서/업무/권한 등 그룹 트리 : 체크박스
				// 2. 업무추가 팝업의 업무 트리 : 하위업무 클릭 시 상위업무 체크, 상위업무 체크해제 시 하위업무 체크해제
				if (callbacks.onCheckChange && typeof callbacks.onCheckChange === 'function') {
					callbacks.onCheckChange(e, nodeData, $checkbox);
				}
			});
	}

	treeView.getList = function() {
		const data = {
			...{
				//				category: $filter.val(),
				//				searchKeyword: $container.find("section.search .input").text(),
				//				searchColumns: searchColumns,
				//				filters: filters,
				//				countPerPage: paginationInfo.countPerPage.toString(),
				//				currPage: paginationInfo.currPage.toString(),
				//				sorts: sorts
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
			success: function(result, status, xhr) {
				getListPostAction(result);
			},
			error: function(xhr, status, error) {
				console.error("Tree 데이터 로드 오류:", error);
				$container.html("데이터를 불러오는 중 오류가 발생했습니다.");
			},
			timeout: Constants.DEFAULT_AJAX_TIMEOUT * 5
		});
	}

	/*
	 * 검색 공통:
	 * 1. 검색 결과는 사용자가 계층 위치를 알 수 있도록 root 부터 해당 노드까지의 상위 경로를 남기고 나머지 노드를 숨기는 방식
	 * 2. 검색 시 서버/API 호출 하지 않음
	 * TODO: 성능 비교를 위해 search, searchIndexed를 각각 구현 > 테스트 후 최종 search만 남길 예정
	 */

	// 1. DOM 전체 순회 방식
	// 검색 방식: 매 검색마다 현재 브라우저에 그려져 있는 모든 노드 DOM 전체 순회
	// 단점: 매 검색마다 DOM 노드 순회와 대량 show/hide DOM 조작에 드는 비용이 큼
	treeView.search = function(keyword) {
		const startedAt = performance.now();

		if (!$container || !$container.length) {
			return;
		}

		const normalizedKeyword = $.trim(String(keyword || ""));

		const $layoutBody = $container.find(".layout-body").last();
		const $treeList = $layoutBody.find(".tree-list").first();
		const $nodes = $treeList.find("li.tree-node");

		$layoutBody.find(".empty-case").remove();

		if (!$nodes.length) {
			return;
		}

		// 검색어 초기화 (빈 검색어): resetBehavior 옵션을 통해 원래 상태로 복원
		if (!normalizedKeyword) {
			$nodes.show().removeClass("tree-search-match");
			$nodes.children(".tree-node-label").removeClass("tree-search-match");
			$layoutBody.find(".tree-node-label").removeClass("expand");

			if (searchOptions.resetBehavior === "expandAll") {
				// 노드 전체 펼치기
				$layoutBody.find(".tree-static-root").show().addClass("expand");
				$treeList.show();
				$layoutBody.find(".tree-subtree").show();
				$layoutBody.find(".tree-node-label").each(function() {
					if ($(this).next(".tree-subtree").length > 0) {
						$(this).addClass("expand");
					}
				});
			} else {
				// 최상위 목록까지만 보이는 접힘 상태
				$layoutBody.find(".tree-static-root").show().addClass("expand");
				$treeList.show();
				$layoutBody.find(".tree-subtree").hide();
			}
			console.log(
				"[Tree Search]",
				"keyword:", "(empty)",
				"time:", (performance.now() - startedAt).toFixed(2) + "ms",
				"matchCount:", 0,
				"nodeCount:", $nodes.length
			);
			return;
		}

		// 실제 검색 처리: 일단 전부 숨긴 뒤, 매칭되는 노드와 그 상위 경로만 다시 펼침
		let matchCount = 0;
		$layoutBody.find(".tree-static-root").show().addClass("expand");
		$treeList.show();
		$nodes.hide().removeClass("tree-search-match");
		$nodes.children(".tree-node-label").removeClass("tree-search-match");
		$layoutBody.find(".tree-subtree").hide();
		$layoutBody.find(".tree-node-label").removeClass("expand");

		$nodes.each(function() {
			const $node = $(this);
			// 한 번 추출한 검색용 텍스트를 노드에 저장해 두고, 이후 같은 노드는 이 값을 재사용
			let nodeText = $node.data("treeSearchText");

			if (nodeText === undefined) {
				const text = $.trim($node.children(".tree-node-label").find(".name").first().text() || "");
				nodeText = $.trim(String(text || ""));
				$node.data("treeSearchText", nodeText);
			}

			if (nodeText.indexOf(normalizedKeyword) < 0) {
				return;
			}

			matchCount++;
			$node.show().addClass("tree-search-match");
			$node.children(".tree-node-label").addClass("tree-search-match");

			$node.parents("li.tree-node").show();

			$node.parents("ul.tree-subtree").each(function() {
				const $subtree = $(this);
				$subtree.show();
				$subtree.prev(".tree-node-label").addClass("expand");
			});
		});

		// 매칭 결과가 하나도 없으면 기존 empty UI 형식을 재사용해서 안내 영역을 출력
		if (matchCount === 0) {
			$treeList.after(`
		            <div class="empty-case">
		                <img src="/images/admin/icon-no-data.png" alt="empty icon" />
		                <p class="main-text">${apiInfo.emptyMainText}</p>
		                <p class="sub-text">${apiInfo.emptySubText}</p>
		            </div>
		        `);
		}
		console.log(
			"[Tree Search]",
			"keyword:", keyword || "(empty)",
			"time:", (performance.now() - startedAt).toFixed(2) + "ms",
			"matchCount:", matchCount,
			"nodeCount:", $nodes.length
		);
	}

	// 2. 검색 인덱스 생성 방식
	// 검색 방식: DOM은 그대로 두고, 최초 검색 시 검색용 인덱스를 한 번 만들어 재사용
	// 단점:
	treeView.searchIndexed = function(keyword) {
		const startedAt = performance.now();

		if (!$container || !$container.length) {
			return;
		}

		const normalizedKeyword = $.trim(String(keyword || ""));

		const $layoutBody = $container.find(".layout-body").last();
		const $treeList = $layoutBody.find(".tree-list").first();
		const $nodes = $treeList.find("li.tree-node");

		$layoutBody.find(".empty-case").remove();

		if (!$nodes.length) {
			return;
		}

		// 트리가 다시 렌더링되면 기존 인덱스의 DOM 참조가 끊어지므로 인덱스 재생성
		if (!searchIndex.length || !$.contains(document, searchIndex[0].node)) {
			treeView.buildSearchIndex();
			lastSearch = null;
		}

		// 검색어 초기화 (빈 검색어): resetBehavior 옵션을 통해 원래 상태로 복원
		if (!normalizedKeyword) {
			lastSearch = null;
			lastMatchCount = 0;
			$nodes.show().removeClass("tree-search-match");
			$nodes.children(".tree-node-label").removeClass("tree-search-match");
			$layoutBody.find(".tree-node-label").removeClass("expand");

			if (searchOptions.resetBehavior === "expandAll") {
				// 전체 노드 펼치기
				$layoutBody.find(".tree-static-root").show().addClass("expand");
				$treeList.show();
				$layoutBody.find(".tree-subtree").show();
				$layoutBody.find(".tree-node-label").each(function() {
					if ($(this).next(".tree-subtree").length > 0) {
						$(this).addClass("expand");
					}
				});
			} else {
				// 최상위 목록까지만 보이는 접힘 상태
				$layoutBody.find(".tree-static-root").show().addClass("expand");
				$treeList.show();
				$layoutBody.find(".tree-subtree").hide();
			}
			console.log(
				"[Tree Search Indexed]",
				"keyword:", "(empty)",
				"time:", (performance.now() - startedAt).toFixed(2) + "ms",
				"matchCount:", lastMatchCount,
				"indexCount:", searchIndex.length
			);
			return;
		}

		// 다음 검색 시 빠르게 정리하기 위해 이번 검색에 사용된 DOM을 저장
		let matchCount = 0;
		const nodes = [];
		const subtrees = [];
		const labels = [];
		const matchedLabels = [];

		$layoutBody.find(".tree-static-root").show().addClass("expand");
		$treeList.show();

		// 두 번째 검색부터는 직전 검색에서 열고 표시한 부분만 원복
		if (lastSearch) {
			$(lastSearch.nodes).hide();
			$(lastSearch.subtrees).hide();
			$(lastSearch.labels).removeClass("expand");
			$(lastSearch.matchedLabels).removeClass("tree-search-match");
		} else {
			// 첫 검색은 이전 상태를 모르기 때문에 전체 트리를 검색 모드의 기본 상태로 맞춤
			$nodes.hide().removeClass("tree-search-match");
			$layoutBody.find(".tree-subtree").hide();
			$layoutBody.find(".tree-node-label").removeClass("expand tree-search-match");
		}

		searchIndex.forEach(function(item) {
			// 검색어 비교는 미리 저장한 라벨 텍스트만 사용
			if (item.text.indexOf(normalizedKeyword) < 0) {
				return;
			}

			matchCount++;
			// 매칭 노드와 부모 경로를 함께 보여야 트리 안의 위치 파악 가능
			item.$node.show().addClass("tree-search-match");
			item.$label.addClass("tree-search-match");
			item.$parents.show();
			item.$parentSubtrees.show();
			item.$parentSubtrees.prev(".tree-node-label").addClass("expand");

			// 이번에 표시한 DOM을 저장해 다음 검색에서 전체 트리를 다시 조작하지 않음
			nodes.push(item.node);
			matchedLabels.push(item.$label[0]);
			Array.prototype.push.apply(nodes, item.$parents.get());
			Array.prototype.push.apply(subtrees, item.$parentSubtrees.get());
			Array.prototype.push.apply(labels, item.$parentSubtrees.prev(".tree-node-label").get());
		});

		// 다음 검색은 이 목록만 정리한 뒤 새 결과를 표시
		lastSearch = {
			nodes: nodes,
			subtrees: subtrees,
			labels: labels,
			matchedLabels: matchedLabels
		};
		lastMatchCount = matchCount;

		// 매칭 결과가 하나도 없으면 기존 empty UI 형식을 재사용해서 안내 영역을 출력
		if (matchCount === 0) {
			$treeList.after(`
		            <div class="empty-case">
		                <img src="/images/admin/icon-no-data.png" alt="empty icon" />
		                <p class="main-text">${apiInfo.emptyMainText}</p>
		                <p class="sub-text">${apiInfo.emptySubText}</p>
		            </div>
		        `);
		}

		console.log(
			"[Tree Search Indexed]",
			"keyword:", keyword || "(empty)",
			"time:", (performance.now() - startedAt).toFixed(2) + "ms",
			"matchCount:", lastMatchCount,
			"indexCount:", searchIndex.length
		);
	}

	// searchIndex 배열 생성
	treeView.buildSearchIndex = function() {
		searchIndex = [];

		if (!$container || !$container.length) {
			return;
		}

		const $layoutBody = $container.find(".layout-body").last();
		const $treeList = $layoutBody.find(".tree-list").first();

		// 렌더링된 노드를 한 번 훑어 검색에 필요한 값과 DOM 참조를 저장
		$treeList.find("li.tree-node").each(function() {
			const $node = $(this);
			const $label = $node.children(".tree-node-label").first();
			let text = $.trim($label.find(".name").first().text() || "");

			searchIndex.push({
				node: this,
				// 매 검색마다 DOM에서 text를 다시 읽지 않도록 비교용 문자열을 미리 생성
				text: $.trim(String(text || "")),
				$node: $node,
				$label: $label,
				// 부모 노드와 부모 subtree는 매칭 결과를 펼칠 때 그대로 재사용
				$parents: $node.parents("li.tree-node"),
				$parentSubtrees: $node.parents("ul.tree-subtree")
			});
		});
	}

	function getListPostAction(res) {
		if (res.code == 200) {
			console.log(res);
		}
		else {
			alert(res.msg);
			return;
		}

		render(res.data);

		apiInfo.renderCallback(res);
	}

	// data 배열과 totalCount 숫자를 포함한 객체를 파라미터로 받음
	function render(data) {
		console.time("Tree Rendering Time");
		
		const totalCount = +data.totalCount;
		const $layoutBody = $container.find(".layout-body");

		$container.find("section.total .count").text(totalCount.toLocaleString());
		$layoutBody.empty();
		
		// 1. ROOT 생성 및 treeContainer 생성
		let rootCheckboxHtml = '';
		if (features.checkbox && features.checkbox.enable) {
			rootCheckboxHtml = '<input type="checkbox" class="tree-checkbox root-checkbox"/>';
		}

		const $treeContainer = $(`
				<div>
		            <div class="tree-static-root">
		                ${rootCheckboxHtml}
		                <div class="tree-toggle"></div>
		                <img class="${dataMapping.root.iconClass}" src="/images/admin/${dataMapping.root.iconClass}.png" alt="GROUP ROOT 아이콘">
		                <span class="name">${dataMapping.root.name}</span>
		            </div>
					<ul class="tree-list"></ul>
				</div>
		        `);
		$layoutBody.append($treeContainer);

		// 2. 파서로 데이터 변환
		const standardNodes = apiInfo.parser(data);

		// 3. 재귀 함수를 호출하여 순수 HTML 문자열 생성
		const treeHtmlString = createTreeHtmlString(standardNodes);

		// 4. 문자열을 한 번에 append 하여 렌더링 비용 최소화
		$treeContainer.find(".tree-list").append(treeHtmlString);
		
		setTimeout(function() {
			console.timeEnd("Tree Rendering Time");
		}, 0);
	}
	
	// 렌더링 속도 개선: 제이쿼리 객체 대신 순수 HTML 문자열 생성
	// TODO: 노드 데이터 10만건 넘어갈 경우 API 지연 호출 방식, 지연 렌더링 등을 고려해야함
	function createTreeHtmlString(nodes, depth = 1) {
		if (!nodes || nodes.length === 0) {
			return '';
		}

		const htmlArray = [];

		nodes.forEach(function(nodeData) {
			const nodeClass = nodeData.disabled ? ' tree-node-disabled' : '';
			let liAttrs = 'class="tree-node' + nodeClass + '" data-node-no="' + nodeData.nodeNo + '"';

			// nodeNo외의 필요한 nodeData 속성 추출
			if (features.getNodeData && typeof features.getNodeData === 'function') {
				const extraData = features.getNodeData(nodeData) || {};
				for (const key in extraData) {
					let safeValue = "";
					if (extraData[key] !== null && extraData[key] !== undefined) {
						safeValue = escapeHtml(String(extraData[key])) // 안전한 문자열로 변경
					}
					liAttrs += ' ' + key + '="' + safeValue + '"';
				}
			}

			htmlArray.push('<li ' + liAttrs + '>');

			const labelClass = nodeData.disabled ? ' tree-node-disabled' : '';
			let labelHtml = '<div class="tree-node-label expand' + labelClass + '" style="padding-left: calc(' + depth + ' * 1.25rem);">';
			const hasChildren = nodeData.children && nodeData.children.length > 0;

			// 체크박스
			if (features.checkbox && features.checkbox.enable) {
				const checkboxAttr = nodeData.disabled ? ' checked disabled' : '';
				labelHtml += '<input type="checkbox" class="tree-checkbox"' + checkboxAttr + '>';
			}

			// 접기/펼치기 아이콘
			if (hasChildren) {
				labelHtml += '<span class="tree-toggle"></span>';
			} else {
				labelHtml += '<span class="tree-toggle-spacer"></span>';
			}

			// 아이콘
			if (nodeData.iconClass && nodeData.iconClass.length > 0) {
				labelHtml += '<img class="' + nodeData.iconClass + '" src="/images/admin/' + nodeData.iconClass + '.png" alt="업무 아이콘">';
			}

			// 노드명
			labelHtml += '<span class="name">' + nodeData.name + ' (' + nodeData.originalData.id + ')</span>';

			// Ordering input
			if (features.ordering && features.ordering.enable) {
				// 260312: parentNo 참조 구조로 변경 작업 > code 사용하지 않음
				const orderingKey = nodeData.nodeNo;
				const orderingValue = (nodeData.originalData && (nodeData.originalData.ordering ?? "")) || "";

				if (orderingState) {
					orderingState.ordering[orderingKey] = { VALUE: orderingValue };
					orderingState.orderingOrigin[orderingKey] = { VALUE: orderingValue };
				}

				labelHtml += '<input class="ordering-input" id="' + orderingKey + '" value="' + orderingValue + '" inputmode="numeric" title="정렬(순서)" disabled />';
				labelHtml += '<button type="button" class="apply-order stop-node-click basic-button-28-solid-sub4-500" style="display:none;">적용</button>';
			}

			labelHtml += '</div>';
			htmlArray.push(labelHtml);

			// 자식 노드 처리 (재귀)
			if (hasChildren) {
				htmlArray.push('<ul class="tree-subtree" style="display:none;">');
				htmlArray.push(createTreeHtmlString(nodeData.children, depth + 1));
				htmlArray.push('</ul>');
			}

			htmlArray.push('</li>');
		});

		return htmlArray.join('');
	}
	
	// TODO: HTML 이스케이프 함수 (공통 유틸)
	function escapeHtml(unsafeString) {
		if (typeof unsafeString !== 'string') {
			return unsafeString;
		}
		return unsafeString
			.replace(/&/g, "&amp;")
			.replace(/</g, "&lt;")
			.replace(/>/g, "&gt;")
			.replace(/"/g, "&quot;")
			.replace(/'/g, "&#039;");
	};

})(window.JWorks_JSCommonListTreeView);
