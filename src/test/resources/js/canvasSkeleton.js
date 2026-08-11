/* ===============================================================================================
  프리뷰 렌더러(previewRenderer.js)가 만드는 캔버스 DOM의 **골격**을 표준 어휘로 찍어낸다.
  CanvasPreviewParityTest(Java)가 이 스크립트를 node 로 실행해, 생성물(shell.ftl 산출 JSP)에서
  같은 방식으로 뽑은 골격과 비교한다.

  왜 필요한가: 이 프로젝트에는 계획↔산출 드리프트 그물(GenPlannerTest)은 있는데
  **프리뷰↔산출** 그물이 없었다. 그래서 §17.12 로 산출 구조를 바꿨을 때 프리뷰가 옛 모양으로
  남아 있는 것을 테스트가 못 잡았다(사람이 눈으로 발견). 이 스크립트가 그 구멍을 막는다.

  표준 어휘(두 렌더러의 태그·클래스가 서로 다르므로 의미로 환산한다):
    C  = 컨테이너 블록   (산출 .frg-fc-item.frg-fc-container / 프리뷰 .frg-fc-block.frg-fc-container)
    B  = 내용 상자       (양쪽 .frg-fc-panel-body — §17.12)
    I  = 일반 인스턴스   (산출 .frg-fc-item          / 프리뷰 .frg-fc-block)
  깊이는 들여쓰기 2칸으로 표현한다.

  사용: node canvasSkeleton.js <studioJsDir> <definitionJsonFile>
=============================================================================================== */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const jsDir = process.argv[2];
const defFile = process.argv[3];
if (!jsDir || !defFile) {
    console.error('usage: node canvasSkeleton.js <studioJsDir> <definitionJsonFile>');
    process.exit(2);
}

// ---------- 최소 DOM 셰임 ----------

function makeClassList(node) {
    return {
        add(...names) {
            const cur = node.className ? node.className.split(/\s+/) : [];
            names.forEach(n => { if (n && cur.indexOf(n) === -1) { cur.push(n); } });
            node.className = cur.join(' ');
        },
        remove(...names) {
            const cur = node.className ? node.className.split(/\s+/) : [];
            node.className = cur.filter(c => names.indexOf(c) === -1).join(' ');
        },
        contains(n) {
            return !!node.className && node.className.split(/\s+/).indexOf(n) !== -1;
        },
        toggle(n, on) { if (on) { this.add(n); } else { this.remove(n); } }
    };
}

function makeEl(tag) {
    const node = {
        tagName: String(tag).toUpperCase(),
        className: '', _text: '', tabIndex: 0,
        children: [], attrs: {}, style: {}, hidden: false,
        get textContent() { return this._text; },
        set textContent(v) { this._text = String(v); this.children = []; },
        get firstChild() { return this.children[0] || null; },
        appendChild(c) { this.children.push(c); c.parentNode = this; return c; },
        insertBefore(c, ref) {
            const i = this.children.indexOf(ref);
            if (i < 0) { this.children.push(c); } else { this.children.splice(i, 0, c); }
            c.parentNode = this;
            return c;
        },
        removeChild(c) { this.children = this.children.filter(x => x !== c); return c; },
        setAttribute(k, v) { this.attrs[k] = String(v); },
        getAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k) ? this.attrs[k] : null; },
        hasAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k); },
        removeAttribute(k) { delete this.attrs[k]; },
        addEventListener() {},
        removeEventListener() {},
        closest() { return null; },
        querySelectorAll() { return []; },
        querySelector() { return null; },
        focus() {}
    };
    node.classList = makeClassList(node);
    return node;
}

const rootEl = makeEl('div');
const win = {};
win.window = win;
win.location = { origin: 'http://localhost' };
win.parent = { postMessage() {} };
win.document = {
    readyState: 'complete',
    createElement: makeEl,
    createTextNode: t => ({ tagName: '#text', _text: String(t), children: [], classList: makeClassList({}) }),
    getElementById: id => (id === 'frg-preview-root' ? rootEl : null),
    addEventListener() {},
    body: makeEl('body')
};
win.addEventListener = () => {};

// ---------- 실제 파일 로드(수정본 그대로) ----------

const ctx = vm.createContext(win);
for (const f of ['slotMeta.js', 'previewRenderer.js']) {
    vm.runInContext(fs.readFileSync(path.join(jsDir, f), 'utf8'), ctx, { filename: f });
}

const def = JSON.parse(fs.readFileSync(defFile, 'utf8'));
win.JWorks_JSForgeAdminStudioPreview.renderDefinition(def, null, def.archetype, null);

// ---------- 골격 추출 ----------

function kindOf(node) {
    const cls = node.className || '';
    const has = n => cls.split(/\s+/).indexOf(n) !== -1;
    if (has('frg-fc-panel-body')) { return 'B'; }
    if (has('frg-fc-block') || has('frg-fc-item')) {
        return has('frg-fc-container') ? 'C' : 'I';
    }
    return null;
}

const lines = [];
(function walk(node, depth) {
    (node.children || []).forEach(child => {
        const kind = kindOf(child);
        if (kind) {
            lines.push('  '.repeat(depth) + kind);
            walk(child, depth + 1);
        } else {
            walk(child, depth); // 골격에 없는 장식 노드는 깊이를 늘리지 않는다
        }
    });
})(rootEl, 0);

process.stdout.write(lines.join('\n'));
