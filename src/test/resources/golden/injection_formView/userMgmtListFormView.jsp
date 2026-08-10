<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section id="form-view" class="form"
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
                <div class="form-field row-checkbox-scope" data-field-class="fld" data-name="n&quot;&gt;&lt;/section&gt;&lt;script&gt;alert(1)&lt;/script&gt;">
                    <label class="form-field-label" for="ff-n&quot;&gt;&lt;/section&gt;&lt;script&gt;alert(1)&lt;/script&gt;">&lt;/label&gt;&lt;script&gt;evil()&lt;/script&gt; &#36;{7*7} <span class="required-mark">*</span></label>
                    <input type="text" id="ff-n&quot;&gt;&lt;/section&gt;&lt;script&gt;alert(1)&lt;/script&gt;" name="n&quot;&gt;&lt;/section&gt;&lt;script&gt;alert(1)&lt;/script&gt;" class="form-field-input fld" required />
                </div>
                <div class="form-field row-checkbox-scope" data-field-class="ok-cls" data-name="l sep &lt;/script&gt;&lt;script&gt;bad()&lt;/script&gt; &#36;{9*9}">
                    <label class="form-field-label" for="ff-l sep &lt;/script&gt;&lt;script&gt;bad()&lt;/script&gt; &#36;{9*9}">p&quot; onerror=&quot;alert(1)</label>
                    <input type="text" id="ff-l sep &lt;/script&gt;&lt;script&gt;bad()&lt;/script&gt; &#36;{9*9}" name="l sep &lt;/script&gt;&lt;script&gt;bad()&lt;/script&gt; &#36;{9*9}" class="form-field-input ok-cls" />
                </div>
            </form>
            <div class="empty-case" style="display:none;"></div>
        </div>
    </div>
</section>
