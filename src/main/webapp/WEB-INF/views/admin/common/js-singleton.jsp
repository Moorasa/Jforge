<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 서버주입 싱글톤: 전역 네임스페이스 앵커. 스크립트릿 없이 EL만 사용. --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<script>
    // J-FORGE 전역 싱글톤 (빌더가 타겟용으로 생성/관리하는 자리)
    window.MagicIAM_JSForge = window.MagicIAM_JSForge || {};
    window.MagicIAM_JSForge.contextPath = "${ctx}";
    window.MagicIAM_JSForge.appName = "J-FORGE";
</script>
