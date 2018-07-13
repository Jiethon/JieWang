#############################
# App Name:DMClient
# Support ndk:None
#############################

LOCAL_PATH := $(my-dir)

############################
# app(DMClient.apk)
include $(CLEAR_VARS)

LOCAL_MODULE := DMClient
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := platform

# override package
LOCAL_OVERRIDES_PACKAGES := :null

# ODEX
LOCAL_DEX_PREOPT := $(call local_odex_status, $(LOCAL_MODULE))

include $(BUILD_PREBUILT)
############################

