/* ===============================================================================================

Name : MagicIAM_JSCommonAttributeHandler.js

Description :
	속성을 관리하는 모든 화면에서 공통으로 사용하는 유틸리티 파일

Remarks :
	재배포를 금합니다.
	
=============================================================================================== */
window.MagicIAM_JSCommonAttributeHandler = window.MagicIAM_JSCommonAttributeHandler || {};
(function(attributeHandler) {
	"use strict";

	if (attributeHandler.__defined) {
		return;
	}
	attributeHandler.__defined = true;


	attributeHandler.init = function(context) {

		// 최초 로딩 시 '기본 속성 지정' 속성 리스트 렌더링
		loadAttributeData(context, function(isSuccess) {
			if (!isSuccess) return;
			
			const $container = context.$container;
			const existingData = context.attribute.initialData || {};
			const state = $container.data("attr-state");
			
			// 비우기
			const $attrBody = $container.find(".attribute-body");
			$attrBody.children().not(".empty-case").remove();
						
			// 기존 데이터가 있으면 먼저 렌더링
			if (existingData) {
				$.each(existingData, function(key, value) {
					attributeHandler.addAttributeRow($container, key, value);
				});
			} 
			
			// 템플릿 리스트가 있으면 렌더링
			if (state && state.list) {
				state.list.forEach(function(templateItem) {
					// 기존 데이터에 해당 키가 없을 때만 추가
					if (!existingData || !existingData.hasOwnProperty(templateItem.name)) {
						if (templateItem.required) {
							attributeHandler.addAttributeRow($container, templateItem.name, templateItem.defaultValue || "");
						}
					}
				});
			}
		});

		registEvent(context);

	}

	function registEvent(context) {
		const $container = context.$container;
		const $attrBody = $container.find(".attribute-body");
		const scrollSelector = context.attribute.scrollTarget || ".layout-body";
		const $scrollTarget = $container.find(scrollSelector);
		
		// 행 추가 버튼 클릭
		$container.off("click", ".attribute .insert").on("click", ".attribute .insert", function() {
			loadAttributeData(context, function(isSuccess) {
				if (isSuccess) {
					attributeHandler.addAttributeRow($container); // 파라미터 없이 호출 -> 빈 행 추가

					// 스크롤 이동
					$scrollTarget.stop().animate({
						scrollTop: $scrollTarget[0].scrollHeight
					}, 300);
				}
			});
		});

		// 행 삭제 버튼 클릭
		$container.off("click", ".attribute .delete").on("click", ".attribute .delete", function() {
			const $checkedRows = $attrBody.find("input[type='checkbox']:checked");

			if ($checkedRows.length === 0) {
				JWORKS_JSAlert.start("", "삭제할 속성을 선택하세요.");
				return;
			}

			$checkedRows.each(function() {
				$(this).closest(".attribute-row").remove();
			});

			// 삭제 후 Empty Case 처리
			if ($attrBody.find(".attribute-row").length === 0) {
				$attrBody.find(".empty-case").show();
			}
		});

		// 속성명 Input 클릭 -> 속성명 리스트 출력
		$attrBody.off("click", ".attr-key").on("click", ".attr-key", function(e) {
			const state = $container.data("attr-state");
			if (!state) return;

			const $input = $(this);
			const $wrapper = $input.closest(".input-wrapper");

			if ($wrapper.find(".attribute-dropdown").length > 0) {
				$(".attribute-dropdown").remove();
				return;
			}
			$(".attribute-dropdown").remove();

			$input.prop("readonly", true); // 속성명 선택 중이므로 입력 불가

			// '직접 입력' 메뉴 추가
			let listItems = [];
			if (state.typeCode !== "C2600002") {
				listItems.push({
					label: "직접 입력",
					value: "__DIRECT_INPUT__", // 시스템 예약어 사용 (사용자가 입력할리 없는 특수한 패턴)
					type: "system"
				});
			}

			// 속성명 데이터 매핑
			state.list.forEach(function(attr) {
				listItems.push({
					label: attr.name,
					value: attr.name,
					type: "data",
					required: attr.required
				});
			});

			// 드롭다운 렌더링 호출 : 위치 계산>input-wrapper, 데이터 출력>item list
			renderDropdown($wrapper, listItems);
		});

		// 속성값 Input 클릭 -> 옵션 리스트 출력
		$attrBody.off("click", ".attr-value").on("click", ".attr-value", function(e) {
			const state = $container.data("attr-state");
			if (!state) return;

			const $valInput = $(this);
			const $wrapper = $valInput.closest(".input-wrapper");
			const $row = $valInput.closest(".attribute-row");
			const keyName = $row.find(".attr-key").val().trim(); // 현재 입력된 속성명
			const attrData = state.map[keyName];

			// 데이터가 없거나, 옵션이 없는(텍스트 입력형) 경우 리스트 띄우지 않음
			if (!attrData || !attrData.options) {
				return;
			}
			$valInput.prop("readonly", true);

			// 옵션이 있는 경우에만 리스트 출력
			if ($wrapper.find(".attribute-dropdown").length > 0) {
				$(".attribute-dropdown").remove();
				return;
			}
			$(".attribute-dropdown").remove();

			// 옵션 데이터 매핑
			const listItems = attrData.options.map(function(opt) {
				return {
					label: opt.value,
					value: opt.value,
					type: "data",
					isDefault: (opt.value === attrData.defaultValue)
				};
			});

			// 드롭다운 렌더링 호출
			renderDropdown($wrapper, listItems);
		});

		// 드롭다운 아이템 클릭 -> 속성명/속성값 로직 분기
		$container.off("click", ".attribute-dropdown li").on("click", ".attribute-dropdown li", function(e) {
			const $li = $(this);
			const type = $li.data("type");
			const value = $li.data("value"); // 문자열

			// 어떤 Input에서 열렸는지 추적하기 위해 드롭다운에 저장해둔 타겟 ID 활용
			const targetId = $li.parent().data("target-id");
			const $targetInput = $("#" + targetId);

			if ($targetInput.hasClass("attr-key")) {
				handleNameSelect($targetInput, { value: value, type: type }); // 속성명(Key) 선택 시
			} else {
				$targetInput.val(value); // 속성값(Value) 선택 시
			}

			$(".attribute-dropdown").remove(); // 닫기
		});

		// 문서 전체 클릭 시 (input-wrapper가 없는 곳) 열려있는 드롭다운 닫기
		$(document)
			.off("click.attributeDropdown")
			.on("click.attributeDropdown", function(e) {
				if ($(e.target).closest(".input-wrapper").length === 0) {
					$(".attribute-dropdown").remove();
				}
			});

		// 스크롤 발생 시 닫기
		$scrollTarget
			.off("scroll.attributeDropdown")
			.on("scroll.attributeDropdown", function() {
				if ($(".attribute-dropdown").length > 0) {
					$(".attribute-dropdown").remove();
				}
			});

	}

	// AJAX 요청 및 데이터 처리
	function loadAttributeData(context, callback) {
		const config = context.attribute;
		$.ajax({
			url: config.url,
			type: "POST",
			data: JSON.stringify(config.param),
			contentType: "application/json",
			dataType: "JSON",
			success: function(res) {
				if (res.code === 200) {
					processAttributeData(context, res.data);
				} else {
					processAttributeData(context, []);
					JWORKS_JSAlert.start("", res.msg || "속성 영역 조회 실패");
				}

				if (callback && typeof callback === "function") {
					callback(res.code === 200); // 성공 여부 넘겨줌
				}
			},
			error: function(res) {
				JWORKS_JSAlert.start("", res.msg || "속성 영역 조회 실패");
			}
		});
	}

	// 데이터 가공
	function processAttributeData(context, serverData) {
		const $container = context.$container;

		// 속성 관리를 위한 상태 변수
		let currentTemplateType = "";
		let attributeList = [];
		let attributeMap = {};

		const hasTemplate = (serverData && serverData.length > 0); // 등록된 속성 영역 없음
		const hasItems = (hasTemplate && !!serverData[0].attributeName); // 등록된 속성 아이템 없음
		const typeCode = hasTemplate ? serverData[0].typeCode : "";

		// 전역 변수 업데이트
		if (hasTemplate) {
			currentTemplateType = typeCode;
		} else {
			currentTemplateType = "";
		}

		// 데이터 가공 : 표시할 속성 아이템이 없을 경우 빈 배열
		const rawList = hasItems ? serverData : [];
		attributeMap = {};

		const parsedList = rawList.map(function(item) {
			let parsedOptions = null;
			if (item.valueOptions) {
				try {
					parsedOptions = JSON.parse(item.valueOptions);
				} catch (e) {
					console.error("Option Parsing Error: " + item.attributeName, e);
				}
			}

			const processedItem = {
				id: item.id,
				name: item.attributeName || "",
				required: item.isRequired === 'Y',
				inputType: item.valueTypeCode,
				defaultValue: item.defaultValue,
				options: (parsedOptions && parsedOptions.length > 0) ? parsedOptions : null,
				originalItem: item
			};
			attributeMap[processedItem.name] = processedItem;
			return processedItem;
		});

		// 정렬 : 기본 속성 지정(Required) 항목을 상단으로
		attributeList = parsedList.sort(function(a, b) {
			if (a.required && !b.required) return -1;
			if (!a.required && b.required) return 1;
			return 0;
		});

		// 템플릿 타입에 따른 Placeholder 문구 결정
		let keyPh = "속성명을 입력하세요.";
		let valPh = "속성값을 입력하세요.";

		if (typeCode === "C2600002") {
			keyPh = "속성명을 선택하세요.";
			valPh = "속성값을 선택하세요.";
		} else if (typeCode === "C2600003") {
			keyPh = "속성명을 직접 입력하거나 선택하세요.";
			valPh = "속성값을 직접 입력하거나 선택하세요.";
		}

		const $attrBody = $container.find(".attribute-body");
		$attrBody.data("attr-config", { keyPlaceholder: keyPh, valPlaceholder: valPh });
		$attrBody.find(".attr-key").attr("placeholder", keyPh);
		$attrBody.find(".attr-value").attr("placeholder", valPh);

		// 상태 데이터를 $container에 저장
		$container.data("attr-state", {
			list: attributeList,
			map: attributeMap,
			typeCode: currentTemplateType
		});
	}

	// 드롭다운 렌더링
	function renderDropdown($targetWrapper, items) {
		if (items.length === 0) return;
		const $container = $targetWrapper.closest("section");

		// 컨테이너 기준 상대 좌표 계산
		const $input = $targetWrapper.find("input"); // 기준이 되는 인풋
		const targetInputId = $input.attr("id");
		const containerOffset = $container.offset();
		const inputOffset = $input.offset();
		const inputHeight = $input.outerHeight();

		// 리스트가 위치할 Top, Left 계산
		const top = inputOffset.top - containerOffset.top + inputHeight + 4; // 4px 여백
		const left = inputOffset.left - containerOffset.left;
		const width = $input.outerWidth(); // 인풋 너비와 맞춤

		let html = `<ul class="attribute-dropdown" data-target-id="${targetInputId}" style="top:${top}px; left:${left}px; width:${width}px;">`;

		items.forEach(function(item) {
			let badgeHtml = "";
			if (item.required) badgeHtml += ` <span class="required">(필수)</span>`;
			if (item.isDefault) badgeHtml += ` <span class="default-value">(기본값)</span>`;
			const typeClass = item.type === 'system' ? 'system-item' : 'data-item';

			if ($("body").hasClass("dual-layout")) {
				html += `
				        <li class="${typeClass} font-body-04" data-value="${item.value}" data-type="${item.type}">
				            ${item.label} ${badgeHtml}
				        </li>
				    `;
			} else {
				html += `
				        <li class="${typeClass} font-body-02" data-value="${item.value}" data-type="${item.type}">
				            ${item.label} ${badgeHtml}
				        </li>
				    `;
			}
			
		});
		html += `</ul>`;
		$(".attribute-dropdown").remove();
		$container.append(html);
	}

	// 속성명 선택 시 처리 로직
	function handleNameSelect($keyInput, selectedItem) {
		const $container = $keyInput.closest("section");
		const state = $container.data("attr-state");
		if (!state) return;

		const $row = $keyInput.closest(".attribute-row");
		const $valInput = $row.find(".attr-value");

		// [직접 입력 선택 시] : 입력 가능
		if (selectedItem.type === 'system' && selectedItem.value === '__DIRECT_INPUT__') {
			$keyInput.val("");
			$keyInput.prop("readonly", false);
			$keyInput.focus();
			$valInput.val("");
			$valInput.prop("readonly", false);
		// [리스트 속성 선택 시] : 해당 속성명의 속성값 선택
		} else {
			$keyInput.val(selectedItem.value);
			const attrData = state.map[selectedItem.value];

			if (attrData) {
				$valInput.val(attrData.defaultValue || "");

				// 플레이스홀더 변경
				if (attrData.options && attrData.options.length > 0) {
					$valInput.prop("readonly", true); // 직접 입력이 아니므로 입력 불가
					setTimeout(function() {
						$valInput.trigger("click");
					}, 0);
				} else {
					$valInput.prop("readonly", false);
				}
			}
		}
	}

	// 유효성 및 입력값 검사
	attributeHandler.getValidatedData = function($container) {
		// 형식 검사
		const attributeData = MagicIAM_JSCommonUtils.getAttributeData({
			$container: $container.find(".attribute-body")
		});
		if (!attributeData) return null;

		// 속성 입력값 검사
		const state = $container.data("attr-state");
		if (!state || !state.list) return attributeData;

		const requiredKey = state.list.filter(function(attr) {
			return attr.required && !attributeData.hasOwnProperty(attr.name);
		});

		if (requiredKey.length > 0) {
			const missingNames = requiredKey.map(function(a) { return a.name; }).join(", ");
			JWORKS_JSAlert.start("", "필수 속성이 누락되었습니다.<br>(" + missingNames + ")");
			return null;
		}

		return attributeData;
	}

	// 행 추가
	attributeHandler.addAttributeRow = function($container, key = "", value = "") {
		if (!$container) return;

		const $attrBody = $container.find(".attribute-body");
		$attrBody.find(".empty-case").hide();

		const config = $attrBody.data("attr-config") || {
			keyPlaceholder: "속성명을 선택하세요.",
			valPlaceholder: "속성값을 선택하세요."
		};

		const uniqueId = new Date().getTime() + Math.random().toString(36).substring(2);
		const newRowHtml = `
            <div class="attribute-row">
                <input type="checkbox" id="attr-chk-${uniqueId}">
                <label for="attr-chk-${uniqueId}" class="blind">속성 선택</label>

                <div class="input-wrapper clearable">
                    <input type="text" id="attr-key-${uniqueId}" class="attr-key" placeholder="${config.keyPlaceholder}" value="${key}">
                    <img class="clear-btn" src="/images/admin/icon-clear-button.png" alt="입력 내용 지우기">
                </div>

                <div class="input-wrapper clearable">
                    <input type="text" id="attr-value-${uniqueId}" class="attr-value" placeholder="${config.valPlaceholder}" value="${value}">
                    <img class="clear-btn" src="/images/admin/icon-clear-button.png" alt="입력 내용 지우기">
                </div>
            </div>
        `;

		$attrBody.append(newRowHtml);
	};

})(window.MagicIAM_JSCommonAttributeHandler);
