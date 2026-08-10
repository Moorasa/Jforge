<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="form-view" class="form-compact"
    data-selection-type="checkbox">
    <div class="layout-column">
        <div class="layout-header">
            <div class="layout-left">
                <label class="select-all-label">
                    <input type="checkbox" id="select-all" class="select-all" />
                    <span>전체 선택</span>
                </label>
            </div>
        </div>
        <div class="layout-body">
            <form class="form-view-form" onsubmit="return false;">
                <div class="form-field row-checkbox-scope" data-field-class="fld-id" data-name="userId">
                    <label class="form-field-label" for="ff-userId">사용자 ID <span class="required-mark">*</span></label>
                    <input type="text" id="ff-userId" name="userId" class="form-field-input fld-id" required />
                </div>
                <div class="form-field row-checkbox-scope" data-name="email">
                    <label class="form-field-label" for="ff-email">이메일</label>
                    <input type="email" id="ff-email" name="email" class="form-field-input" />
                </div>
                <div class="form-field row-checkbox-scope" data-name="memo">
                    <label class="form-field-label" for="ff-memo">메모</label>
                    <textarea id="ff-memo" name="memo" class="form-field-input"></textarea>
                </div>
                <div class="form-field row-checkbox-scope" data-name="grade">
                    <label class="form-field-label" for="ff-grade">등급</label>
                    <select id="ff-grade" name="grade" class="form-field-input"></select>
                </div>
            </form>
            <div class="empty-case" style="display:none;"></div>
        </div>
    </div>
</section>
