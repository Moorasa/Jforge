<#assign inst = slots["popupBody"][0]>
<#assign props = inst.props>
<#assign confirmText = (props["confirmText"])!"확인">
<#assign cancelYn = (props["cancelYn"])!true>
<#assign sizeRaw = (props["size"])!"medium"><#assign sizeConst="MEDIUM">
<#if sizeRaw == "small"><#assign sizeConst="SMALL"><#elseif sizeRaw == "large"><#assign sizeConst="LARGE"></#if>
<#assign Domain = stem?cap_first>
window.MagicIAM_JS${Domain}Popup = window.MagicIAM_JS${Domain}Popup || {};
(function(popup) {
    "use strict";
    if (popup.__defined) { return; }
    popup.__defined = true;

    var $container = $("#${stem}-popup");

    popup.init = function() {
        MagicIAM_JSCommonPopup.init({
            $container: $container,
            type: PopupType.width.${sizeConst},
            footer: {
                type: PopupType.button.<#if cancelYn>OK_CANCEL<#else>OK</#if>,
                okText: "${jsString(confirmText)}",
                okCallback: function() {
                    // TODO: 도메인 저장 API를 연결한다.
                }<#if cancelYn>,
                cancelCallback: function() { }
                </#if>
            }
        });
    };

    $(popup.init);
})(window.MagicIAM_JS${Domain}Popup);
