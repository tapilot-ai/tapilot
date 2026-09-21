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
export { ANDROID_ACCESSIBILITY_PROTOCOL_VERSION, NativeBridgeError };
