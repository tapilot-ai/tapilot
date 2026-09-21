"use strict";
var __webpack_require__ = {};
(()=>{
    __webpack_require__.d = (exports1, definition)=>{
        for(var key in definition)if (__webpack_require__.o(definition, key) && !__webpack_require__.o(exports1, key)) Object.defineProperty(exports1, key, {
            enumerable: true,
            get: definition[key]
        });
    };
})();
(()=>{
    __webpack_require__.o = (obj, prop)=>Object.prototype.hasOwnProperty.call(obj, prop);
})();
(()=>{
    __webpack_require__.r = (exports1)=>{
        if ("u" > typeof Symbol && Symbol.toStringTag) Object.defineProperty(exports1, Symbol.toStringTag, {
            value: 'Module'
        });
        Object.defineProperty(exports1, '__esModule', {
            value: true
        });
    };
})();
var __webpack_exports__ = {};
__webpack_require__.r(__webpack_exports__);
__webpack_require__.d(__webpack_exports__, {
    ANDROID_ACCESSIBILITY_PROTOCOL_VERSION: ()=>ANDROID_ACCESSIBILITY_PROTOCOL_VERSION,
    NativeBridgeError: ()=>NativeBridgeError
});
function _define_property(obj, key, value) {
    if (key in obj) Object.defineProperty(obj, key, {
        value: value,
        enumerable: true,
        configurable: true,
        writable: true
    });
    else obj[key] = value;
    return obj;
}
const ANDROID_ACCESSIBILITY_PROTOCOL_VERSION = 1;
class NativeBridgeError extends Error {
    constructor(code, message, retryable = false){
        super(message), _define_property(this, "code", void 0), _define_property(this, "retryable", void 0), this.code = code, this.retryable = retryable;
        this.name = 'NativeBridgeError';
    }
}
exports.ANDROID_ACCESSIBILITY_PROTOCOL_VERSION = __webpack_exports__.ANDROID_ACCESSIBILITY_PROTOCOL_VERSION;
exports.NativeBridgeError = __webpack_exports__.NativeBridgeError;
for(var __rspack_i in __webpack_exports__)if (-1 === [
    "ANDROID_ACCESSIBILITY_PROTOCOL_VERSION",
    "NativeBridgeError"
].indexOf(__rspack_i)) exports[__rspack_i] = __webpack_exports__[__rspack_i];
Object.defineProperty(exports, '__esModule', {
    value: true
});
