<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="userMgmt-detail" class="detail-area">

    <section class="detail-toolbar">
        <button type="button" class="btn btn-primary" data-action="approve">승인</button>
    </section>

    <section id="basic-info" class="view-mode basic-compact">
        <div class="detail-header">
            <button type="button" class="button-detail-collapse" aria-label="상세 접기"></button>
            <button type="button" class="button-detail-expand" aria-label="상세 펼치기"></button>
        </div>
        <div class="detail-info-view">
            <div class="layout-column">
                <div class="detail-field" data-name="userId">
                    <span class="label">사용자 ID</span>
                    <span class="value">-</span>
                </div>
                <div class="detail-field" data-name="memo">
                    <span class="label">메모</span>
                    <span class="value">-</span>
                </div>
            </div>
            <div class="attribute-area">
                <span class="label">속성</span>
                <div class="attribute-chip-container"><span>-</span></div>
            </div>
        </div>
        <div class="detail-info-edit">
            <div class="layout-column">
                <div class="edit-field" data-name="userId">
                    <label class="clearable" for="bi-userId">사용자 ID <span class="required-mark">*</span></label>
                    <input type="text" id="bi-userId" name="userId" class="detail-input fld-id" required />
                </div>
                <div class="edit-field" data-name="memo">
                    <label class="clearable" for="bi-memo">메모</label>
                    <textarea id="bi-memo" name="memo" class="detail-input"></textarea>
                </div>
            </div>
        </div>
        <div class="buttons">
            <button type="button" class="update">수정</button>
            <button type="button" class="delete">삭제</button>
        </div>
        <div class="buttons-edit">
            <button type="button" class="save">저장</button>
            <button type="button" class="cancel">취소</button>
        </div>
    </section>

    <section id="associate-info" class="associate-info with-tab">
        <div class="tabs">
            <div class="tab tab-role on">권한</div>
            <div class="tab tab-history">이력</div>
        </div>
        <div class="contents">
            <iframe title="권한" id="roleFrame" class="associate-frame tab-role on"></iframe>
            <iframe title="이력" id="historyFrame" class="associate-frame tab-history"></iframe>
        </div>
    </section>

</section>
