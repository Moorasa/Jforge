#${stem}-popup .layout-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
}
#${stem}-popup .popup-form {
    display: flex;
    flex-direction: column;
    gap: 12px;
}
#${stem}-popup .popup-field {
    display: flex;
    flex-direction: column;
    gap: 5px;
}
#${stem}-popup .popup-field input,
#${stem}-popup .popup-field select,
#${stem}-popup .popup-field textarea {
    width: 100%;
    box-sizing: border-box;
}
#${stem}-popup .required-mark { color: #dc2626; }
#${stem}-popup.popup-size-small .box { width: 380px; }
#${stem}-popup.popup-size-medium .box { width: 560px; }
#${stem}-popup.popup-size-large .box { width: 760px; }
