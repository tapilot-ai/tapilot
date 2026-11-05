#include <napi/native_api.h>
#include <multimodalinput/oh_input_manager.h>
#include <hilog/log.h>
#include <cstdio>
static void Authorization(Input_InjectionStatus state) { OH_LOG_INFO(LOG_APP, "TapilotProbe callback=%{public}d", (int)state); }
static napi_value Request(napi_env env, napi_callback_info info) {
 int result = (int)OH_Input_RequestInjection(Authorization);
 char text[120]; snprintf(text, sizeof(text), "OH_Input_RequestInjection returned %d", result);
 OH_LOG_INFO(LOG_APP, "TapilotProbe %{public}s", text);
 napi_value value; napi_create_string_utf8(env,text,NAPI_AUTO_LENGTH,&value); return value;
}
static napi_value Init(napi_env env,napi_value exports) { napi_property_descriptor p={"requestInjection",nullptr,Request,nullptr,nullptr,nullptr,napi_default,nullptr}; napi_define_properties(env,exports,1,&p);return exports; }
static napi_module module={1,0,nullptr,Init,"probe",nullptr,{0}};
extern "C" __attribute__((constructor)) void Register() { napi_module_register(&module); }
