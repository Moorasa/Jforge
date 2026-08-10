/* ===============================================================================================

Name : jworks-empty-view-0.0.1.js

Description :
	데이터가 없을 때 화면에 표시되는 공통 Empty View 처리

Dependency :
	없음 (Vanilla JS)

License :
	Original BSD License 준용

Remarks :
	뷰 타입(카드/테이블/트리 등)과 무관하게 사용할 수 있도록 공통 empty-case 클래스 사용
	아이콘 옵션은 생략 가능하며, false일 경우 아이콘 미표시

=============================================================================================== */

"undefined" == typeof JWORKS_JSEmptyView && ((JWORKS_JSEmptyView = {}), (function(empty) {

	// 기본 옵션 정의
	empty.option = {
		icon: "/images/admin/icon-no-data.png",          // 기본 아이콘 (생략 시 자동 적용)
		mainText: "데이터가 없습니다",                     // 메인 텍스트
		subText: "새로운 데이터를 추가해보세요.",         // 서브 텍스트
		container: null                                   // 필수: 삽입할 DOM selector
	};

	// 옵션 설정
	empty.setOption = function(option) {
		if (!option || typeof option !== 'object') return;
		for (let key in option) {
			if (empty.option.hasOwnProperty(key)) {
				empty.option[key] = option[key];
			}
		}
	};

	// EmptyView 렌더링
	empty.render = function(option = {}) {
		const settings = Object.assign({}, empty.option, option);

		if (!settings.container) {
			console.warn("JWORKS_JSEmptyView: container 선택자는 필수입니다.");
			return;
		}

		let iconHtml = '';
		if (settings.icon !== false) {
			const iconPath = settings.icon || empty.option.icon;
			iconHtml = `<img src="${iconPath}" alt="empty icon" />`;
		}

		const html = `
			<div class="empty-case">
				${iconHtml}
				<p class="main-text">${settings.mainText}</p>
				<p class="sub-text">${settings.subText}</p>
			</div>
		`;

		const containerEl = document.querySelector(settings.container);
		if (containerEl) {
			containerEl.innerHTML = html;
		}
	};

})(JWORKS_JSEmptyView));
