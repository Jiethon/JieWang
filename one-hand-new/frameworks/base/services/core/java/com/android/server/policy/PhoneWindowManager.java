/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.policy;

import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FREEFORM_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.HOME_STACK_ID;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.content.pm.PackageManager.FEATURE_TELEVISION;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.content.res.Configuration.EMPTY;
import static android.content.res.Configuration.UI_MODE_TYPE_CAR;
import static android.content.res.Configuration.UI_MODE_TYPE_MASK;
import static android.view.WindowManager.DOCKED_TOP;
import static android.view.WindowManager.DOCKED_LEFT;
import static android.view.WindowManager.DOCKED_RIGHT;
import static android.view.WindowManager.TAKE_SCREENSHOT_FULLSCREEN;
import static android.view.WindowManager.TAKE_SCREENSHOT_SELECTED_REGION;
import static android.view.WindowManager.LayoutParams.*;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_COVERED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_COVER_ABSENT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_UNCOVERED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_ABSENT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_CLOSED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_OPEN;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.StackId;
import android.app.ActivityManagerInternal;
import android.app.ActivityManagerInternal.SleepToken;
import android.app.ActivityManagerNative;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.IUiModeManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManagerInternal;
// Add by gaohui@wind-mobi.com 20161107 start to disable home key when enrolling
import android.hardware.fingerprint.FingerprintManager;
// Add by gaohui@wind-mobi.com 20161107 start to disable home key when enrolling
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.hardware.hdmi.HdmiPlaybackClient.OneTouchPlayCallback;
import android.hardware.input.InputManager;
import android.hardware.input.InputManagerInternal;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.IAudioService;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaSessionLegacyHelper;
import android.os.AlwaysOnControllerInternal;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.InadvertentTouchControllerInternal;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.os.Vibrator;
import android.os.SystemMonitorInternal;
import android.provider.MediaStore;
import android.provider.Settings;
import android.service.dreams.DreamManagerInternal;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.service.vr.IVrManager;
import com.android.server.vr.VrManagerService;
import android.speech.RecognizerIntent;
/*wangchaobin@wind-mobi.com added  2017.01.10 for new feature begin*/
import android.telecom.Connection;
/*wangchaobin@wind-mobi.com added  2017.01.10 for new feature end*/
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.MutableBoolean;
import android.util.Slog;
import android.util.SparseArray;
import android.util.LongSparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.IApplicationToken;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.KeyCharacterMap;
import android.view.KeyCharacterMap.FallbackAction;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerInternal;
import android.view.WindowManagerPolicy;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import com.android.internal.policy.EmergencyAffordanceManager;
import com.android.internal.R;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.policy.PhoneWindow;
import com.android.internal.policy.IShortcutService;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.ScreenShapeHelper;
import com.android.internal.widget.PointerLocationView;
import com.android.server.GestureLauncherService;
import com.android.server.LocalServices;
import com.android.server.policy.keyguard.KeyguardServiceDelegate;
import com.android.server.policy.keyguard.KeyguardServiceDelegate.DrawnListener;
import com.android.server.statusbar.StatusBarManagerInternal;
import com.android.server.policy.flipcover2.FlipCover2ServiceDelegate;

// chih-hsuan add for virtual key light behavior +++
import com.android.server.VirtualKeyBackLightService;
// chih-hsuan add for virtual key light behavior ---
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/// M: cenxingcan@wind-mobi.com add import for otg reverse 20161119 begin
import android.app.AlertDialog;
import android.os.BatteryManager;
import android.content.DialogInterface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
/// M: cenxingcan@wind-mobi.com add import for otg reverse 20161119 end
//dongjiangpeng@wind-mobi.com add 2016/12/21 start
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
//dongjiangpeng@wind-mobi.com add 2016/12/21 end
import java.io.BufferedReader;
/// M: Add import.
import android.hardware.input.InputManager;
import android.content.res.TypedArray;

import android.view.CombineKeyDetector;
import android.app.IActivityManager;
import android.widget.Toast;

//lifeifei@wind-mobi.com add 2018/01/09 begin
import com.mediatek.perfservice.IPerfServiceWrapper;
import com.mediatek.perfservice.PerfServiceWrapper;
//lifeifei@wind-mobi.com add 2018/01/09 end

import static android.provider.Settings.System.GESTURE_TYPE1_APP;
import static android.provider.Settings.System.GESTURE_TYPE2_APP;
import static android.provider.Settings.System.GESTURE_TYPE3_APP;
import static android.provider.Settings.System.GESTURE_TYPE4_APP;
import static android.provider.Settings.System.GESTURE_TYPE5_APP;
import static android.provider.Settings.System.GESTURE_TYPE6_APP;
/**
 * WindowManagerPolicy implementation for the Android phone UI.  This
 * introduces a new method suffix, Lp, for an internal lock of the
 * PhoneWindowManager.  This is used to protect some internal state, and
 * can be acquired with either the Lw and Li lock held, so has the restrictions
 * of both of those when held.
 */
public class PhoneWindowManager implements WindowManagerPolicy {
    static final String TAG = "WindowManager";
    /// M: runtime switch debug flags @{
    static boolean DEBUG = false;
    static boolean localLOGV = false;
    static boolean DEBUG_INPUT = false;
    static boolean DEBUG_KEYGUARD = false;
    static boolean DEBUG_LAYOUT = false;
    static boolean DEBUG_STARTING_WINDOW = false;
    static boolean DEBUG_WAKEUP = false;
    static boolean DEBUG_ORIENTATION = false;
    /// @}
    static final boolean SHOW_STARTING_ANIMATIONS = true;
    static final boolean SHOW_PROCESSES_ON_ALT_MENU = false;

    // Whether to allow dock apps with METADATA_DOCK_HOME to temporarily take over the Home key.
    // No longer recommended for desk docks;
    static final boolean ENABLE_DESK_DOCK_HOME_CAPTURE = false;

    static final int SHORT_PRESS_POWER_NOTHING = 0;
    static final int SHORT_PRESS_POWER_GO_TO_SLEEP = 1;
    static final int SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP = 2;
    static final int SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP_AND_GO_HOME = 3;
    static final int SHORT_PRESS_POWER_GO_HOME = 4;

    static final int LONG_PRESS_POWER_NOTHING = 0;
    static final int LONG_PRESS_POWER_GLOBAL_ACTIONS = 1;
    static final int LONG_PRESS_POWER_SHUT_OFF = 2;
    static final int LONG_PRESS_POWER_SHUT_OFF_NO_CONFIRM = 3;

    static final int LONG_PRESS_BACK_NOTHING = 0;
    static final int LONG_PRESS_BACK_GO_TO_VOICE_ASSIST = 1;

    static final int MULTI_PRESS_POWER_NOTHING = 0;
    static final int MULTI_PRESS_POWER_THEATER_MODE = 1;
    static final int MULTI_PRESS_POWER_BRIGHTNESS_BOOST = 2;

    // These need to match the documentation/constant in
    // core/res/res/values/config.xml
    static final int LONG_PRESS_HOME_NOTHING = 0;
    static final int LONG_PRESS_HOME_RECENT_SYSTEM_UI = 1;
    static final int LONG_PRESS_HOME_ASSIST = 2;
    static final int LAST_LONG_PRESS_HOME_BEHAVIOR = LONG_PRESS_HOME_ASSIST;

    static final int DOUBLE_TAP_HOME_NOTHING = 0;
    static final int DOUBLE_TAP_HOME_RECENT_SYSTEM_UI = 1;

    static final int SHORT_PRESS_WINDOW_NOTHING = 0;
    static final int SHORT_PRESS_WINDOW_PICTURE_IN_PICTURE = 1;
    // BEGIN leo_liao@asus.com, One-hand control
    static final int DOUBLE_TAP_HOME_ONEHAND_CTRL = 2;
    // END leo_liao@asus.com

    //BEGIN : roy_huang@asus.com
    static final int DOUBLE_TAP_HOME_TARGET_APP = 3;
    //END : roy_huang@asus.com
    
    static final int SHORT_PRESS_SLEEP_GO_TO_SLEEP = 0;
    static final int SHORT_PRESS_SLEEP_GO_TO_SLEEP_AND_GO_HOME = 1;

    // Controls navigation bar opacity depending on which workspace stacks are currently
    // visible.
    // Nav bar is always opaque when either the freeform stack or docked stack is visible.
    static final int NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED = 0;
    // Nav bar is always translucent when the freeform stack is visible, otherwise always opaque.
    static final int NAV_BAR_TRANSLUCENT_WHEN_FREEFORM_OPAQUE_OTHERWISE = 1;

    static final int APPLICATION_MEDIA_SUBLAYER = -2;
    static final int APPLICATION_MEDIA_OVERLAY_SUBLAYER = -1;
    static final int APPLICATION_PANEL_SUBLAYER = 1;
    static final int APPLICATION_SUB_PANEL_SUBLAYER = 2;
    static final int APPLICATION_ABOVE_SUB_PANEL_SUBLAYER = 3;

    static public final String SYSTEM_DIALOG_REASON_KEY = "reason";
    static public final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";
    static public final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    static public final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
    static public final String SYSTEM_DIALOG_REASON_ASSIST = "assist";

    //add mohongwu@wind-mobi.com 2016/11/23 start
    public static boolean mShouldTurnOffTouch = false;
    //add mohongwu@wind-mobi.com 2016/11/23 end
    /*wangchaobin@wind-mobi.com added amend 2016.12.19 for bug 147973 begin*/
    public static boolean isProximmitySensorPositive = false;
    /*wangchaobin@wind-mobi.com added amend 2017.01.10 for new feature begin*/
    public static boolean isAsusPowerDown = false;
    public boolean isListenerRegisted = false;
    public final static String ASUS_HUNGUP_BROADCAST = "com.asus.hungupcall";
    /*wangchaobin@wind-mobi.com added amend 2017.01.10 for new feature end*/
    /*wangchaobin@wind-mobi.com added amend 2016.12.19 for bug 147973 end*/
	//dongjiangpeng@wind-mobi.com add 2016/12/21 start
    private static final  int MSG_GESTURE_LISTEN = 5555;
    //dongjiangpeng@wind-mobi.com add 2016/12/21 end

    //lifeifei@wind-mobi.com add 2018/01/09 begin
    IPerfServiceWrapper mPerfService = null;
    int mPerfServiceHandle_base_cpu = -1;
    //lifeifei@wind-mobi.com add 2018/01/09 end


    /**
     * These are the system UI flags that, when changing, can cause the layout
     * of the screen to change.
     */
    static final int SYSTEM_UI_CHANGING_LAYOUT =
              View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.STATUS_BAR_TRANSLUCENT
            | View.NAVIGATION_BAR_TRANSLUCENT
            | View.STATUS_BAR_TRANSPARENT
            | View.NAVIGATION_BAR_TRANSPARENT;

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();

    // The panic gesture may become active only after the keyguard is dismissed and the immersive
    // app shows again. If that doesn't happen for 30s we drop the gesture.
    private static final long PANIC_GESTURE_EXPIRATION = 30000;

    private static final String SYSUI_PACKAGE = "com.android.systemui";
    private static final String SYSUI_SCREENSHOT_SERVICE =
            "com.android.systemui.screenshot.TakeScreenshotService";
    private static final String SYSUI_SCREENSHOT_ERROR_RECEIVER =
            "com.android.systemui.screenshot.ScreenshotServiceErrorReceiver";

    private static final int NAV_BAR_BOTTOM = 0;
    private static final int NAV_BAR_RIGHT = 1;
    private static final int NAV_BAR_LEFT = 2;

    //BEGIN : roy_huang@asus.com, Long screenshot feature in CN sku
    private static final String CNSMARTSCREENSHOT_PACKAGE = "com.asus.cnsmartscreenshot";
    private static final String CNSMARTSCREENSHOT_SCREENSHOT_SERVICE =
            "com.asus.cnsmartscreenshot.screenshot.TakeScreenshotService";
    private static final String CNSMARTSCREENSHOT_SCREENSHOT_ERROR_RECEIVER =
            "com.asus.cnsmartscreenshot.screenshot.ScreenshotServiceErrorReceiver";
    //END : roy_huang@asus.com
    /**
     * Keyguard stuff
     */
    private WindowState mKeyguardScrim;
    private boolean mKeyguardHidden;
    private boolean mKeyguardDrawnOnce;
	
	// Add by gaohui@wind-mobi.com 20161107 start to disable home key when enrolling
    private boolean mFingerprintOn = false;
    // Add by gaohui@wind-mobi.com 20161107 end to disable home key when enrolling

    /* Table of Application Launch keys.  Maps from key codes to intent categories.
     *
     * These are special keys that are used to launch particular kinds of applications,
     * such as a web browser.  HID defines nearly a hundred of them in the Consumer (0x0C)
     * usage page.  We don't support quite that many yet...
     */
    static SparseArray<String> sApplicationLaunchKeyCategories;
    static {
        sApplicationLaunchKeyCategories = new SparseArray<String>();
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_EXPLORER, Intent.CATEGORY_APP_BROWSER);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_ENVELOPE, Intent.CATEGORY_APP_EMAIL);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_CONTACTS, Intent.CATEGORY_APP_CONTACTS);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_CALENDAR, Intent.CATEGORY_APP_CALENDAR);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_MUSIC, Intent.CATEGORY_APP_MUSIC);
        sApplicationLaunchKeyCategories.append(
                KeyEvent.KEYCODE_CALCULATOR, Intent.CATEGORY_APP_CALCULATOR);
    }

    /** Amount of time (in milliseconds) to wait for windows drawn before powering on. */
    static final int WAITING_FOR_DRAWN_TIMEOUT = 1000;

    /**
     * Lock protecting internal state.  Must not call out into window
     * manager with lock held.  (This lock will be acquired in places
     * where the window manager is calling in with its own lock held.)
     */
    private final Object mLock = new Object();

    Context mContext;
    IWindowManager mWindowManager;
    WindowManagerFuncs mWindowManagerFuncs;
    WindowManagerInternal mWindowManagerInternal;
    PowerManager mPowerManager;
    ActivityManagerInternal mActivityManagerInternal;
    InputManagerInternal mInputManagerInternal;
    DreamManagerInternal mDreamManagerInternal;
    PowerManagerInternal mPowerManagerInternal;
    IStatusBarService mStatusBarService;
    StatusBarManagerInternal mStatusBarManagerInternal;
    boolean mPreloadedRecentApps;
    final Object mServiceAquireLock = new Object();
    Vibrator mVibrator; // Vibrator for giving feedback of orientation changes
    SearchManager mSearchManager;
    AccessibilityManager mAccessibilityManager;
    BurnInProtectionHelper mBurnInProtectionHelper;
    AppOpsManager mAppOpsManager;
    private boolean mHasFeatureWatch;

    // Vibrator pattern for haptic feedback of a long press.
    long[] mLongPressVibePattern;

    // Vibrator pattern for haptic feedback of virtual key press.
    long[] mVirtualKeyVibePattern;

    // Vibrator pattern for a short vibration.
    long[] mKeyboardTapVibePattern;

    // Vibrator pattern for a short vibration when tapping on an hour/minute tick of a Clock.
    long[] mClockTickVibePattern;

    // Vibrator pattern for a short vibration when tapping on a day/month/year date of a Calendar.
    long[] mCalendarDateVibePattern;

    // Vibrator pattern for haptic feedback during boot when safe mode is disabled.
    long[] mSafeModeDisabledVibePattern;

    // Vibrator pattern for haptic feedback during boot when safe mode is enabled.
    long[] mSafeModeEnabledVibePattern;

    // Vibrator pattern for haptic feedback of a context click.
    long[] mContextClickVibePattern;

    /** If true, hitting shift & menu will broadcast Intent.ACTION_BUG_REPORT */
    boolean mEnableShiftMenuBugReports = false;

    boolean mSafeMode;
    WindowState mStatusBar = null;
    int mStatusBarHeight;
    WindowState mNavigationBar = null;
    boolean mHasNavigationBar = false;
    boolean mNavigationBarCanMove = false; // can the navigation bar ever move to the side?
    int mNavigationBarPosition = NAV_BAR_BOTTOM;
    int[] mNavigationBarHeightForRotationDefault = new int[4];
    int[] mNavigationBarWidthForRotationDefault = new int[4];
    int[] mNavigationBarHeightForRotationInCarMode = new int[4];
    int[] mNavigationBarWidthForRotationInCarMode = new int[4];

    private LongSparseArray<IShortcutService> mShortcutKeyServices = new LongSparseArray<>();

    // Whether to allow dock apps with METADATA_DOCK_HOME to temporarily take over the Home key.
    // This is for car dock and this is updated from resource.
    private boolean mEnableCarDockHomeCapture = true;

    boolean mBootMessageNeedsHiding;
    KeyguardServiceDelegate mKeyguardDelegate;
    final Runnable mWindowManagerDrawCallback = new Runnable() {
        @Override
        public void run() {
            if (DEBUG_WAKEUP) Slog.i(TAG, "All windows ready for display!");
            mHandler.sendEmptyMessage(MSG_WINDOW_MANAGER_DRAWN_COMPLETE);
        }
    };
    final DrawnListener mKeyguardDrawnCallback = new DrawnListener() {
        @Override
        public void onDrawn() {
            if (DEBUG_WAKEUP) Slog.d(TAG, "mKeyguardDelegate.ShowListener.onDrawn.");
            mHandler.sendEmptyMessage(MSG_KEYGUARD_DRAWN_COMPLETE);
        }
    };

    GlobalActions mGlobalActions;
    Handler mHandler;
    WindowState mLastInputMethodWindow = null;
    WindowState mLastInputMethodTargetWindow = null;

    // FIXME This state is shared between the input reader and handler thread.
    // Technically it's broken and buggy but it has been like this for many years
    // and we have not yet seen any problems.  Someday we'll rewrite this logic
    // so that only one thread is involved in handling input policy.  Unfortunately
    // it's on a critical path for power management so we can't just post the work to the
    // handler thread.  We'll need to resolve this someday by teaching the input dispatcher
    // to hold wakelocks during dispatch and eliminating the critical path.
    volatile boolean mPowerKeyHandled;
    volatile boolean mBackKeyHandled;
    volatile boolean mBeganFromNonInteractive;
    volatile int mPowerKeyPressCounter;
    volatile boolean mEndCallKeyHandled;
    volatile boolean mCameraGestureTriggeredDuringGoingToSleep;
    volatile boolean mGoingToSleep;
    volatile boolean mRecentsVisible;
    volatile boolean mTvPictureInPictureVisible;

    int mRecentAppsHeldModifiers;
    boolean mLanguageSwitchKeyPressed;

    int mLidState = LID_ABSENT;
    int mCameraLensCoverState = CAMERA_LENS_COVER_ABSENT;
    boolean mHaveBuiltInKeyboard;

    //+++
    boolean mHasDockFeature;
    boolean mHasKeyboardFeature;
    boolean mHasHallSensorFeature;
    //---
    // +++ [TT-346735]
    boolean mRequireKeyguardDoneWhenScreenOn = false;
    // ---
    boolean mHasTranscoverFeature;  //+++ yuchen_chang: porting to M

    boolean mSystemReady;
    boolean mSystemBooted;
    private boolean mDeferBindKeyguard;
    boolean mHdmiPlugged;
    HdmiControl mHdmiControl;
    IUiModeManager mUiModeManager;
    int mUiMode;
    int mDockMode = Intent.EXTRA_DOCK_STATE_UNDOCKED;
    int mLidOpenRotation;
    int mCarDockRotation;
    int mDeskDockRotation;
    int mUndockedHdmiRotation;
    int mDemoHdmiRotation;
    boolean mDemoHdmiRotationLock;
    int mDemoRotation;
    boolean mDemoRotationLock;
    int mAsusDockRotation = Surface.ROTATION_0;

    boolean mWakeGestureEnabledSetting;
    MyWakeGestureListener mWakeGestureListener;

    // Default display does not rotate, apps that require non-default orientation will have to
    // have the orientation emulated.
    private boolean mForceDefaultOrientation = false;

    int mUserRotationMode = WindowManagerPolicy.USER_ROTATION_FREE;
    int mUserRotation = Surface.ROTATION_0;
    boolean mAccelerometerDefault;

    boolean mSupportAutoRotation;
    int mAllowAllRotations = -1;
    boolean mCarDockEnablesAccelerometer;
    boolean mDeskDockEnablesAccelerometer;
    int mLidKeyboardAccessibility;
    int mLidNavigationAccessibility;
    boolean mLidControlsScreenLock;
    boolean mLidControlsSleep;
    int mShortPressOnPowerBehavior;
    int mLongPressOnPowerBehavior;
    int mDoublePressOnPowerBehavior;
    int mTriplePressOnPowerBehavior;
    int mLongPressOnBackBehavior;
    int mShortPressOnSleepBehavior;
    int mShortPressWindowBehavior;
    boolean mAwake;
    boolean mScreenOnEarly;
    boolean mScreenOnFully;
    ScreenOnListener mScreenOnListener;
    boolean mKeyguardDrawComplete;
    boolean mWindowManagerDrawComplete;
    boolean mOrientationSensorEnabled = false;
    int mCurrentAppOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    boolean mHasSoftInput = false;
    boolean mTranslucentDecorEnabled = true;
    boolean mUseTvRouting;

    int mPointerLocationMode = 0; // guarded by mLock


    // The last window we were told about in focusChanged.
    WindowState mFocusedWindow;
    IApplicationToken mFocusedApp;

    PointerLocationView mPointerLocationView;

    // The current size of the screen; really; extends into the overscan area of
    // the screen and doesn't account for any system elements like the status bar.
    int mOverscanScreenLeft, mOverscanScreenTop;
    int mOverscanScreenWidth, mOverscanScreenHeight;
    // The current visible size of the screen; really; (ir)regardless of whether the status
    // bar can be hidden but not extending into the overscan area.
    int mUnrestrictedScreenLeft, mUnrestrictedScreenTop;
    int mUnrestrictedScreenWidth, mUnrestrictedScreenHeight;
    // Like mOverscanScreen*, but allowed to move into the overscan region where appropriate.
    int mRestrictedOverscanScreenLeft, mRestrictedOverscanScreenTop;
    int mRestrictedOverscanScreenWidth, mRestrictedOverscanScreenHeight;
    // The current size of the screen; these may be different than (0,0)-(dw,dh)
    // if the status bar can't be hidden; in that case it effectively carves out
    // that area of the display from all other windows.
    int mRestrictedScreenLeft, mRestrictedScreenTop;
    int mRestrictedScreenWidth, mRestrictedScreenHeight;
    // During layout, the current screen borders accounting for any currently
    // visible system UI elements.
    int mSystemLeft, mSystemTop, mSystemRight, mSystemBottom;
    // For applications requesting stable content insets, these are them.
    int mStableLeft, mStableTop, mStableRight, mStableBottom;
    // For applications requesting stable content insets but have also set the
    // fullscreen window flag, these are the stable dimensions without the status bar.
    int mStableFullscreenLeft, mStableFullscreenTop;
    int mStableFullscreenRight, mStableFullscreenBottom;
    // During layout, the current screen borders with all outer decoration
    // (status bar, input method dock) accounted for.
    int mCurLeft, mCurTop, mCurRight, mCurBottom;
    // During layout, the frame in which content should be displayed
    // to the user, accounting for all screen decoration except for any
    // space they deem as available for other content.  This is usually
    // the same as mCur*, but may be larger if the screen decor has supplied
    // content insets.
    int mContentLeft, mContentTop, mContentRight, mContentBottom;
    // During layout, the frame in which voice content should be displayed
    // to the user, accounting for all screen decoration except for any
    // space they deem as available for other content.
    int mVoiceContentLeft, mVoiceContentTop, mVoiceContentRight, mVoiceContentBottom;
    // During layout, the current screen borders along which input method
    // windows are placed.
    int mDockLeft, mDockTop, mDockRight, mDockBottom;
    // During layout, the layer at which the doc window is placed.
    int mDockLayer;
    // During layout, this is the layer of the status bar.
    int mStatusBarLayer;
    int mLastSystemUiFlags;
    // Bits that we are in the process of clearing, so we want to prevent
    // them from being set by applications until everything has been updated
    // to have them clear.
    int mResettingSystemUiFlags = 0;
    // Bits that we are currently always keeping cleared.
    int mForceClearedSystemUiFlags = 0;
    int mLastFullscreenStackSysUiFlags;
    int mLastDockedStackSysUiFlags;
    final Rect mNonDockedStackBounds = new Rect();
    final Rect mDockedStackBounds = new Rect();
    final Rect mLastNonDockedStackBounds = new Rect();
    final Rect mLastDockedStackBounds = new Rect();

    // What we last reported to system UI about whether the compatibility
    // menu needs to be displayed.
    boolean mLastFocusNeedsMenu = false;
    // If nonzero, a panic gesture was performed at that time in uptime millis and is still pending.
    private long mPendingPanicGestureUptime;

    InputConsumer mInputConsumer = null;

    // BEGIN: archie_huang@asus.com
    // For feature: Navigation visibility control
    InputConsumer mGestureInputConsumer = null;
    // END: archie_huang@asus.com

    static final Rect mTmpParentFrame = new Rect();
    static final Rect mTmpDisplayFrame = new Rect();
    static final Rect mTmpOverscanFrame = new Rect();
    static final Rect mTmpContentFrame = new Rect();
    static final Rect mTmpVisibleFrame = new Rect();
    static final Rect mTmpDecorFrame = new Rect();
    static final Rect mTmpStableFrame = new Rect();
    static final Rect mTmpNavigationFrame = new Rect();
    static final Rect mTmpOutsetFrame = new Rect();
    private static final Rect mTmpRect = new Rect();

    WindowState mTopFullscreenOpaqueWindowState;
    WindowState mTopFullscreenOpaqueOrDimmingWindowState;
    WindowState mTopDockedOpaqueWindowState;
    WindowState mTopDockedOpaqueOrDimmingWindowState;
    HashSet<IApplicationToken> mAppsToBeHidden = new HashSet<IApplicationToken>();
    HashSet<IApplicationToken> mAppsThatDismissKeyguard = new HashSet<IApplicationToken>();
    boolean mTopIsFullscreen;
    boolean mForceStatusBar;
    boolean mForceStatusBarFromKeyguard;
    private boolean mForceStatusBarTransparent;
    int mNavBarOpacityMode = NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED;
    boolean mHideLockScreen;
    boolean mForcingShowNavBar;
    int mForcingShowNavBarLayer;

    // States of keyguard dismiss.
    private static final int DISMISS_KEYGUARD_NONE = 0; // Keyguard not being dismissed.
    private static final int DISMISS_KEYGUARD_START = 1; // Keyguard needs to be dismissed.
    private static final int DISMISS_KEYGUARD_CONTINUE = 2; // Keyguard has been dismissed.
    int mDismissKeyguard = DISMISS_KEYGUARD_NONE;

    /** The window that is currently dismissing the keyguard. Dismissing the keyguard must only
     * be done once per window. */
    private WindowState mWinDismissingKeyguard;

    /** When window is currently dismissing the keyguard, dismissing the keyguard must handle
     * the keygaurd secure state change instantly case, e.g. the use case of inserting a PIN
     * lock SIM card. This variable is used to record the previous keyguard secure state for
     * monitoring secure state change on window dismissing keyguard. */
    private boolean mSecureDismissingKeyguard;

    /** The window that is currently showing "over" the keyguard. If there is an app window
     * belonging to another app on top of this the keyguard shows. If there is a fullscreen
     * app window under this, still dismiss the keyguard but don't show the app underneath. Show
     * the wallpaper. */
    private WindowState mWinShowWhenLocked;

    boolean mShowingLockscreen;
    boolean mShowingDream;
    boolean mDreamingLockscreen;
    boolean mDreamingSleepTokenNeeded;
    SleepToken mDreamingSleepToken;
    SleepToken mScreenOffSleepToken;
    boolean mKeyguardSecure;
    boolean mKeyguardSecureIncludingHidden;
    volatile boolean mKeyguardOccluded;
    boolean mHomePressed;
    boolean mHomeConsumed;
    boolean mHomeDoubleTapPending;
    Intent mHomeIntent;
    Intent mCarDockIntent;
    Intent mDeskDockIntent;
    boolean mSearchKeyShortcutPending;
    boolean mConsumeSearchKeyUp;
    boolean mAssistKeyLongPressed;
    boolean mPendingMetaAction;
    boolean mPendingCapsLockToggle;
    int mMetaState;
    int mInitialMetaState;
    boolean mForceShowSystemBars;

    // support for activating the lock screen while the screen is on
    boolean mAllowLockscreenWhenOn;
    int mLockScreenTimeout;
    boolean mLockScreenTimerActive;

    // Behavior of ENDCALL Button.  (See Settings.System.END_BUTTON_BEHAVIOR.)
    int mEndcallBehavior;

    // Behavior of POWER button while in-call and screen on.
    // (See Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR.)
    int mIncallPowerBehavior;

    Display mDisplay;

    private int mDisplayRotation;

    int mLandscapeRotation = 0;  // default landscape rotation
    int mSeascapeRotation = 0;   // "other" landscape rotation, 180 degrees from mLandscapeRotation
    int mPortraitRotation = 0;   // default portrait rotation
    int mUpsideDownRotation = 0; // "other" portrait rotation

    int mOverscanLeft = 0;
    int mOverscanTop = 0;
    int mOverscanRight = 0;
    int mOverscanBottom = 0;
    boolean mAsusDockEnablesAccelerometer = false;

    // What we do when the user long presses on home
    private int mLongPressOnHomeBehavior;

    // What we do when the user double-taps on home
    private int mDoubleTapOnHomeBehavior;

    // Allowed theater mode wake actions
    private boolean mAllowTheaterModeWakeFromKey;
    private boolean mAllowTheaterModeWakeFromPowerKey;
    private boolean mAllowTheaterModeWakeFromMotion;
    private boolean mAllowTheaterModeWakeFromMotionWhenNotDreaming;
    private boolean mAllowTheaterModeWakeFromCameraLens;
    private boolean mAllowTheaterModeWakeFromLidSwitch;
    private boolean mAllowTheaterModeWakeFromWakeGesture;

    // Whether to support long press from power button in non-interactive mode
    private boolean mSupportLongPressPowerWhenNonInteractive;

    // Whether to go to sleep entering theater mode from power button
    private boolean mGoToSleepOnButtonPressTheaterMode;

    // BEGIN leo_liao@asus.com, One-hand control
    private boolean mOneHandCtrlFeatureEnabled = false;
    private int mOneHandCtrlQuickTriggerByDefault = 0;
    // END leo_liao@asus.com

    //dongjiangpeng@wind-mobi.com add 2016/12/21 start
    private SensorManager mSensorManager;
    //dongjiangpeng@wind-mobi.com add 2016/12/21 end

    //++add by gaohui@wind-mobi.com for usb_ntc begin
    private NotificationManager mNotificationManager;
    //--

    // Screenshot trigger states
    // Time to volume and power must be pressed within this interval of each other.
    private static final long SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS = 150;
    // Increase the chord delay when taking a screenshot from the keyguard
    private static final float KEYGUARD_SCREENSHOT_CHORD_DELAY_MULTIPLIER = 2.5f;
    private boolean mScreenshotChordEnabled;
    private boolean mScreenshotChordVolumeDownKeyTriggered;
    private long mScreenshotChordVolumeDownKeyTime;
    private boolean mScreenshotChordVolumeDownKeyConsumed;
    private boolean mScreenshotChordVolumeUpKeyTriggered;
    private boolean mScreenshotChordPowerKeyTriggered;
    private long mScreenshotChordPowerKeyTime;

    private boolean mIsInstantCameraEnabled = false;
    private boolean mVolumeUpDoubleClickPending = false;
    private boolean mVolumeDownDoubleClickPending = false;
    private boolean mIsLongPressInstantCameraEnabled = false;
    private boolean mSupportShutterOrRecordKeyDevice = false;
    private boolean mLockRecentKeyEnabled = false;
    private boolean mIsLockPhysicalKey = false;
    private Object mLockPhysicalKey = new Object();
    private LongPressLaunchCamera mLongPressLaunchCameraRunnable = new LongPressLaunchCamera();
    private CombineKeyDetector mCombineKeyDetector;
    private CombineKeyDetector.OnCombineKeyListener mCombineKeyListener = new CombineKeyListener();
    private SystemMonitorInternal mSystemMonitorInternal;
    private boolean mIsKeyguardShow = false;
    volatile boolean mSwitchKeyHandled;
    private int mFuncWhenLongPressAppSwitch = Settings.System.LONG_PRESSED_FUNC_DEFAULT;
    private NotifyCameraRunnable mNotifyCameraRunnable = new NotifyCameraRunnable();

    //For long-pressing Recent key to launch stitchimage
    private static final String STITCHIMAGE_APP_PACKAGE_NAME = "com.asus.stitchimage";
    private static final String STITCHIMAGE_SERVICE_PACKAGE_NAME = "com.asus.stitchimage.service";
    private static final String ACTION_START_STITCHIMAGE = "com.asus.stitchimage.OverlayService";
    private static final String EXTRA_KEY_STITCHIMAGE_SETTINGS_CALLFROM = "callfrom";
    private static final String EXTRA_VALUE_STITCHIMAGE_SETTINGS_CALLFROM_ASUSSETTINGS = "AsusSettings";

    //For game genie lock mode ,blocking home/recent key
    private static final int GAMEGENIE_KEY_LOCKED = 0;
    private static final int GAMEGENIE_LOCK_MODE_ENABLE = 1;
    private static final int GAMEGENIE_UNLOCK = 2;
    private boolean mIsGameGenieLock = false;
    private NotifyGameGenieLockModeRunnable mNotifyGameGenieLockModeRunnable;
    //END:Chilin_Wang@asus.com

    /* The number of steps between min and max brightness */
    private static final int BRIGHTNESS_STEPS = 10;

    SettingsObserver mSettingsObserver;
    ShortcutManager mShortcutManager;
    PowerManager.WakeLock mBroadcastWakeLock;
    PowerManager.WakeLock mPowerKeyWakeLock;
    boolean mHavePendingMediaKeyRepeatWithWakeLock;

    private int mCurrentUserId;

    // Maps global key codes to the components that will handle them.
    private GlobalKeyManager mGlobalKeyManager;

    // Fallback actions by key code.
    private final SparseArray<KeyCharacterMap.FallbackAction> mFallbackActions =
            new SparseArray<KeyCharacterMap.FallbackAction>();

    private final LogDecelerateInterpolator mLogDecelerateInterpolator
            = new LogDecelerateInterpolator(100, 0);

    private final MutableBoolean mTmpBoolean = new MutableBoolean(false);

    private static final int MSG_ENABLE_POINTER_LOCATION = 1;
    private static final int MSG_DISABLE_POINTER_LOCATION = 2;
    private static final int MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK = 3;
    private static final int MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK = 4;
    private static final int MSG_KEYGUARD_DRAWN_COMPLETE = 5;
    private static final int MSG_KEYGUARD_DRAWN_TIMEOUT = 6;
    private static final int MSG_WINDOW_MANAGER_DRAWN_COMPLETE = 7;
    private static final int MSG_DISPATCH_SHOW_RECENTS = 9;
    private static final int MSG_DISPATCH_SHOW_GLOBAL_ACTIONS = 10;
    private static final int MSG_HIDE_BOOT_MESSAGE = 11;
    private static final int MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK = 12;
    private static final int MSG_POWER_DELAYED_PRESS = 13;
    private static final int MSG_POWER_LONG_PRESS = 14;
    private static final int MSG_UPDATE_DREAMING_SLEEP_TOKEN = 15;
    private static final int MSG_REQUEST_TRANSIENT_BARS = 16;
    private boolean mIsTranscoverEnabled;
    private static final int MSG_SHOW_TV_PICTURE_IN_PICTURE_MENU = 17;
    private static final int MSG_BACK_LONG_PRESS = 18;
    private static final int MSG_DISPOSE_INPUT_CONSUMER = 19;


    private static final int MSG_REQUEST_TRANSIENT_BARS_ARG_STATUS = 0;
    private static final int MSG_REQUEST_TRANSIENT_BARS_ARG_NAVIGATION = 1;

    //BEGIN: Jeffrey_Chiang@asus.com
    private static final int MSG_ZENNY_EVENT = 4201;
    private static final int MSG_LOGUPLOADER_EVENT = 4202;
    private static final int MSG_SCREEN_UNPINNING_EVENT = 4203;
    private static final int MSG_DOUBLE_CLICK_EVENT = 4204;
    private static final int MSG_LONG_PRESS_EVENT = 4205;
    private static final int MSG_KEY_UP_EVENT = 4206;
    private static final int MSG_UPDATE_LOCK_PHYSICAL_STATUS = 4207;
    private static final int MSG_UPDATE_KEYGUARD_SCREEN_STATUS = 4208;
    private static final int MSG_FP_LAUNCH_CAMERA_EVENT = 4210;
    private static final int MSG_FP_RESET_LAUNCH_CAMERA_FLAG = 4211;
    private static final int MSG_DOUBLE_TAP_ON_HOME_TARGET_APP = 4212;
    private static final int MSG_INADVERTENT_TOUCH_EVENT = 4213;
    private static final int MSG_INADVERTENT_HARDWAREKEY_PRESSED_EVENT = 4214;
    //End: Jeffrey_Chiang@asus.com
    //add touchGestureKey +++
    private static final int MSG_TOUCHGESTURE_DELAY_WAKEUP_SCREEN=8000;
    private long mGestureKeyWakeTime=0;
    //add touchGestureKey ---
   
    //BEGIN: Hungjie_Tseng@asus.com, AlwaysOn
    AlwaysOnControllerInternal mAlwaysOnController;

    private static final int TURNING_SCREEN_OFF = 1;
    private static final int TURNING_SCREEN_ON = 2;
    //add mohongwu@wind-mobi.com 2017/2/9 start
    private static final int MSG_ASUS_POWER_LONG_PRESS = 6666;
    //add mohongwu@wind-mobi.com 2017/2/9 end

    //begin:hungjie_tseng@asus.com
    boolean mHasAlwaysonWindow;
    //end:hungjie_tseng@asus.com
    //End:hungjie: Hungjie_Tseng@asus.com, AlwaysOn

    //dongjiangpeng@wind-mobi.com add 2016/12/21 start
    private Sensor mProximityMotion;
    //dongjiangpeng@wind-mobi.com add 2016/12/21 end

    /*wangchaobin@wind-mobi.com added  2017.01.10 for new feature begin*/
    // Trigger proximity if distance is less than 5 cm.
    private static final float TYPICAL_PROXIMITY_THRESHOLD = 5.0f;
    private float mProximityThreshold;
    private BroadcastReceiver phoneHungupReceiver = new  BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action == ASUS_HUNGUP_BROADCAST) {
                isAsusPowerDown = false;
                mSensorManager.unregisterListener(mDistanceSensorListener);
                isListenerRegisted = false;
            }
        }
    };
    /*wangchaobin@wind-mobi.com added  2017.01.10 for new feature end*/

     // window attributes that are in the HideLockScreen state
     private ArrayList<WindowManager.LayoutParams> mHideLockScreenAttrs = new ArrayList<WindowManager.LayoutParams>();

    //+++ jeson_li@20141112 for flipcover
    static public final String FLIPCOVER_TAG = "FlipCover";
    private boolean mHasTranscoverInfoFeature;
    private boolean mUnlockScreenOnWakingUp;
    private boolean mTranscoverAutomaticUnlock;
    private android.os.UserManager mCoverUserManager=null;
    private boolean mIsTranscoverEnabledLastForCover;
    private int mCurrentUserIdLastForCover;
    private boolean mTranscoverAutomaticUnlockLastForCover;

    // chih-hsuan add for virtual key light behavior +++
    private VirtualKeyBackLightService mVirtualKeyBackLight;
    // chih-hsuan add for virtual key light behavior ---

    /*vr mode enabled state, to disable power key.*/
    private boolean mVrModeEnabled = false;
    private boolean mHasFeatureVR = false;


    //BEGIN:Chilin_Wang@asus.com triple tap powerkey to make emergency call for India
    private EmergencyAffordanceManager mEmergencyAffordanceManager;
    private PowerManager.WakeLock mEmergencyCallWakeLock = null;
    //END:Chilin_Wang@asus.com

    private FlipCover2ServiceDelegate mFlipCover2ServiceDelegate=null;

    //BEGIN: Jeffrey_Chiang@asus.com Fingerprint
    // Avoid receiving continuous keyevents in a short time
    private boolean mIsLaunchCameraFromFpPending = false;
    private boolean mIsLaunchCameraFromFpEnabled = false;
    //END: Jeffrey_Chiang@asus.com
    //BEGIN: Chilin_Wang@asus.com, Avoid receiving backkey event after screen is just unpinning
    private boolean mIsScreenUnpinning = false;
    //END: Chilin_Wang@asus.com

    // BEGIN: archie_huang@asus.com
    //For feature: Colorful Navigation Bar
    private WindowState mNavBarColorProvider;
    private int mNavigationBarColor = -1;
    // END: archie_huang@asus.com

    //BEGIN : roy_huang@asus.com
    private InadvertentTouchControllerInternal mInadvertentTouchController;
    private boolean mVolumeUpKeyLongPressed = false;
    //END : roy_huang@asus.com
    //BEGIN:HJ@asus.com
    private static final int INADVERTENTTOUCH_WINDOW_FORMAT = 5566;
    
    //BEGIN : roy_huang@asus.com
    private boolean mIsFpNavigationKeysEnabled = false;
    private boolean mShortPressOnHome = false;
    private boolean mLongPressOnHome = false;
    private boolean mDoublePressOnHome = false;
    private boolean mIsNavigationPaymentEnabled = false;
    private boolean mIsCNSku = false;
    private String mHomeDoubleTapTargetAppPackage;
    private String mHomeDoubleTapTargetAppClass;
    //END : roy_huang@asus.com
    /**
     * with cover feature
     */
    private static final String FEATURE_ASUS_TRANSCOVER = "asus.hardware.transcover";
    /**
     * Indicate cover with hole
     */
    private static final String FEATURE_ASUS_TRANSCOVER_INFO = "asus.hardware.transcover_info";
    private static final int MSG_FLIPCOVER_SYSTEM_GOTOSLEEP =62101;
    private static final int MSG_FLIPCOVER_SYSTEM_WAKEUP =62102;
    private static final int MSG_FLIPCOVER_SYSTEM_USERACTIVITY = 62103;
    private static final int MSG_FLIPCOVER_SYSTEM_POWEROFF =62104;
    private static final int MSG_FLIPCOVER_SYSTEM_REBOOT = 62105;
    private static final int MSG_FLIPCOVER_AIRPLANE_CHANGED_ON = 62106;
    private static final int MSG_FLIPCOVER_AIRPLANE_CHANGED_OFF = 62107;
    private static final int MSG_FLIPCOVER_SET_PROP_FOR_HALL_SENSOR = 62108;
    private static final int MSG_FLIPCOVER_UNLOCK_LOCKSCREEN = 62109;
    //BEGIN: Chris_Ye@asus.com add for disbale & enable home/back/recent key
    private boolean mIsShieldKeyCode = false;
    //END: Chris_Ye@asus.com add for disbale & enable home/back/recent key

    /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 begin
    private static final boolean WIND_DEF_OTG_REVERSE = SystemProperties.get("ro.wind.otg_reverse_charging").equals("1");
    private static final String ASUS_OTG_REVERSE_CHARGE_ENABLE = "reverse_charging_enable";
    private static final int MSG_OTG_PLUG_IN_OPERATION = MSG_FP_RESET_LAUNCH_CAMERA_FLAG + 20;
    private static final int MSG_OTG_PLUG_OUT_OPERATION = MSG_FP_RESET_LAUNCH_CAMERA_FLAG + 21;
    private static final String OTG_REVERSE_CHARGING_CONTROL = "/sys/class/usbsw/usbsw/usbsw_val";
    private static final String OTG_CHARGING_ENABLE = "/sys/class/otg_chg/otg_chg/otg_chg_val";
    private static final String OTG_CURRENT_STATE = "/sys/class/switch/otg_state/state";
    private static final String OTG_BATTERY_PERCENT = "/sys/class/power_supply/battery/capacity";
    private static final String WIND_DEF_DATA_OTG_SELECT_MODE = "wind_def_data_otg_select_mode";
    private static final String ACTION_CLOSE_OTG_PLUG_IN_DIALOG = "android.intent.action.CLOSE_OTG_PLUG_IN_DIALOG";
    private static final int OTG_PLUG_IN_NORMOL_MODE = 0;
    private static final int OTG_PLUG_IN_REVERSE_CHARGE_MODE = 1;
    private static int BATTERY_LEVEL_20 = 20;
    private static int BATTERY_LEVEL_FULL = 100;
    private static int BATTERY_CURRENT_LEVEL;
    private static AlertDialog ReverseChargingDialog;
    private static boolean canPopOtgDialog = false;
    private static Intent mOtgSelectIntent;
    private static boolean otgState = false;  //M: modify by cenxingcan@wind-mobi.com 2017/01/12
    /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 end

    //++gaohui@wind-mobi.com add for usb_ntc 20170323 begin
    private static final boolean WIND_DEF_USB_NTC = SystemProperties.get("ro.wind.def.usb_ntc").equals("1");
    //--gaohui@wind-mobi.com add for usb_ntc 20170323 end

    //+++ cenxingcan@wind-mobi.com [ALPS03101432] add new Feature#149702 begin 2016/12/30 +++
    private static final boolean WIND_DEF_MUTERINGER_FEATURE = SystemProperties.get("ro.wind.def_muteringer").equals("1");
    //+++ cenxingcan@wind-mobi.com [ALPS03101432] add new Feature#149702 end 2016/12/30 +++

    //lishunbo@wind-mobi.com modify 20180109 for Chile SAE start
    private static final String COUNTRY_CODE = SystemProperties.get("ro.config.versatility", "");
    //lishunbo@wind-mobi.com modify 20180109 for Chile SAE end

    /**for hall sensor to enable/disable irq
     * @author Jeson_Li
     * @date 2015-9-21
     */
    private void setCoverSysPropetyForHallSensor(){
        try {
            boolean enableSensor=false;
            if(android.os.Build.FEATURES.HAS_TRANSCOVER){//with cover
                if(mHasHallSensorFeature){//with hall sensor
                    if(isPrivateUser()){//snap view in L , or not owner user in M
                        enableSensor=true;
                    }else{
                        enableSensor=(!mHasTranscoverInfoFeature&&mIsTranscoverEnabled)||(mHasTranscoverInfoFeature&&(mIsTranscoverEnabled||mTranscoverAutomaticUnlock));
                    }
                }else{//without hall sensor
                    enableSensor=false;
                }
            }else{//without cover
                enableSensor=mHasHallSensorFeature;
            }
            android.os.SystemProperties.set("persist.asus.coverenabled", enableSensor?"1":"0");
            Log.i(TAG, "setCoverSysPropetyForHallSensor:"+enableSensor);
        } catch (Exception e) {
            // TODO: handle exception
            Log.w(FLIPCOVER_TAG, e.toString());
        }
    }

    private void setFlipCoverSysPropety(){
        try {
            int value=(mIsTranscoverEnabled&&mHasTranscoverFeature&&mHasTranscoverInfoFeature&&mLidState == LID_CLOSED)?1:0;
            //ASUS Flip Cover For framework to  get hall sensor state+++
            value=(mHasTranscoverFeature&&mLidState == LID_CLOSED)?1:0;
            android.os.SystemProperties.set("persist.asus.lidstate", Integer.toString(value));
            //ASUS Flip Cover For framework to  get hall sensor state---
        } catch (Exception e) {
            // TODO: handle exception
            Log.w(FLIPCOVER_TAG, e.toString());
        }
    }

    private void notifyAirplaneModeState(int msgType){
        try {
            boolean on=(msgType==MSG_FLIPCOVER_AIRPLANE_CHANGED_ON);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", on);
            mContext.sendBroadcast(intent);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public void notifyAirplaneModeState(boolean on){
        if(isCoverClosed()&&mHandler!=null&&mContext!=null){
            if(on){
                mHandler.removeMessages(MSG_FLIPCOVER_AIRPLANE_CHANGED_ON);
                mHandler.sendEmptyMessage(MSG_FLIPCOVER_AIRPLANE_CHANGED_ON);
            }else{
                mHandler.removeMessages(MSG_FLIPCOVER_AIRPLANE_CHANGED_OFF);
                mHandler.sendEmptyMessage(MSG_FLIPCOVER_AIRPLANE_CHANGED_OFF);
            }
        }
    }

    public Bundle getFlipCover2Options(boolean forNotifyLidSwitchChanged){
        Bundle bundle=new Bundle();
        bundle.putInt("flipcover2_mLidState", mLidState);
        bundle.putBoolean("flipcover2_isLidOpened", mLidState==LID_OPEN);
        bundle.putBoolean("flipcover2_mScreenOnEarly", mScreenOnEarly);
        bundle.putBoolean("flipcover2_mScreenOnFully", mScreenOnFully);
        bundle.putBoolean("flipcover2_mAwake", mAwake);
        bundle.putBoolean("flipcover2_forNotifyLidSwitchChanged", forNotifyLidSwitchChanged);
        return bundle;
    }

    public Bundle getFlipCover2Options(){
        return getFlipCover2Options(false);
    }

    public boolean isCoverClosed(){
        return mIsTranscoverEnabled&&mHasTranscoverFeature&&mHasTranscoverInfoFeature&&mLidState == LID_CLOSED&&!isPrivateUser();
    }

    public void wakeUpForFlipCover(){
        if(isCoverClosed()&&mHandler!=null&&mPowerManager!=null){
            mHandler.removeMessages(MSG_FLIPCOVER_SYSTEM_WAKEUP);
            mHandler.sendEmptyMessage(MSG_FLIPCOVER_SYSTEM_WAKEUP);
        }
    }

    public void goToSleepForFlipCoverAfterTimeStamp(long timeStampMillis){
        if(isCoverClosed()&&mHandler!=null&&mPowerManager!=null){
            mHandler.removeMessages(MSG_FLIPCOVER_SYSTEM_GOTOSLEEP);
            if(timeStampMillis>0){
                mHandler.sendEmptyMessageDelayed(MSG_FLIPCOVER_SYSTEM_GOTOSLEEP, timeStampMillis);
            }else{
                mHandler.sendEmptyMessage(MSG_FLIPCOVER_SYSTEM_GOTOSLEEP);
            }
        }
    }

    public void goToSleepForFlipCoverAtTime(long atTimeMillis){
        if(isCoverClosed()&&mHandler!=null&&mPowerManager!=null){
            mHandler.removeMessages(MSG_FLIPCOVER_SYSTEM_GOTOSLEEP);
            if(atTimeMillis>SystemClock.uptimeMillis()){
                mHandler.sendEmptyMessageAtTime(MSG_FLIPCOVER_SYSTEM_GOTOSLEEP, atTimeMillis);
            }else{
                mHandler.sendEmptyMessage(MSG_FLIPCOVER_SYSTEM_GOTOSLEEP);
            }
        }
    }

    public void userActivityForFlipCover(){
        if(isCoverClosed()&&mHandler!=null&&mPowerManager!=null){
            mHandler.removeMessages(MSG_FLIPCOVER_SYSTEM_USERACTIVITY);
            mHandler.sendEmptyMessage(MSG_FLIPCOVER_SYSTEM_USERACTIVITY);
        }
    }

    public void rebootForFlipCover(){
        if(isCoverClosed()&&mHandler!=null&&mContext!=null){
                mHandler.removeMessages(MSG_FLIPCOVER_SYSTEM_REBOOT);
                mHandler.sendEmptyMessage(MSG_FLIPCOVER_SYSTEM_REBOOT);
        }
    }

    public void powerOffForFlipCover(){
        if(isCoverClosed()&&mHandler!=null&&mContext!=null){
                mHandler.removeMessages(MSG_FLIPCOVER_SYSTEM_POWEROFF);
                mHandler.sendEmptyMessage(MSG_FLIPCOVER_SYSTEM_POWEROFF);
        }
    }

    public boolean isPrivateUser() {
        try {
            //int myUserId=android.app.ActivityManagerNative.getDefault().getCurrentUser().id;
            int myUserId=mCurrentUserId;
            Log.d(TAG, "myUserId:"+myUserId);
            android.content.pm.UserInfo user = mCoverUserManager.getUserInfo(myUserId);
            if(user==null){
                Log.d(TAG, "userInfo=null");
            }else{
                boolean result=false;
                if(mHasTranscoverInfoFeature){
                    result=false;
                    Log.d(TAG, "mHasTranscoverInfoFeature:true, isPrivateUser:"+result+", if true, means it is not owner user in M, or it is snapview in L");
                }else{
                    result=false;
                    Log.d(TAG, "mHasTranscoverInfoFeature:false, isPrivateUser:"+result+", if true, means it is snapview");
                }
                return result;
            }
        } catch (Exception e) {
            // TODO: handle exception
            Log.d(TAG, "isPrivateUser exception:"+e.toString());
        }
        return false;
    }
    //--- jeson_li@20141112 for flipcover
    
    private class PolicyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENABLE_POINTER_LOCATION:
                    enablePointerLocation();
                    break;
                case MSG_DISABLE_POINTER_LOCATION:
                    disablePointerLocation();
                    break;

                case MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK:
                    dispatchMediaKeyWithWakeLock((KeyEvent)msg.obj);
                    break;
                case MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK:
                    dispatchMediaKeyRepeatWithWakeLock((KeyEvent)msg.obj);
                    break;
                case MSG_DISPATCH_SHOW_RECENTS:
                    showRecentApps(false, msg.arg1 != 0);
                    break;
                case MSG_DISPATCH_SHOW_GLOBAL_ACTIONS:
                    showGlobalActionsInternal();
                    break;
                case MSG_KEYGUARD_DRAWN_COMPLETE:
                    if (DEBUG_WAKEUP) Slog.w(TAG, "Setting mKeyguardDrawComplete");
                    finishKeyguardDrawn();
                    break;
                case MSG_KEYGUARD_DRAWN_TIMEOUT:
                    Slog.w(TAG, "Keyguard drawn timeout. Setting mKeyguardDrawComplete");
                    finishKeyguardDrawn();
                    break;
                case MSG_WINDOW_MANAGER_DRAWN_COMPLETE:
                    if (DEBUG_WAKEUP) Slog.w(TAG, "Setting mWindowManagerDrawComplete");
                    finishWindowsDrawn();
                    break;
                case MSG_HIDE_BOOT_MESSAGE:
                    handleHideBootMessage();
                    break;
                case MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK:
                    launchVoiceAssistWithWakeLock(msg.arg1 != 0);
                    break;
                case MSG_POWER_DELAYED_PRESS:
                    powerPress((Long)msg.obj, msg.arg1 != 0, msg.arg2);
                    finishPowerKeyPress();
                    break;
                case MSG_POWER_LONG_PRESS:
                    powerLongPress();
                    break;
                //add mohongwu@wind-mobi.com 2017/2/9 start
                case MSG_ASUS_POWER_LONG_PRESS:
                    asusPowerLongPress();
                    break;
                //add mohongwu@wind-mobi.com 2017/2/9 end
                case MSG_UPDATE_DREAMING_SLEEP_TOKEN:
                    updateDreamingSleepToken(msg.arg1 != 0);
                    break;
                    //+++ jeson_li flipcover2+++
                case MSG_FLIPCOVER_SYSTEM_WAKEUP:
                    if(mPowerManager!=null){
                        mPowerManager.wakeUp(SystemClock.uptimeMillis());
                    }
                    break;
                case MSG_FLIPCOVER_SYSTEM_USERACTIVITY:
                    if(mPowerManager!=null){
                        mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
                    }
                    break;
                case MSG_FLIPCOVER_SYSTEM_GOTOSLEEP:
                    if(mPowerManager!=null){
                        mPowerManager.goToSleep(SystemClock.uptimeMillis());
                    }
                    break;
                case MSG_FLIPCOVER_SYSTEM_REBOOT:
                    if(mContext!=null){
                        Intent i = new Intent(Intent.ACTION_REBOOT);
                        i.putExtra("nowait", 1);
                        i.putExtra("interval", 1);
                        i.putExtra("window", 0);
                        mContext.sendBroadcast(i);
                    }
                    break;
                case MSG_FLIPCOVER_SYSTEM_POWEROFF:
                    if(mContext!=null){
                        Intent shutdown = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
                        shutdown.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
                        shutdown.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(shutdown);
                    }
                    break;
                case MSG_FLIPCOVER_AIRPLANE_CHANGED_ON:
                case MSG_FLIPCOVER_AIRPLANE_CHANGED_OFF:
                    notifyAirplaneModeState(msg.what);
                    break;
                case MSG_FLIPCOVER_SET_PROP_FOR_HALL_SENSOR:
                    //for hall sensor to enable/disable irq, move to here to avoid dead lock (fix TT-701354)
                    setCoverSysPropetyForHallSensor();
                    break;
                case MSG_FLIPCOVER_UNLOCK_LOCKSCREEN:
                    if(mKeyguardDelegate!=null){
                        mKeyguardDelegate.unLockNonSecureLock(mCurrentUserId);
                    }
                    break;
                //--- jeson_li flipcover2---
                case MSG_REQUEST_TRANSIENT_BARS:
                    WindowState targetBar = (msg.arg1 == MSG_REQUEST_TRANSIENT_BARS_ARG_STATUS) ?
                            mStatusBar : mNavigationBar;
                    if (targetBar != null) {
                        requestTransientBars(targetBar);
                    }
                    break;
                case MSG_SHOW_TV_PICTURE_IN_PICTURE_MENU:
                    showTvPictureInPictureMenuInternal();
                    break;
                case MSG_BACK_LONG_PRESS:
                    backLongPress();
                    break;
                case MSG_DISPOSE_INPUT_CONSUMER:
                    disposeInputConsumer((InputConsumer) msg.obj);
                    break;
                case MSG_ZENNY_EVENT: {
                    Log.d(TAG, "MSG_LOGUPLOADER_EVENT combineKey Trigger !!!");
                    String zennyStatus = new String((msg.arg1 == 1) ? "non_suspend" : "suspend");
                    break;
                }
                case MSG_LOGUPLOADER_EVENT: {
                    Log.d(TAG, "MSG_LOGUPLOADER_EVENT combineKey Trigger !!!");
                    Intent intent = new Intent("com.asus.loguploader.action.COMBINE_KEY");
                    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                    break;
                }
                case MSG_SCREEN_UNPINNING_EVENT: {
                    Log.d(TAG, "MSG_SCREEN_UNPINNING_EVENT combineKey Trigger !!!");
                    mHandler.removeCallbacks(mScreenUnpinningRunnable);
                    mHandler.postDelayed(mScreenUnpinningRunnable, ViewConfiguration.getLongPressTimeout());
                    break;
                }
                //+++Chilin_Wang@asus.com, Instant camera porting+++
                case MSG_LONG_PRESS_EVENT:
                    String longPressSrc = (String)msg.obj;
                    String longPressStatus;
                    int longPressKeyCode = msg.arg2;

                    if (msg.arg1 == 1) {
                        longPressStatus = "suspend";
                    } else {
                        longPressStatus = "non_suspend";
                    }

                    mLongPressLaunchCameraRunnable.set(longPressSrc,longPressStatus,longPressKeyCode);
                    if ("hardware:camerakey".equals(longPressSrc)) {
                        mHandler.removeCallbacks(mLongPressLaunchCameraRunnable);
                        mHandler.postDelayed(mLongPressLaunchCameraRunnable, ViewConfiguration.getLongPressTimeout());
                    }

                    if ("hardware:camerarecordkey".equals(longPressSrc)) {
                        mHandler.removeCallbacks(mLongPressLaunchCameraRunnable);
                        mHandler.postDelayed(mLongPressLaunchCameraRunnable, ViewConfiguration.getLongPressTimeout());
                    }
                    break;
                case MSG_DOUBLE_CLICK_EVENT:
                    String doubleClickSrc = (String)msg.obj;
                    String doubleClickStatus;
                    int doubleKeyCode = msg.arg2;
                    if (msg.arg1 == 1) {
                        doubleClickStatus = "suspend";
                    } else {
                        doubleClickStatus = "non_suspend";
                    }

                    if (mVolumeDownDoubleClickPending && "hardware:volumekey_down".equals(doubleClickSrc)) {
                        mVolumeDownDoubleClickPending = false;
                        mHandler.removeCallbacks(mVolumeDownDoubleClickTimeoutRunnable);
                        handleDoubleClickLaunchCamera(doubleClickSrc,doubleClickStatus,doubleKeyCode);
                    }

                    if (mVolumeUpDoubleClickPending && "hardware:volumekey_up".equals(doubleClickSrc)) {
                        mVolumeUpDoubleClickPending = false;
                        mHandler.removeCallbacks(mVolumeUpDoubleClickTimeoutRunnable);
                        handleDoubleClickLaunchCamera(doubleClickSrc,doubleClickStatus,doubleKeyCode);
                    }
                    break;
                case MSG_KEY_UP_EVENT:
                    String upSrc = (String)msg.obj;
                    mHandler.removeCallbacks(mLongPressLaunchCameraRunnable);

                    if ("hardware:volumedownkey_up".equals(upSrc)) {
                        mHandler.removeCallbacks(mVolumeDownDoubleClickTimeoutRunnable);
                        mVolumeDownDoubleClickPending = true;
                        mHandler.postDelayed(mVolumeDownDoubleClickTimeoutRunnable,
                                ViewConfiguration.getDoubleTapTimeout());
                    }

                    if ("hardware:volumeupkey_up".equals(upSrc)) {
                        mHandler.removeCallbacks(mVolumeUpDoubleClickTimeoutRunnable);
                        mVolumeUpDoubleClickPending = true;
                        mHandler.postDelayed(mVolumeUpDoubleClickTimeoutRunnable,
                                ViewConfiguration.getDoubleTapTimeout());
                    }
                    break;
                case MSG_UPDATE_KEYGUARD_SCREEN_STATUS:
                    String update = (String)msg.obj;
                    if (update.equals("show")) {
                        mIsKeyguardShow = true;
                    } else {
                        mIsKeyguardShow = false;
                    }
                    break;
                //---Instant camera porting---
                //+++Chilin_Wang@asus.com,Porting of lock physical key when the screen pinning request is show.
                case MSG_UPDATE_LOCK_PHYSICAL_STATUS: {
                    synchronized (mLockPhysicalKey) {
                        mIsLockPhysicalKey = (msg.arg1 == 1);
                        Log.i(TAG,"Update mIsLockPhysicalKey = "+mIsLockPhysicalKey);
                    }
                    break;
                }
                //---Chilin_Wang@asus.com
                //BEGIN: Steven_Chao@asus.com
                case MSG_FP_LAUNCH_CAMERA_EVENT: {
                    boolean isCameraActive = isCameraActive();
                    Slog.d(TAG, "mIsLaunchCameraFromFpPending=" + mIsLaunchCameraFromFpPending + ". isCameraActive=" + isCameraActive);
                    if (!mIsLaunchCameraFromFpPending && !isCameraActive && isDeviceProvisioned()) {
                        Slog.d(TAG, "Launch camera due to the event from fingerprint sensor");
                        String source = (String) msg.obj;
                        String status = msg.arg1 == 1 ? "suspend" : "non_suspend";
                        int keyCode = msg.arg2;
                        mIsLaunchCameraFromFpPending = true;
                        handleDoubleClickLaunchCamera(source, status, keyCode);
                        mHandler.sendEmptyMessageDelayed(MSG_FP_RESET_LAUNCH_CAMERA_FLAG,
                            1500L); // The launch time of AsusCamera always exceeds 1200ms
                    }
                } break;
                case MSG_FP_RESET_LAUNCH_CAMERA_FLAG: {
                    mIsLaunchCameraFromFpPending = false;
                    Slog.d(TAG, "Reset flag for camera launch from fingerprint sensor");
                } break;
                // END: Steven_Chao@asus.com
                //BEGIN : roy_huang@asus.com
                case MSG_INADVERTENT_TOUCH_EVENT: {
                    if (Build.FEATURES.ENABLE_INADVERTENTTOUCH && mInadvertentTouchController != null) {
                        Slog.d(TAG, "Disable inadvertent touch functionality.");
                        mInadvertentTouchController.combinePowerFPKey();
                    }
                } break;
                //END : roy_huang@asus.com

                //Begin:HJ@asus.com
                case MSG_INADVERTENT_HARDWAREKEY_PRESSED_EVENT: {
                    if(mInadvertentTouchController != null) {
                        mInadvertentTouchController.notifyHardwarekeyPressed();
                    }
                } break;
                //End:HJ@asus.com
                // add touch gesture key +++
                case MSG_TOUCHGESTURE_DELAY_WAKEUP_SCREEN: {
                    Log.d(TAG, "receive MSG_TOUCH_GESTURE_DELAY_WAKE_UP 200ms");
                    // A: huangyouzhong@wind-mobi.com 20170512 -s
                    int psValue = getPsNodeValue();
                    int fiveCmValue = getDataReadFiveNodeValue();
                    Log.d(TAG, "receive MSG_TOUCH_GESTURE_DELAY_WAKE_UP :"+(psValue < fiveCmValue));
                    if (psValue < fiveCmValue) {
                        // A: huangyouzhong@wind-mobi.com 20170512 -e
                        wakeUp(mGestureKeyWakeTime, mAllowTheaterModeWakeFromKey,
                                "android.policy:KEY");
                    }
                } break;
                // BEGIN : roy_huang@asus.com
                case MSG_DOUBLE_TAP_ON_HOME_TARGET_APP: {
                    Slog.i(TAG,"MSG_DOUBLE_TAP_ON_HOME_TARGET_APP");
                    luanchHomeDoubleTapTargetApp(msg.arg1 != 0);
                } break;
                //END : roy_huang@asus.com
                /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 begin
                case MSG_OTG_PLUG_IN_OPERATION: {
                    if (getCurrBatteryPercent() <= BATTERY_LEVEL_20) {
                        setPowerReverseChargeEnable(false,OTG_REVERSE_CHARGING_CONTROL);
                    } else {
                        startOtgModeChooserActivity(mContext);
                    }
                }
                break;
                case MSG_OTG_PLUG_OUT_OPERATION: {
                    setPowerReverseChargeEnable(false,OTG_REVERSE_CHARGING_CONTROL);
                    setPowerReverseChargeEnable(false,OTG_CHARGING_ENABLE);  //M: add by cenxingcan@wind-mobi.com 2017/01/12
                    /**if (ReverseChargingDialog != null) {
                        ReverseChargingDialog.dismiss();
                    }**/
                    sendDismissOtgDialogBroadcast(mContext);
                    setPopOtgDialogValue(false);
                }
                break;
                /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 end
                //dongjiangpeng@wind-mobi.com add 2016/12/21 start
                case MSG_GESTURE_LISTEN:{
                    //M: huangyouzhong@wind-mobi.com 20170512 -s
                    int psValue = getPsNodeValue();
                    int fiveCmValue = getDataReadFiveNodeValue();
                    if(psValue < fiveCmValue) {
                    //M: huangyouzhong@wind-mobi.com 20170512 -e
                        int keyCode = msg.arg1;
                        boolean isOpenCamera = msg.arg2 == 1?true:false;
                        long eventTime = ((Long)msg.obj).longValue();
                        if(keyCode == KeyEvent.KEYCODE_GESTURE_DOUBLE_CLICK){
                            mVibrator.vibrate(50);
                        }else{
                            sendGestureBroadcast(keyCode);
                        }
                        if(!isOpenCamera){
                            wakeUp(eventTime, mAllowTheaterModeWakeFromKey, "android.policy:KEY");
                        }
                    }
                    mSensorManager.unregisterListener(mDistanceSensorListener);
                }
                break;
                //dongjiangpeng@wind-mobi.com add 2016/12/21 end
                //++gaohui@wind-mobi.com add for usb_ntc begin
                case MSG_CANCEL_NOTIFICATION :

                    mNotificationManager.cancelAsUser(null, USB_NTC_NOTICATION_ID, UserHandle.ALL);
                break;
                //++gaohui@wind-mobi.com add for usb_ntc begin
            }
        }
    }

    private UEventObserver mHDMIObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            setHdmiPlugged("1".equals(event.get("SWITCH_STATE")));
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            // Observe all users' changes
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.END_BUTTON_BEHAVIOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.WAKE_GESTURE_ENABLED), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACCELEROMETER_ROTATION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.USER_ROTATION), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_OFF_TIMEOUT), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.POINTER_LOCATION), false, this,
                    UserHandle.USER_ALL);

            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.DEFAULT_INPUT_METHOD), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.IMMERSIVE_MODE_CONFIRMATIONS), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.POLICY_CONTROL), false, this,
                    UserHandle.USER_ALL);
            //+++cenxingcan@wind-mobi.com add begin
            if (WIND_DEF_OTG_REVERSE) {
                resolver.registerContentObserver(Settings.System.getUriFor(WIND_DEF_DATA_OTG_SELECT_MODE), false, this,UserHandle.USER_ALL);
            }
            //---cenxingcan@wind-mobi.com add end
            //+++Chilin_Wang@asus.com,Instant camera porting
            resolver.registerContentObserver(Settings.System.getUriFor(
                            Settings.System.ASUS_LOCKSCREEN_INSTANT_CAMERA), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                            Settings.System.ASUS_LOCKSCREEN_LONGPRESS_INSTANT_CAMERA), false, this,
                    UserHandle.USER_ALL);
            //---Instant camera porting

            //+++Chilin_Wang@asus.com, long-pressing switch key porting
            resolver.registerContentObserver(Settings.System.getUriFor(
                            Settings.System.LONG_PRESSED_FUNC), false, this,
                    UserHandle.USER_ALL);
            //---long-pressing switch key porting

            // BEGIN leo_liao@asus.com, One-hand control
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.ACCESSIBILITY_ONEHAND_CTRL_QUICK_TRIGGER_ENABLED), false, this,
                    UserHandle.USER_ALL);
            // END leo_liao@asus.com
            
             // +++ jeson_li: ViewFlipCover
            if(android.os.Build.FEATURES.HAS_TRANSCOVER){
                resolver.registerContentObserver(Settings.System.getUriFor(
                        Settings.System.ASUS_TRANSCOVER), false, this,
                        UserHandle.USER_ALL);
                resolver.registerContentObserver(Settings.System.getUriFor(
                        Settings.System.ASUS_TRANSCOVER_AUTOMATIC_UNLOCK), false, this,
                        UserHandle.USER_ALL);
            }
            // --- jeson_li: ViewFlipCover

            //BEGIN: Jeffrey_Chiang@asus.com
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.ASUS_FINGERPRINT_SELFIE_CAMERA), false, this,
                    UserHandle.USER_ALL);
            //END: Jeffrey_Chiang@asus.com

            //BEGIN: Chris_Ye@asus.com add for disbale & enable home/back/recent key
            if(Build.FEATURES.ENABLE_SHIELD_KEYCODE) {
                resolver.registerContentObserver(Settings.Global.getUriFor(
                        Settings.Global.SHIELD_KEY_CODE), false, this,
                        UserHandle.USER_ALL);
            }
            //END: Chris_Ye@asus.com add for disbale & enable home/back/recent key
            
            //BEGIN : roy_huang@asus.com
            resolver.registerContentObserver(Settings.Global.getUriFor(
                            Settings.Global.ASUS_FINGERPRINT_ENABLE_NAVIGATION_KEYS), false, this,
                    UserHandle.USER_ALL);
            
            resolver.registerContentObserver(Settings.Global.getUriFor(
                            Settings.Global.MOBILE_PAYMENT_ENABLE), false, this,
                    UserHandle.USER_ALL);
            
            resolver.registerContentObserver(Settings.Global.getUriFor(
                            Settings.Global.MOBILE_PAYMENT_METHOD), false, this,
                    UserHandle.USER_ALL);
            //END : roy_huang@asus.com
            
            updateSettings();
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
            updateRotation(false);
        }
    }

    class MyWakeGestureListener extends WakeGestureListener {
        MyWakeGestureListener(Context context, Handler handler) {
            super(context, handler);
        }

        @Override
        public void onWakeUp() {
            synchronized (mLock) {
                if (shouldEnableWakeGestureLp()) {
                    performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_KEY, false);
                    wakeUp(SystemClock.uptimeMillis(), mAllowTheaterModeWakeFromWakeGesture,
                            "android.policy:GESTURE");
                }
            }
        }
    }

    class MyOrientationListener extends WindowOrientationListener {
        private final Runnable mUpdateRotationRunnable = new Runnable() {
            @Override
            public void run() {
                // send interaction hint to improve redraw performance
                mPowerManagerInternal.powerHint(PowerManagerInternal.POWER_HINT_INTERACTION, 0);
                updateRotation(false);
            }
        };

        MyOrientationListener(Context context, Handler handler) {
            super(context, handler);
        }

        @Override
        public void onProposedRotationChanged(int rotation) {
            if (localLOGV) Slog.v(TAG, "onProposedRotationChanged, rotation=" + rotation);
            mHandler.post(mUpdateRotationRunnable);
        }
    }
    MyOrientationListener mOrientationListener;

    private final StatusBarController mStatusBarController = new StatusBarController();

    private final BarController mNavigationBarController = new BarController("NavigationBar",
            View.NAVIGATION_BAR_TRANSIENT,
            View.NAVIGATION_BAR_UNHIDE,
            View.NAVIGATION_BAR_TRANSLUCENT,
            StatusBarManager.WINDOW_NAVIGATION_BAR,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            View.NAVIGATION_BAR_TRANSPARENT);

    private ImmersiveModeConfirmation mImmersiveModeConfirmation;

    private SystemGesturesPointerEventListener mSystemGestures;

    IStatusBarService getStatusBarService() {
        synchronized (mServiceAquireLock) {
            if (mStatusBarService == null) {
                mStatusBarService = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService("statusbar"));
            }
            return mStatusBarService;
        }
    }

    StatusBarManagerInternal getStatusBarManagerInternal() {
        synchronized (mServiceAquireLock) {
            if (mStatusBarManagerInternal == null) {
                mStatusBarManagerInternal =
                        LocalServices.getService(StatusBarManagerInternal.class);
            }
            return mStatusBarManagerInternal;
        }
    }

    /*
     * We always let the sensor be switched on by default except when
     * the user has explicitly disabled sensor based rotation or when the
     * screen is switched off.
     */
    boolean needSensorRunningLp() {
        if (mSupportAutoRotation) {
            if (mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    || mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    || mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    || mCurrentAppOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                // If the application has explicitly requested to follow the
                // orientation, then we need to turn the sensor on.
                return true;
            }
        }
        if ((mCarDockEnablesAccelerometer && mDockMode == Intent.EXTRA_DOCK_STATE_CAR) ||
                (mDeskDockEnablesAccelerometer && (mDockMode == Intent.EXTRA_DOCK_STATE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_LE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_HE_DESK))
                        || (mAsusDockEnablesAccelerometer && mDockMode != Intent.EXTRA_DOCK_STATE_UNDOCKED)) {
            // enable accelerometer if we are docked in ASUS dock that enables accelerometer
            // orientation management,
            return true;
        }
        if (mUserRotationMode == USER_ROTATION_LOCKED) {
            // If the setting for using the sensor by default is enabled, then
            // we will always leave it on.  Note that the user could go to
            // a window that forces an orientation that does not use the
            // sensor and in theory we could turn it off... however, when next
            // turning it on we won't have a good value for the current
            // orientation for a little bit, which can cause orientation
            // changes to lag, so we'd like to keep it always on.  (It will
            // still be turned off when the screen is off.)
            return false;
        }
        return mSupportAutoRotation;
    }

    /*
     * Various use cases for invoking this function
     * screen turning off, should always disable listeners if already enabled
     * screen turned on and current app has sensor based orientation, enable listeners
     * if not already enabled
     * screen turned on and current app does not have sensor orientation, disable listeners if
     * already enabled
     * screen turning on and current app has sensor based orientation, enable listeners if needed
     * screen turning on and current app has nosensor based orientation, do nothing
     */
    void updateOrientationListenerLp() {
        if (!mOrientationListener.canDetectOrientation()) {
            // If sensor is turned off or nonexistent for some reason
            return;
        }
        // Could have been invoked due to screen turning on or off or
        // change of the currently visible window's orientation.
        if (localLOGV) Slog.v(TAG, "mScreenOnEarly=" + mScreenOnEarly
                + ", mAwake=" + mAwake + ", mCurrentAppOrientation=" + mCurrentAppOrientation
                + ", mOrientationSensorEnabled=" + mOrientationSensorEnabled
                + ", mKeyguardDrawComplete=" + mKeyguardDrawComplete
                + ", mWindowManagerDrawComplete=" + mWindowManagerDrawComplete);
        boolean disable = true;
        // Note: We postpone the rotating of the screen until the keyguard as well as the
        // window manager have reported a draw complete.
        if (mScreenOnEarly && mAwake &&
                mKeyguardDrawComplete && mWindowManagerDrawComplete) {
            if (needSensorRunningLp()) {
                disable = false;
                //enable listener if not already enabled
                if (!mOrientationSensorEnabled) {
                    mOrientationListener.enable();
                    if(localLOGV) Slog.v(TAG, "Enabling listeners");
                    mOrientationSensorEnabled = true;
                }
            }
        }
        //check if sensors need to be disabled
        if (disable && mOrientationSensorEnabled) {
            mOrientationListener.disable();
            if(localLOGV) Slog.v(TAG, "Disabling listeners");
            mOrientationSensorEnabled = false;
        }
    }

    private void interceptPowerKeyDown(KeyEvent event, boolean interactive) {
        // Hold a wake lock until the power key is released.
        if (!mPowerKeyWakeLock.isHeld()) {
            mPowerKeyWakeLock.acquire();
        }

        // Cancel multi-press detection timeout.
        if (mPowerKeyPressCounter != 0) {
            mHandler.removeMessages(MSG_POWER_DELAYED_PRESS);
        }

        // Detect user pressing the power button in panic when an application has
        // taken over the whole screen.
        boolean panic = mImmersiveModeConfirmation.onPowerKeyDown(interactive,
                SystemClock.elapsedRealtime(), isImmersiveMode(mLastSystemUiFlags));
        if (panic) {
            mHandler.post(mHiddenNavPanic);
        }

        // Latch power key state to detect screenshot chord.
        if (interactive && !mScreenshotChordPowerKeyTriggered
                && (event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
            mScreenshotChordPowerKeyTriggered = true;
            mScreenshotChordPowerKeyTime = event.getDownTime();
            interceptScreenshotChord();
        }

        // Stop ringing or end call if configured to do so when power is pressed.
        TelecomManager telecomManager = getTelecommService();
        boolean hungUp = false;
        if (telecomManager != null) {
            if (telecomManager.isRinging()) {
                // Pressing Power while there's a ringing incoming
                // call should silence the ringer.
                //+++ cenxingcan@wind-mobi.com [ALPS03101432] add new Feature#149702 begin 2016/12/30 +++
                // telecomManager.silenceRinger();  //origin code
                if (WIND_DEF_MUTERINGER_FEATURE) {
                    telecomManager.muteRinger();
                } else {
                    telecomManager.silenceRinger();  //origin code
                }
                //+++ cenxingcan@wind-mobi.com [ALPS03101432] add new Feature#149702 end 2016/12/30 +++
            } else if ((mIncallPowerBehavior
                    & Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP) != 0
                    && telecomManager.isInCall() && interactive) {
                // Otherwise, if "Power button ends call" is enabled,
                // the Power button will hang up any current active call.
                hungUp = telecomManager.endCall();
            /*wangchaobin@wind-mobi.com added amend 2016.12.14 for feature 146623 begin*/
            } else if((mIncallPowerBehavior
                    & Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_DEFAULT) != 0
                    && telecomManager.isInCall() && interactive) {
                if (!isListenerRegisted) {
                    listenForCallHangup();
                }
                /*wangchaobin@wind-mobi.com added amend 2016.12.19 for feature 147973 begin*/
                if(isProximmitySensorPositive) {
                    /*wangchaobin@wind-mobi.com added amend 2016.12.19 for feature 147973 end*/
                    //do not screen on or off
                /*wangchaobin@wind-mobi.com added  2017.01.10 for new feature begin*/
                    isAsusPowerDown = !isAsusPowerDown;
                    if (isAsusPowerDown) {
                        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                        if (powerManager != null) {
                            powerManager.goToSleep(SystemClock.uptimeMillis(), PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
                        }
                        return;
                    }
                    /*wangchaobin@wind-mobi.com added  2017.01.10 for new feature end*/
                } else {
                    mPowerManager.wakeUp(SystemClock.uptimeMillis());
                }
               /*wangchaobin@wind-mobi.com added  2017.01.10 for new feature end*/
            }
        }

        GestureLauncherService gestureService = LocalServices.getService(
                GestureLauncherService.class);
        boolean gesturedServiceIntercepted = false;
        if (gestureService != null) {
            gesturedServiceIntercepted = gestureService.interceptPowerKeyDown(event, interactive,
                    mTmpBoolean);
            if (mTmpBoolean.value && mGoingToSleep) {
                mCameraGestureTriggeredDuringGoingToSleep = true;
            }
        }

        // If the power key has still not yet been handled, then detect short
        // press, long press, or multi press and decide what to do.
        mPowerKeyHandled = hungUp || mScreenshotChordVolumeDownKeyTriggered
                || mScreenshotChordVolumeUpKeyTriggered || gesturedServiceIntercepted;
        if (!mPowerKeyHandled) {
            if (interactive) {
                // When interactive, we're already awake.
                // Wait for a long press or for the button to be released to decide what to do.
                if (hasLongPressOnPowerBehavior()) {
                    Message msg = mHandler.obtainMessage(MSG_POWER_LONG_PRESS);
                    msg.setAsynchronous(true);
                    //modify by mohongwu@wind-mobi.com 2016/11/10 start
                    mHandler.sendMessageDelayed(msg,
                            ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
                    //modify by mohongwu@wind-mobi.com 2016/11/10 end
                    //add mohongwu@wind-mobi.com 2017/2/9 start
                    Message msg1 = mHandler.obtainMessage(MSG_ASUS_POWER_LONG_PRESS);
                    msg1.setAsynchronous(true);
                    mHandler.sendMessageDelayed(msg1,
                            ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout() + 6500);
                    //add mohongwu@wind-mobi.com 2017/2/9 end
                }
            } else {
                wakeUpFromPowerKey(event.getDownTime());

                if (mSupportLongPressPowerWhenNonInteractive && hasLongPressOnPowerBehavior()) {
                    Message msg = mHandler.obtainMessage(MSG_POWER_LONG_PRESS);
                    msg.setAsynchronous(true);
                    //add by mohongwu@wind-mobi.com 2016/11/10 start
                    mHandler.sendMessageDelayed(msg,
                            ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
                    //add by mohongwu@wind-mobi.com 2016/11/10 end
                    mBeganFromNonInteractive = true;
                } else {
                    final int maxCount = getMaxMultiPressPowerCount();

                    if (maxCount <= 1) {
                        mPowerKeyHandled = true;
                    } else {
                        mBeganFromNonInteractive = true;
                    }
                }
            }
        }
    }

    private void interceptPowerKeyUp(KeyEvent event, boolean interactive, boolean canceled) {
        final boolean handled = canceled || mPowerKeyHandled;
        mScreenshotChordPowerKeyTriggered = false;
        cancelPendingScreenshotChordAction();
        cancelPendingPowerKeyAction();

        if (!handled) {
            // Figure out how to handle the key now that it has been released.
            mPowerKeyPressCounter += 1;

            final int maxCount = getMaxMultiPressPowerCount();
            final long eventTime = event.getDownTime();
            if (mPowerKeyPressCounter < maxCount) {
                // This could be a multi-press.  Wait a little bit longer to confirm.
                // Continue holding the wake lock.
                Message msg = mHandler.obtainMessage(MSG_POWER_DELAYED_PRESS,
                        interactive ? 1 : 0, mPowerKeyPressCounter, eventTime);
                msg.setAsynchronous(true);
                mHandler.sendMessageDelayed(msg, ViewConfiguration.getDoubleTapTimeout());
                return;
            }

            // No other actions.  Handle it immediately.
            powerPress(eventTime, interactive, mPowerKeyPressCounter);
        }

        // Done.  Reset our state.
        finishPowerKeyPress();
    }

    private void finishPowerKeyPress() {
        mBeganFromNonInteractive = false;
        mPowerKeyPressCounter = 0;
        if (mPowerKeyWakeLock.isHeld()) {
            mPowerKeyWakeLock.release();
        }
    }

    private void cancelPendingPowerKeyAction() {
        if (!mPowerKeyHandled) {
            mPowerKeyHandled = true;
            mHandler.removeMessages(MSG_POWER_LONG_PRESS);
        }
        //add mohongwu@wind-mobi.com 2017/2/9 start
        mHandler.removeMessages(MSG_ASUS_POWER_LONG_PRESS);
        //add mohongwu@wind-mobi.com 2017/2/9 end
    }

    private void cancelPendingBackKeyAction() {
        if (!mBackKeyHandled) {
            mBackKeyHandled = true;
            mHandler.removeMessages(MSG_BACK_LONG_PRESS);
        }
    }

    private void powerPress(long eventTime, boolean interactive, int count) {
        if (mScreenOnEarly && !mScreenOnFully) {
            Slog.i(TAG, "Suppressed redundant power key press while "
                    + "already in the process of turning the screen on.");
            return;
        }

        if (count == 2) {
            powerMultiPressAction(eventTime, interactive, mDoublePressOnPowerBehavior);
        } else if (count == 3) {
            powerMultiPressAction(eventTime, interactive, mTriplePressOnPowerBehavior);
        } else if (interactive && !mBeganFromNonInteractive) {
            switch (mShortPressOnPowerBehavior) {
                case SHORT_PRESS_POWER_NOTHING:
                    break;
                case SHORT_PRESS_POWER_GO_TO_SLEEP:
                    mPowerManager.goToSleep(eventTime,
                            PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
                    break;
                case SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP:
                    mPowerManager.goToSleep(eventTime,
                            PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON,
                            PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
                    break;
                case SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP_AND_GO_HOME:
                    mPowerManager.goToSleep(eventTime,
                            PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON,
                            PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
                    launchHomeFromHotKey();
                    break;
                case SHORT_PRESS_POWER_GO_HOME:
                    launchHomeFromHotKey(true /* awakenFromDreams */, false /*respectKeyguard*/);
                    break;
            }
        }
    }

    private void powerMultiPressAction(long eventTime, boolean interactive, int behavior) {
        switch (behavior) {
            case MULTI_PRESS_POWER_NOTHING:
                break;
            case MULTI_PRESS_POWER_THEATER_MODE:
                if (!isUserSetupComplete()) {
                    Slog.i(TAG, "Ignoring toggling theater mode - device not setup.");
                    break;
                }

                if (isTheaterModeEnabled()) {
                    Slog.i(TAG, "Toggling theater mode off.");
                    Settings.Global.putInt(mContext.getContentResolver(),
                            Settings.Global.THEATER_MODE_ON, 0);
                    if (!interactive) {
                        wakeUpFromPowerKey(eventTime);
                    }
                } else {
                    Slog.i(TAG, "Toggling theater mode on.");
                    Settings.Global.putInt(mContext.getContentResolver(),
                            Settings.Global.THEATER_MODE_ON, 1);

                    if (mGoToSleepOnButtonPressTheaterMode && interactive) {
                        mPowerManager.goToSleep(eventTime,
                                PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
                    }
                }
                break;
            case MULTI_PRESS_POWER_BRIGHTNESS_BOOST:
                Slog.i(TAG, "Starting brightness boost.");
                if (!interactive) {
                    wakeUpFromPowerKey(eventTime);
                }
                mPowerManager.boostScreenBrightness(eventTime);
                break;
        }
    }

    private int getMaxMultiPressPowerCount() {
        if (mTriplePressOnPowerBehavior != MULTI_PRESS_POWER_NOTHING) {
            return 3;
        }
        if (mDoublePressOnPowerBehavior != MULTI_PRESS_POWER_NOTHING) {
            return 2;
        }
        return 1;
    }

    private void powerLongPress() {
        final int behavior = getResolvedLongPressOnPowerBehavior();
        switch (behavior) {
        case LONG_PRESS_POWER_NOTHING:
            break;
        case LONG_PRESS_POWER_GLOBAL_ACTIONS:
            mPowerKeyHandled = true;
            if (!performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false)) {
                performAuditoryFeedbackForAccessibilityIfNeed();
            }
            showGlobalActionsInternal();
            break;
        case LONG_PRESS_POWER_SHUT_OFF:
        case LONG_PRESS_POWER_SHUT_OFF_NO_CONFIRM:
            mPowerKeyHandled = true;
            performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
            mWindowManagerFuncs.shutdown(behavior == LONG_PRESS_POWER_SHUT_OFF);
            break;
        }
    }

    //add mohongwu@wind-mobi.com 2017/2/9 start
    private void asusPowerLongPress() {
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
        mWindowManagerFuncs.reboot(false);
    }
    //add mohongwu@wind-mobi.com 2017/2/9 end

    private void backLongPress() {
        mBackKeyHandled = true;

        switch (mLongPressOnBackBehavior) {
            case LONG_PRESS_BACK_NOTHING:
                break;
            case LONG_PRESS_BACK_GO_TO_VOICE_ASSIST:
                Intent intent = new Intent(Intent.ACTION_VOICE_ASSIST);
                startActivityAsUser(intent, UserHandle.CURRENT_OR_SELF);
                break;
        }
    }

    private void disposeInputConsumer(InputConsumer inputConsumer) {
        if (inputConsumer != null) {
            inputConsumer.dismiss();
        }
    }

    private void sleepPress(long eventTime) {
        if (mShortPressOnSleepBehavior == SHORT_PRESS_SLEEP_GO_TO_SLEEP_AND_GO_HOME) {
            launchHomeFromHotKey(false /* awakenDreams */, true /*respectKeyguard*/);
        }
    }

    private void sleepRelease(long eventTime) {
        switch (mShortPressOnSleepBehavior) {
            case SHORT_PRESS_SLEEP_GO_TO_SLEEP:
            case SHORT_PRESS_SLEEP_GO_TO_SLEEP_AND_GO_HOME:
                Slog.i(TAG, "sleepRelease() calling goToSleep(GO_TO_SLEEP_REASON_SLEEP_BUTTON)");
                mPowerManager.goToSleep(eventTime,
                       PowerManager.GO_TO_SLEEP_REASON_SLEEP_BUTTON, 0);
                break;
        }
    }

    private int getResolvedLongPressOnPowerBehavior() {
        if (FactoryTest.isLongPressOnPowerOffEnabled()) {
            return LONG_PRESS_POWER_SHUT_OFF_NO_CONFIRM;
        }
        return mLongPressOnPowerBehavior;
    }

    private boolean hasLongPressOnPowerBehavior() {
        return getResolvedLongPressOnPowerBehavior() != LONG_PRESS_POWER_NOTHING;
    }

    private boolean hasLongPressOnBackBehavior() {
        return mLongPressOnBackBehavior != LONG_PRESS_BACK_NOTHING;
    }

    private void interceptScreenshotChord() {
        if (mScreenshotChordEnabled
                && mScreenshotChordVolumeDownKeyTriggered && mScreenshotChordPowerKeyTriggered
                && !mScreenshotChordVolumeUpKeyTriggered) {
            final long now = SystemClock.uptimeMillis();
            if (now <= mScreenshotChordVolumeDownKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS
                    && now <= mScreenshotChordPowerKeyTime
                            + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS) {
                mScreenshotChordVolumeDownKeyConsumed = true;
                cancelPendingPowerKeyAction();
                mScreenshotRunnable.setScreenshotType(TAKE_SCREENSHOT_FULLSCREEN);
                mHandler.postDelayed(mScreenshotRunnable, getScreenshotChordLongPressDelay());
            }
        }
    }

    private long getScreenshotChordLongPressDelay() {
        if (mKeyguardDelegate.isShowing()) {
            // Double the time it takes to take a screenshot from the keyguard
            return (long) (KEYGUARD_SCREENSHOT_CHORD_DELAY_MULTIPLIER *
                    ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
        }
        return ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout();
    }

    private void cancelPendingScreenshotChordAction() {
        mHandler.removeCallbacks(mScreenshotRunnable);
    }

    private final Runnable mEndCallLongPress = new Runnable() {
        @Override
        public void run() {
            mEndCallKeyHandled = true;
            if (!performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false)) {
                performAuditoryFeedbackForAccessibilityIfNeed();
            }
            showGlobalActionsInternal();
        }
    };

    private class ScreenshotRunnable implements Runnable {
        private int mScreenshotType = TAKE_SCREENSHOT_FULLSCREEN;

        public void setScreenshotType(int screenshotType) {
            mScreenshotType = screenshotType;
        }

        @Override
        public void run() {
            //+++Chilin_Wang@asus.com, long-pressing switch key porting
            mSwitchKeyHandled = true;
            //---long-pressing switch key porting
            if (mSafeMode) {
                if (Build.FEATURES.ENABLE_LONG_SCREENSHOT) {
                    Log.i(TAG, "gauss-ScreenshotRunnable run");
                    takeLongScreenshot(mScreenshotType);
                } else {
                    takeScreenshot(mScreenshotType);
                }
            } else {
                try {
                    PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(STITCHIMAGE_SERVICE_PACKAGE_NAME, 0);
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(STITCHIMAGE_APP_PACKAGE_NAME, ACTION_START_STITCHIMAGE));
                    intent.putExtra(EXTRA_KEY_STITCHIMAGE_SETTINGS_CALLFROM, EXTRA_VALUE_STITCHIMAGE_SETTINGS_CALLFROM_ASUSSETTINGS);
                    mContext.startService(intent);
                    Log.i(TAG, "ScreenshotRunnable trigger stitchimage");
                } catch (Exception e) {
                    Log.i(TAG, "trigger stitchimage failed, take orginal screenshot, exception :" + e);
                    if (Build.FEATURES.ENABLE_LONG_SCREENSHOT) {
                        takeLongScreenshot(mScreenshotType);
                    } else {
                        takeScreenshot(mScreenshotType);
                    }
                }
            }
        }
    }

    private final ScreenshotRunnable mScreenshotRunnable = new ScreenshotRunnable();

    @Override
    public void showGlobalActions() {
        mHandler.removeMessages(MSG_DISPATCH_SHOW_GLOBAL_ACTIONS);
        mHandler.sendEmptyMessage(MSG_DISPATCH_SHOW_GLOBAL_ACTIONS);
    }

    void showGlobalActionsInternal() {
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
        if (mGlobalActions == null) {
            mGlobalActions = new GlobalActions(mContext, mWindowManagerFuncs);
        }
        final boolean keyguardShowing = isKeyguardShowingAndNotOccluded();
        // [Vizon] {
        final boolean isVZWSKU = SystemProperties.getInt("ro.asus.is_verizon_device", 0) == 1;
        if (isVZWSKU) {
            final boolean isKeyguardShowingOrOccluded = isKeyguardShowingOrOccluded();
            final boolean keyguardSecure = isKeyguardSecure(mCurrentUserId);
            mGlobalActions.showDialog(keyguardShowing, isDeviceProvisioned(), keyguardSecure, isKeyguardShowingOrOccluded);
        } else {
            // [Vizon] }
            mGlobalActions.showDialog(keyguardShowing, isDeviceProvisioned());
            // [Vizon] {
        }
        // [Vizon] }
        if (keyguardShowing) {
            // since it took two seconds of long press to bring this up,
            // poke the wake lock so they have some time to see the dialog.
            mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
        }
    }

    boolean isDeviceProvisioned() {
        return Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    boolean isUserSetupComplete() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0, UserHandle.USER_CURRENT) != 0;
    }
	
	// Add by gaohui@wind-mobi.com 20161107 start to disable home key when enrolling
    private boolean fingerprintOn() {
        FingerprintManager fm = (FingerprintManager)
                mContext.getSystemService(Context.FINGERPRINT_SERVICE);
        if (fm == null) {
            Slog.w(TAG, "FingerprintManager not found.");
            return false;
        }
        return fm.isEnrolling();
    }
    // Add by gaohui@wind-mobi.com 20161107 end to disable home key when enrolling

    private void handleShortPressOnHome() {
        // Turn on the connected TV and switch HDMI input if we're a HDMI playback device.
        getHdmiControl().turnOnTv();

        // If there's a dream running then use home to escape the dream
        // but don't actually go home.
        if (mDreamManagerInternal != null && mDreamManagerInternal.isDreaming()) {
            mDreamManagerInternal.stopDream(false /*immediate*/);
            return;
        }

        // Go home!
        launchHomeFromHotKey();
    }

    /**
     * Creates an accessor to HDMI control service that performs the operation of
     * turning on TV (optional) and switching input to us. If HDMI control service
     * is not available or we're not a HDMI playback device, the operation is no-op.
     */
    private HdmiControl getHdmiControl() {
        if (null == mHdmiControl) {
            HdmiControlManager manager = (HdmiControlManager) mContext.getSystemService(
                        Context.HDMI_CONTROL_SERVICE);
            HdmiPlaybackClient client = null;
            if (manager != null) {
                client = manager.getPlaybackClient();
            }
            mHdmiControl = new HdmiControl(client);
        }
        return mHdmiControl;
    }

    private static class HdmiControl {
        private final HdmiPlaybackClient mClient;

        private HdmiControl(HdmiPlaybackClient client) {
            mClient = client;
        }

        public void turnOnTv() {
            if (mClient == null) {
                return;
            }
            mClient.oneTouchPlay(new OneTouchPlayCallback() {
                @Override
                public void onComplete(int result) {
                    if (result != HdmiControlManager.RESULT_SUCCESS) {
                        Log.w(TAG, "One touch play failed: " + result);
                    }
                }
            });
        }
    }

    private void handleLongPressOnHome(int deviceId) {
        if (mLongPressOnHomeBehavior == LONG_PRESS_HOME_NOTHING) {
            return;
        }
        mHomeConsumed = true;
        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);

        switch (mLongPressOnHomeBehavior) {
            case LONG_PRESS_HOME_RECENT_SYSTEM_UI:
                if (mIsCNSku && mIsFpNavigationKeysEnabled) {
                    takeScreenshot(TAKE_SCREENSHOT_FULLSCREEN);
                } else {
                    toggleRecentApps();
                }
                break;
            case LONG_PRESS_HOME_ASSIST:
                if (mIsCNSku && mIsFpNavigationKeysEnabled) {
                    takeScreenshot(TAKE_SCREENSHOT_FULLSCREEN);
                } else {
                    launchAssistAction(null, deviceId);
                }
                break;
            default:
                Log.w(TAG, "Undefined home long press behavior: " + mLongPressOnHomeBehavior);
                break;
        }
        
        //BEGIN : roy_huang@asus.com
        if (mIsCNSku && mIsFpNavigationKeysEnabled) {
            mLongPressOnHome = true;
            mHandler.postDelayed(mLongPressHomeTimeoutRunnable,
                    1000L);
        }
        //END : roy_huang@asus.com
    }

    private void handleDoubleTapOnHome() {
        if (mDoubleTapOnHomeBehavior == DOUBLE_TAP_HOME_RECENT_SYSTEM_UI) {
            mHomeConsumed = true;
            toggleRecentApps();
        }
        // BEGIN leo_liao@asus.com, One-hand control
        else if (mDoubleTapOnHomeBehavior == DOUBLE_TAP_HOME_ONEHAND_CTRL
                && isUserSetupComplete()) {
            mHomeConsumed = true;
            mHandler.removeCallbacks(mActivateOneHandCtrlRunnable);
            mHandler.postDelayed(mActivateOneHandCtrlRunnable,
                    ViewConfiguration.getDoubleTapTimeout());
        }
        // END leo_liao@asus.com
        
        //BEGIN : roy_huang@asus.com
        else if (mDoubleTapOnHomeBehavior == DOUBLE_TAP_HOME_TARGET_APP) {
            mHomeConsumed = true;
            // CombineKeyListener decides which app to be triggered.
        }
        //END : roy_huang@asus.com
        
    }

    private void showTvPictureInPictureMenu(KeyEvent event) {
        if (DEBUG_INPUT) Log.d(TAG, "showTvPictureInPictureMenu event=" + event);
        mHandler.removeMessages(MSG_SHOW_TV_PICTURE_IN_PICTURE_MENU);
        Message msg = mHandler.obtainMessage(MSG_SHOW_TV_PICTURE_IN_PICTURE_MENU);
        msg.setAsynchronous(true);
        msg.sendToTarget();
    }

    private void showTvPictureInPictureMenuInternal() {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.showTvPictureInPictureMenu();
        }
    }

    private final Runnable mHomeDoubleTapTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mHomeDoubleTapPending) {
                mHomeDoubleTapPending = false;
                handleShortPressOnHome();
            }
        }
    };

    //BEGIN : roy_huang@asus.com
    private final Runnable mShortPressHomeTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mShortPressOnHome) {
                mShortPressOnHome = false;
            }
        }
    };
    
    private final Runnable mLongPressHomeTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mLongPressOnHome) {
                mLongPressOnHome = false;
            }
        }
    };
    
    private final Runnable mDoublePressHomeTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDoublePressOnHome) {
                mDoublePressOnHome = false;
            }
        }
    };
    
    private void updateHomeDoubleTapTargetApp(String appInfo) {
        if (!"".equals(appInfo) && appInfo != null) {
            int index = appInfo.indexOf("/");
            if(index >= 0) {
                mHomeDoubleTapTargetAppPackage = appInfo.substring(0, index);
                mHomeDoubleTapTargetAppClass = appInfo.substring(index+1);
            }
           
        }
    }
    
    private void luanchHomeDoubleTapTargetApp(boolean interactive) {
        if (mIsCNSku && mIsFpNavigationKeysEnabled) {
            boolean keyguardActive = (mKeyguardDelegate == null ? false :
                    (interactive ?
                            isKeyguardShowingAndNotOccluded() :
                            mKeyguardDelegate.isShowing()));
            Slog.i(TAG,"launchHomeDoubleTapTargetApp iteractive = "+interactive+", keyguardActive "+keyguardActive);
            Slog.i(TAG,"launchHomeDoubleTapTargetApp isKeyguardShowingAndNotOccluded = "+
                    isKeyguardShowingAndNotOccluded()+ " ,isShowing:"+mKeyguardDelegate.isShowing());
            if (interactive && !keyguardActive) {
                toggleRecentApps();
                Log.d(TAG, "launchHomeDoubleTapTargetApp trigger toggleRecentApps");
            } else {
                Slog.i(TAG,"launchHomeDoubleTapTargetApp prepare start app");
                if (mIsNavigationPaymentEnabled) {
                    if (!"".equals(mHomeDoubleTapTargetAppPackage) && !"".equals(mHomeDoubleTapTargetAppClass)) {
                        Intent targetIntent = new Intent();
                        targetIntent.setComponent(new ComponentName(mHomeDoubleTapTargetAppPackage, mHomeDoubleTapTargetAppClass))
                                .setAction(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_DEFAULT);
                        try {
                            final UserHandle user = new UserHandle(UserHandle.USER_CURRENT);
                            mContext.startActivityAsUser(targetIntent, user);
                        } catch (Exception e) {
                            Log.d(TAG, "start app exception : " + e);
                        }
                        Log.d(TAG, "start target app : " + mHomeDoubleTapTargetAppPackage + " ; " + mHomeDoubleTapTargetAppClass);
                    } else {
                        Log.d(TAG, "no payment app information.");
                    }
                } else {
                    Log.d(TAG, "navigation payment disabled.");
                }
            }
        }
    }
    private boolean isAllowTakeScreenshotFromFPLongPress() {
        String pkg = getCurrentFocusPackageName();
        Log.d(TAG, "Current focus app is " + pkg);
        if ("com.asus.asusincallui".equals(pkg)) {
            return false;
        }
        return true;
    }
    //END : roy_huang@asus.com
    
    
    private boolean isRoundWindow() {
        return mContext.getResources().getConfiguration().isScreenRound();
    }

    /** {@inheritDoc} */
    @Override
    public void init(Context context, IWindowManager windowManager,
            WindowManagerFuncs windowManagerFuncs) {
        mContext = context;
        mWindowManager = windowManager;
        mWindowManagerFuncs = windowManagerFuncs;
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
        mInputManagerInternal = LocalServices.getService(InputManagerInternal.class);
        mDreamManagerInternal = LocalServices.getService(DreamManagerInternal.class);
        mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);
        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        mHasFeatureWatch = mContext.getPackageManager().hasSystemFeature(FEATURE_WATCH);
        
        //BEGIN : roy_huang@asus.com
        // wangyan@wind-mobi.com modify 2017/09/07 start
        mIsCNSku = false;
        // wangyan@wind-mobi.com modify 2017/09/07 start
        /*mIsCNSku = (Build.PRODUCT.startsWith("CN") || Build.PRODUCT.startsWith("cn")) ? true : false;*/
        //END : roy_huang@asus.com
        
        //dongjiangpeng@wind-mobi.com add 2016/12/21 start
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mProximityMotion = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        //dongjiangpeng@wind-mobi.com add 2016/12/21 end
        /*wangchaobin@wind-mobi.com added  2017.01.10 for new feature begin*/
        mProximityThreshold = Math.min(mProximityMotion.getMaximumRange(),
                TYPICAL_PROXIMITY_THRESHOLD);
        /*wangchaobin@wind-mobi.com added  2017.01.10 for new feature end*/

        //++add gaohui@wind-mobi.com for usb_ntc begin
        mNotificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        //--
        // Init display burn-in protection
        boolean burnInProtectionEnabled = context.getResources().getBoolean(
                com.android.internal.R.bool.config_enableBurnInProtection);
        // Allow a system property to override this. Used by developer settings.
        boolean burnInProtectionDevMode =
                SystemProperties.getBoolean("persist.debug.force_burn_in", false);
        if (burnInProtectionEnabled || burnInProtectionDevMode) {
            final int minHorizontal;
            final int maxHorizontal;
            final int minVertical;
            final int maxVertical;
            final int maxRadius;
            if (burnInProtectionDevMode) {
                minHorizontal = -8;
                maxHorizontal = 8;
                minVertical = -8;
                maxVertical = -4;
                maxRadius = (isRoundWindow()) ? 6 : -1;
            } else {
                Resources resources = context.getResources();
                minHorizontal = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMinHorizontalOffset);
                maxHorizontal = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMaxHorizontalOffset);
                minVertical = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMinVerticalOffset);
                maxVertical = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMaxVerticalOffset);
                maxRadius = resources.getInteger(
                        com.android.internal.R.integer.config_burnInProtectionMaxRadius);
            }
            mBurnInProtectionHelper = new BurnInProtectionHelper(
                    context, minHorizontal, maxHorizontal, minVertical, maxVertical, maxRadius);
        }

        mHandler = new PolicyHandler();
        mWakeGestureListener = new MyWakeGestureListener(mContext, mHandler);
        mOrientationListener = new MyOrientationListener(mContext, mHandler);
        try {
            mOrientationListener.setCurrentRotation(windowManager.getRotation());
        } catch (RemoteException ex) { }
        //+++ jeson_li for filpcover
        PackageManager coverpm = mContext.getPackageManager();
        mHasTranscoverFeature = coverpm.hasSystemFeature(FEATURE_ASUS_TRANSCOVER);
        android.os.Build.FEATURES.HAS_TRANSCOVER=mHasTranscoverFeature;
        Log.d(TAG, "Build.FEATURES.HAS_TRANSCOVER:"+android.os.Build.FEATURES.HAS_TRANSCOVER);
        if(android.os.Build.FEATURES.HAS_TRANSCOVER){
            mHasTranscoverInfoFeature = coverpm.hasSystemFeature(FEATURE_ASUS_TRANSCOVER_INFO);
            mCoverUserManager= (android.os.UserManager) mContext.getSystemService(Context.USER_SERVICE);
        }
        //--- jeson_li for filpcover
        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
        mShortcutManager = new ShortcutManager(context);
        mUiMode = context.getResources().getInteger(
                com.android.internal.R.integer.config_defaultUiModeType);
        mHomeIntent =  new Intent(Intent.ACTION_MAIN, null);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
        mHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mEnableCarDockHomeCapture = context.getResources().getBoolean(
                com.android.internal.R.bool.config_enableCarDockHomeLaunch);
        mCarDockIntent =  new Intent(Intent.ACTION_MAIN, null);
        mCarDockIntent.addCategory(Intent.CATEGORY_CAR_DOCK);
        mCarDockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mDeskDockIntent =  new Intent(Intent.ACTION_MAIN, null);
        mDeskDockIntent.addCategory(Intent.CATEGORY_DESK_DOCK);
        mDeskDockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        mPowerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mBroadcastWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PhoneWindowManager.mBroadcastWakeLock");
        mPowerKeyWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PhoneWindowManager.mPowerKeyWakeLock");
        mEnableShiftMenuBugReports = "1".equals(SystemProperties.get("ro.debuggable"));
        mSupportAutoRotation = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_supportAutoRotation);
        mLidOpenRotation = readRotation(
                com.android.internal.R.integer.config_lidOpenRotation);
        mCarDockRotation = readRotation(
                com.android.internal.R.integer.config_carDockRotation);
        mDeskDockRotation = readRotation(
                com.android.internal.R.integer.config_deskDockRotation);
        mUndockedHdmiRotation = readRotation(
                com.android.internal.R.integer.config_undockedHdmiRotation);
        mCarDockEnablesAccelerometer = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_carDockEnablesAccelerometer);
        mDeskDockEnablesAccelerometer = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_deskDockEnablesAccelerometer);
        mLidKeyboardAccessibility = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lidKeyboardAccessibility);
        mLidNavigationAccessibility = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lidNavigationAccessibility);
        mLidControlsScreenLock = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_lidControlsScreenLock);
        mLidControlsSleep = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_lidControlsSleep);
        mTranslucentDecorEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableTranslucentDecor);

        mAllowTheaterModeWakeFromKey = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromKey);
        mAllowTheaterModeWakeFromPowerKey = mAllowTheaterModeWakeFromKey
                || mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_allowTheaterModeWakeFromPowerKey);
        mAllowTheaterModeWakeFromMotion = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromMotion);
        mAllowTheaterModeWakeFromMotionWhenNotDreaming = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromMotionWhenNotDreaming);
        mAllowTheaterModeWakeFromCameraLens = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromCameraLens);
        mAllowTheaterModeWakeFromLidSwitch = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromLidSwitch);
        mAllowTheaterModeWakeFromWakeGesture = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromGesture);

        mGoToSleepOnButtonPressTheaterMode = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_goToSleepOnButtonPressTheaterMode);

        mSupportLongPressPowerWhenNonInteractive = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_supportLongPressPowerWhenNonInteractive);

        mLongPressOnBackBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_longPressOnBackBehavior);

        mShortPressOnPowerBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_shortPressOnPowerBehavior);
        mLongPressOnPowerBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_longPressOnPowerBehavior);
        mDoublePressOnPowerBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_doublePressOnPowerBehavior);
        mTriplePressOnPowerBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_triplePressOnPowerBehavior);
        mShortPressOnSleepBehavior = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_shortPressOnSleepBehavior);

        mUseTvRouting = AudioSystem.getPlatformType(mContext) == AudioSystem.PLATFORM_TELEVISION;

        readConfigurationDependentBehaviors();

        mAccessibilityManager = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);

        // register for dock events
        IntentFilter filter = new IntentFilter();
        filter.addAction(UiModeManager.ACTION_ENTER_CAR_MODE);
        filter.addAction(UiModeManager.ACTION_EXIT_CAR_MODE);
        filter.addAction(UiModeManager.ACTION_ENTER_DESK_MODE);
        filter.addAction(UiModeManager.ACTION_EXIT_DESK_MODE);
        filter.addAction(Intent.ACTION_DOCK_EVENT);
        Intent intent = context.registerReceiver(mDockReceiver, filter);
        if (intent != null) {
            // Retrieve current sticky dock event broadcast.
            mDockMode = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                    Intent.EXTRA_DOCK_STATE_UNDOCKED);
        }

        /// M: register for oma events @{
        IntentFilter ipoEventFilter = new IntentFilter();
        ipoEventFilter.addAction(IPO_ENABLE);
        ipoEventFilter.addAction(IPO_DISABLE);
        context.registerReceiver(mIpoEventReceiver, ipoEventFilter);
        /// @}

        ///M: register for power-off alarm shutDown @{
        IntentFilter poweroffAlarmFilter = new IntentFilter();
        poweroffAlarmFilter.addAction(NORMAL_SHUTDOWN_ACTION);
        poweroffAlarmFilter.addAction(NORMAL_BOOT_ACTION);
        context.registerReceiver(mPoweroffAlarmReceiver, poweroffAlarmFilter);
        /// @}

        /// M: [ALPS00062902]register for stk  events @{
        IntentFilter stkUserActivityFilter = new IntentFilter();
        stkUserActivityFilter.addAction(STK_USERACTIVITY_ENABLE);
        context.registerReceiver(mStkUserActivityEnReceiver, stkUserActivityFilter);
        /// @}

        // register for dream-related broadcasts
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        context.registerReceiver(mDreamReceiver, filter);

        // register for multiuser-relevant broadcasts
        filter = new IntentFilter(Intent.ACTION_USER_SWITCHED);
        context.registerReceiver(mMultiuserReceiver, filter);

        /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 begin
        if (WIND_DEF_OTG_REVERSE) {
            IntentFilter mBatteryForOtgCtrlFilter = new IntentFilter();
            mBatteryForOtgCtrlFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
            mBatteryForOtgCtrlFilter.addAction(Intent.ACTION_SCREEN_ON);
            mBatteryForOtgCtrlFilter.addAction(Intent.ACTION_SCREEN_OFF);
            context.registerReceiver(mBatteryForOtgCtrlBroadcastReceiver, mBatteryForOtgCtrlFilter);
        }
        /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 end

        //++gaohui@wind-mobi.com add for usb_ntc 20170323 begin
        if(WIND_DEF_USB_NTC) {
            IntentFilter mBatteryForUsbNtcFilter = new IntentFilter();
            mBatteryForUsbNtcFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(mBatteryForUsbNtclBroadcastReceiver, mBatteryForUsbNtcFilter);
        }
        //--gaohui@wind-mobi.com add for usb_ntc 20170323 end

        //BEGIN : roy_huang@asus.com
        //register for taking screen-shot broadcasts
        filter = new IntentFilter("ACTION_APP_TAKE_SCREENSHOT");
        filter.addCategory("asus.category.broadcast.APPTAKESCREENSHOT");
        context.registerReceiver(mScreenShotReceiver, filter);
        //END : roy_huang@asus.com

        //BEGIN: Chilin_Wang@asus.com, register for game genie lock mode broadcast
        mNotifyGameGenieLockModeRunnable = new NotifyGameGenieLockModeRunnable(mContext);
        filter = new IntentFilter();
        filter.addAction("com.asus.gamewidget.app.SET_LOCK_MODE_LOCK");
        filter.addAction("com.asus.gamewidget.app.SET_LOCK_MODE_UNLOCK");
        filter.addAction("com.asus.gamewidget.app.STOP");
        context.registerReceiver(mGameGenieLockModeReceiver, filter);
        SystemProperties.set("persist.asus.gamegenie_lock", "1");
        //END: Chilin_Wang@asus.com

        // monitor for system gestures
        mSystemGestures = new SystemGesturesPointerEventListener(context,
                new SystemGesturesPointerEventListener.Callbacks() {
                    @Override
                    public void onSwipeFromTop() {
                        /// M: Disable gesture in immersive mode. {@
                        if (isGestureIsolated()) {
                            return;
                        }
                        /// @}
                        if (mStatusBar != null) {
                            requestTransientBars(mStatusBar);
                        }
                    }
                    @Override
                    public void onSwipeFromBottom() {
                        /// M: Disable gesture in immersive mode. {@
                        if (isGestureIsolated()) {
                            return;
                        }
                        /// @}
                        if (mNavigationBar != null && mNavigationBarPosition == NAV_BAR_BOTTOM) {
                            requestTransientBars(mNavigationBar);
                        }
                    }
                    @Override
                    public void onSwipeFromRight() {
                        /// M: Disable gesture in immersive mode. {@
                        if (isGestureIsolated()) {
                            return;
                        }
                        /// @}
                        if (mNavigationBar != null && mNavigationBarPosition == NAV_BAR_RIGHT) {
                            requestTransientBars(mNavigationBar);
                        }
                    }
                    @Override
                    public void onSwipeFromLeft() {
                        /// M: Disable gesture in immersive mode. {@
                        if (isGestureIsolated()) {
                            return;
                        }
                        /// @}
                        if (mNavigationBar != null && mNavigationBarPosition == NAV_BAR_LEFT) {
                            requestTransientBars(mNavigationBar);
                        }
                    }
                    @Override
                    public void onFling(int duration) {
                        if (mPowerManagerInternal != null) {
                            mPowerManagerInternal.powerHint(
                                    PowerManagerInternal.POWER_HINT_INTERACTION, duration);
                        }
                    }
                    @Override
                    public void onDebug() {
                        // no-op
                    }
                    @Override
                    public void onDown() {
                        mOrientationListener.onTouchStart();
                    }
                    @Override
                    public void onUpOrCancel() {
                        mOrientationListener.onTouchEnd();
                    }
                    @Override
                    public void onMouseHoverAtTop() {
                        /// M: Disable gesture in immersive mode. {@
                        if (isGestureIsolated()) {
                            return;
                        }
                        /// @}
                        mHandler.removeMessages(MSG_REQUEST_TRANSIENT_BARS);
                        Message msg = mHandler.obtainMessage(MSG_REQUEST_TRANSIENT_BARS);
                        msg.arg1 = MSG_REQUEST_TRANSIENT_BARS_ARG_STATUS;
                        mHandler.sendMessageDelayed(msg, 500);
                    }
                    @Override
                    public void onMouseHoverAtBottom() {
                        /// M: Disable gesture in immersive mode. {@
                        if (isGestureIsolated()) {
                            return;
                        }
                        /// @}
                        mHandler.removeMessages(MSG_REQUEST_TRANSIENT_BARS);
                        Message msg = mHandler.obtainMessage(MSG_REQUEST_TRANSIENT_BARS);
                        msg.arg1 = MSG_REQUEST_TRANSIENT_BARS_ARG_NAVIGATION;
                        mHandler.sendMessageDelayed(msg, 500);
                    }
                    @Override
                    public void onMouseLeaveFromEdge() {
                        mHandler.removeMessages(MSG_REQUEST_TRANSIENT_BARS);
                    }
                    /// M: Disable gesture in immersive mode. {@
                    private boolean isGestureIsolated() {
                        WindowState win = mFocusedWindow != null
                                ? mFocusedWindow : mTopFullscreenOpaqueWindowState;
                        if (win != null
                            && (win.getSystemUiVisibility()
                            & View.SYSTEM_UI_FLAG_IMMERSIVE_GESTURE_ISOLATED) != 0) {
                            return true;
                        } else {
                            return false;
                        }
                    }
                    /// @}
                });
        mImmersiveModeConfirmation = new ImmersiveModeConfirmation(mContext);
        mWindowManagerFuncs.registerPointerEventListener(mSystemGestures);

        mVibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        mLongPressVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_longPressVibePattern);
        mVirtualKeyVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_virtualKeyVibePattern);
        mKeyboardTapVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_keyboardTapVibePattern);
        mClockTickVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_clockTickVibePattern);
        mCalendarDateVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_calendarDateVibePattern);
        mSafeModeDisabledVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_safeModeDisabledVibePattern);
        mSafeModeEnabledVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_safeModeEnabledVibePattern);
        mContextClickVibePattern = getLongIntArray(mContext.getResources(),
                com.android.internal.R.array.config_contextClickVibePattern);

        mScreenshotChordEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableScreenshotChord);

        mGlobalKeyManager = new GlobalKeyManager(mContext);

        // Controls rotation and the like.
        initializeHdmiState();

        // Match current screen state.
        if (!mPowerManager.isInteractive()) {
            startedGoingToSleep(WindowManagerPolicy.OFF_BECAUSE_OF_USER);
            finishedGoingToSleep(WindowManagerPolicy.OFF_BECAUSE_OF_USER);
        }


        //+++  Yuchen_Chang
        mHasDockFeature = mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_ASUS_DOCK);
        if (mHasDockFeature) {
            mDockMode = readDockState();
        }
        mHasHallSensorFeature = mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_ASUS_HALL_SENSOR);
        mHasKeyboardFeature = mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_ASUS_KEYBOARD);

        android.os.SystemProperties.set("persist.asus.cover.dbwake", String.valueOf(android.os.Build.VERSION.SDK_INT));

        /*
         * If the device has hall sensor and keyboard feature but not have dock feature (ex SL101)
         * reset the mLidKeyboardAccessibility to default value 1
         */
        if (mHasHallSensorFeature && mHasKeyboardFeature && !mHasDockFeature) {
            mLidKeyboardAccessibility = 1;
        }

        /*
         * If the device has hall sensor but not have keyboard and dock feature (ex ME570T)
         * reset the mLidOpenRotation to default value -1
         */
        if (mHasHallSensorFeature && !mHasKeyboardFeature && !mHasDockFeature) {
            mLidOpenRotation = -1;
        }
        //---

        mWindowManagerInternal.registerAppTransitionListener(
                mStatusBarController.getAppTransitionListener());
				
        /// M: add for fullscreen switch feature @{
        if ("1".equals(SystemProperties.get("ro.mtk_fullscreen_switch"))) {
            mSupportFullscreenSwitch = true;
        }
        /// @}
		
        //+++jeson_li for cover
        if(android.os.Build.FEATURES.HAS_TRANSCOVER){//with cover
            mIsTranscoverEnabledLastForCover=mIsTranscoverEnabled;
            mCurrentUserIdLastForCover=mCurrentUserId;
            mTranscoverAutomaticUnlockLastForCover=mTranscoverAutomaticUnlock;
            if(mHasHallSensorFeature){//with hall sensor
                if(mHasTranscoverInfoFeature){//cover with hole
                    mLidControlsSleep=false;
                }else{//cover without hole
                    mLidControlsSleep=true;
                }
            }else{//without hall sensor
                mLidControlsSleep=false;
            }
        }else{
            //without cover
            mLidControlsSleep=mHasHallSensorFeature;
        }
        if(mHandler!=null){//send msg (avoid dead lock) for hallsensor to enable/disable irq (fix TT-701354)
            mHandler.removeMessages(MSG_FLIPCOVER_SET_PROP_FOR_HALL_SENSOR);
            mHandler.sendEmptyMessage(MSG_FLIPCOVER_SET_PROP_FOR_HALL_SENSOR);
        }
        //---jeson_li for cover
        mCombineKeyDetector= new CombineKeyDetector(mHandler);
        mCombineKeyDetector.setOnCombineKeyListener(mCombineKeyListener);

        //BEGIN: Chilin_Wang@asus.com, find long press camera hardware feature N-porting
        mSupportShutterOrRecordKeyDevice = mContext.getPackageManager().
                hasSystemFeature(PackageManager.FEATURE_ASUS_LONGPRESS_CAMERA);

        //triple tap powerkey to make emergency call
        mEmergencyAffordanceManager = new EmergencyAffordanceManager(mContext);
        mEmergencyCallWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "performEmergencyCallWakeLock");
        //END: Chilin_Wang@asus.com
		//+++  Yuchen_Chang
        mHasFeatureVR = mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE);
        //---  Yuchen_Chang
        // Support for update navigation bar +++++++++
        if(isCNSku()) {
            mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(ENABLE_NAVIGATION_KEYS), false,
                mNavigationBarObserver);
        }
        //Support for update navigation bar ---------
        
          //gaohui@wind-mobi remove for asus 20170306 patch begin
         //BEGIN : roy_huang@asus.com
         //mIsCNSku = (Build.PRODUCT.startsWith("CN") || Build.PRODUCT.startsWith("cn")) ? true : false || (Build.PRODUCT.startsWith("CTCC"));
         //END : roy_huang@asus.com
         //gaohui@wind-mobi remove for asus 20170306 patch end


        //++gaohui@wind-mobi.com add for usb_ntc begin
        if(WIND_DEF_USB_NTC) {
            mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(ENABLED_USB_NTC_NOTIFICATION), false, mEnableUsbNtcObserver);
        }
        //--gaohui@wind-mobi.com add for usb_ntc end
    }

    private static final String ENABLED_USB_NTC_NOTIFICATION = "enable_usb_ntc_notification";
    private static final int MSG_CANCEL_NOTIFICATION = 0x123;
    private static final int USB_NTC_NOTICATION_ID = 0x321;
    private ContentObserver mEnableUsbNtcObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            //Log.d("gaohui","mEnableUsbNtcObserver onChange called");
            updateUsbNtcNotification();
        }
    };

    private void updateUsbNtcNotification() {
        int enabledUsbNtc = Settings.Global.getInt(mContext.getContentResolver(), ENABLED_USB_NTC_NOTIFICATION, 0);
        //Log.d("gaohui","updateUsbNtcNotification enabledUsbNtc:" + enabledUsbNtc);
        if(enabledUsbNtc == 1) {
            mHandler.sendEmptyMessage(MSG_CANCEL_NOTIFICATION);
        }
    }

    //Support for update navigation bar ++++++++++
    private static final String ENABLE_NAVIGATION_KEYS = "enable_navigation_keys";
    private ContentObserver mNavigationBarObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateNavigationBar();
        }
    };

    private void updateNavigationBar() {
        String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
        if(Settings.Global.getInt(mContext.getContentResolver(), ENABLE_NAVIGATION_KEYS, 0) == 0) {
            if ("1".equals(navBarOverride)) {
                mHasNavigationBar = false;
            } else if ("0".equals(navBarOverride)) {
                mHasNavigationBar = true;
            }
        } else {
            mHasNavigationBar = false;
        }
        Log.d(TAG,"updateNavigationBar mHasNavigationBar:"+mHasNavigationBar);
    }

    private boolean isCNSku() {
        return SystemProperties.get("ro.build.asus.sku","").equals("CN");
    }
    //Support for update navigation bar ---------

    /**
     * Read values from config.xml that may be overridden depending on
     * the configuration of the device.
     * eg. Disable long press on home goes to recents on sw600dp.
     */
    private void readConfigurationDependentBehaviors() {
        final Resources res = mContext.getResources();

        mLongPressOnHomeBehavior = res.getInteger(
                com.android.internal.R.integer.config_longPressOnHomeBehavior);
        if (mLongPressOnHomeBehavior < LONG_PRESS_HOME_NOTHING ||
                mLongPressOnHomeBehavior > LAST_LONG_PRESS_HOME_BEHAVIOR) {
            mLongPressOnHomeBehavior = LONG_PRESS_HOME_NOTHING;
        }

        mDoubleTapOnHomeBehavior = res.getInteger(
                com.android.internal.R.integer.config_doubleTapOnHomeBehavior);
        if (mDoubleTapOnHomeBehavior < DOUBLE_TAP_HOME_NOTHING ||
                mDoubleTapOnHomeBehavior > DOUBLE_TAP_HOME_RECENT_SYSTEM_UI) {
            mDoubleTapOnHomeBehavior = LONG_PRESS_HOME_NOTHING;
        }

        mShortPressWindowBehavior = SHORT_PRESS_WINDOW_NOTHING;
        if (mContext.getPackageManager().hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)) {
            mShortPressWindowBehavior = SHORT_PRESS_WINDOW_PICTURE_IN_PICTURE;
        }

        mNavBarOpacityMode = res.getInteger(
                com.android.internal.R.integer.config_navBarOpacityMode);

        //gaohui@wind-mobi remove for asus patch 20170306 begin
        // BEGIN leo_liao@asus.com, One-hand control
        //readOneHandCtrlConfigurationDependentBehaviors();
        // END leo_liao@asus.com
        //gaohui@wind-mobi remove for asus patch 20170306 end
        
        //BEGIN : roy_huang@asus.com
        if (mIsCNSku) {
            mDoubleTapOnHomeBehavior = DOUBLE_TAP_HOME_TARGET_APP;
        } else {
            // BEGIN leo_liao@asus.com, One-hand control
            readOneHandCtrlConfigurationDependentBehaviors();
            // END leo_liao@asus.com
        }
        //END : roy_huang@asus.com
    }

    @Override
    public void setInitialDisplaySize(Display display, int width, int height, int density) {
        // This method might be called before the policy has been fully initialized
        // or for other displays we don't care about.
        if (mContext == null || display.getDisplayId() != Display.DEFAULT_DISPLAY) {
            return;
        }
        mDisplay = display;

        final Resources res = mContext.getResources();
        int shortSize, longSize;
        if (width > height) {
            shortSize = height;
            longSize = width;
            mLandscapeRotation = Surface.ROTATION_0;
            mSeascapeRotation = Surface.ROTATION_180;
            if (res.getBoolean(com.android.internal.R.bool.config_reverseDefaultRotation)) {
                mPortraitRotation = Surface.ROTATION_90;
                mUpsideDownRotation = Surface.ROTATION_270;
            } else {
                mPortraitRotation = Surface.ROTATION_270;
                mUpsideDownRotation = Surface.ROTATION_90;
            }
        } else {
            shortSize = width;
            longSize = height;
            mPortraitRotation = Surface.ROTATION_0;
            mUpsideDownRotation = Surface.ROTATION_180;
            if (res.getBoolean(com.android.internal.R.bool.config_reverseDefaultRotation)) {
                mLandscapeRotation = Surface.ROTATION_270;
                mSeascapeRotation = Surface.ROTATION_90;
            } else {
                mLandscapeRotation = Surface.ROTATION_90;
                mSeascapeRotation = Surface.ROTATION_270;
            }
        }

        // SystemUI (status bar) layout policy
        int shortSizeDp = shortSize * DisplayMetrics.DENSITY_DEFAULT / density;
        int longSizeDp = longSize * DisplayMetrics.DENSITY_DEFAULT / density;

        // Allow the navigation bar to move on non-square small devices (phones).
        mNavigationBarCanMove = width != height && shortSizeDp < 600;

        mHasNavigationBar = res.getBoolean(com.android.internal.R.bool.config_showNavigationBar);

        // Allow a system property to override this. Used by the emulator.
        // See also hasNavigationBar().
        String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
        //Support for update navigation bar ++++++++++
        if(isCNSku()) {
            updateNavigationBar();
        } else {
            if ("1".equals(navBarOverride)) {
                mHasNavigationBar = false;
            } else if ("0".equals(navBarOverride)) {
                mHasNavigationBar = true;
            }
		}
        //Support for update navigation bar ----------

        // For demo purposes, allow the rotation of the HDMI display to be controlled.
        // By default, HDMI locks rotation to landscape.
        if ("portrait".equals(SystemProperties.get("persist.demo.hdmirotation"))) {
            mDemoHdmiRotation = mPortraitRotation;
        } else {
            mDemoHdmiRotation = mLandscapeRotation;
        }
        mDemoHdmiRotationLock = SystemProperties.getBoolean("persist.demo.hdmirotationlock", false);

        // For demo purposes, allow the rotation of the remote display to be controlled.
        // By default, remote display locks rotation to landscape.
        if ("portrait".equals(SystemProperties.get("persist.demo.remoterotation"))) {
            mDemoRotation = mPortraitRotation;
        } else {
            mDemoRotation = mLandscapeRotation;
        }
        mDemoRotationLock = SystemProperties.getBoolean(
                "persist.demo.rotationlock", false);

        // Only force the default orientation if the screen is xlarge, at least 960dp x 720dp, per
        // http://developer.android.com/guide/practices/screens_support.html#range
        mForceDefaultOrientation = longSizeDp >= 960 && shortSizeDp >= 720 &&
                res.getBoolean(com.android.internal.R.bool.config_forceDefaultOrientation) &&
                // For debug purposes the next line turns this feature off with:
                // $ adb shell setprop config.override_forced_orient true
                // $ adb shell wm size reset
                !"true".equals(SystemProperties.get("config.override_forced_orient"));
    }

    /**
     * @return whether the navigation bar can be hidden, e.g. the device has a
     *         navigation bar and touch exploration is not enabled
     */
    private boolean canHideNavigationBar() {
        return mHasNavigationBar;
    }

    @Override
    public boolean isDefaultOrientationForced() {
        return mForceDefaultOrientation;
    }

    @Override
    public void setDisplayOverscan(Display display, int left, int top, int right, int bottom) {
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            mOverscanLeft = left;
            mOverscanTop = top;
            mOverscanRight = right;
            mOverscanBottom = bottom;
        }
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        boolean updateRotation = false;
        synchronized (mLock) {
            mEndcallBehavior = Settings.System.getIntForUser(resolver,
                    Settings.System.END_BUTTON_BEHAVIOR,
                    Settings.System.END_BUTTON_BEHAVIOR_DEFAULT,
                    UserHandle.USER_CURRENT);
            mIncallPowerBehavior = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_DEFAULT,
                    UserHandle.USER_CURRENT);

            // Configure wake gesture.
            boolean wakeGestureEnabledSetting = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.WAKE_GESTURE_ENABLED, 0,
                    UserHandle.USER_CURRENT) != 0;
            if (mWakeGestureEnabledSetting != wakeGestureEnabledSetting) {
                mWakeGestureEnabledSetting = wakeGestureEnabledSetting;
                updateWakeGestureListenerLp();
            }

            // Configure rotation lock.
            int userRotation = Settings.System.getIntForUser(resolver,
                    Settings.System.USER_ROTATION, Surface.ROTATION_0,
                    UserHandle.USER_CURRENT);
            if (mUserRotation != userRotation) {
                mUserRotation = userRotation;
                updateRotation = true;
            }
            int userRotationMode = Settings.System.getIntForUser(resolver,
                    Settings.System.ACCELEROMETER_ROTATION, 0, UserHandle.USER_CURRENT) != 0 ?
                            WindowManagerPolicy.USER_ROTATION_FREE :
                                    WindowManagerPolicy.USER_ROTATION_LOCKED;
            if (mUserRotationMode != userRotationMode) {
                mUserRotationMode = userRotationMode;
                updateRotation = true;
                updateOrientationListenerLp();
            }

            if (mSystemReady) {
                int pointerLocation = Settings.System.getIntForUser(resolver,
                        Settings.System.POINTER_LOCATION, 0, UserHandle.USER_CURRENT);
                if (mPointerLocationMode != pointerLocation) {
                    mPointerLocationMode = pointerLocation;
                    mHandler.sendEmptyMessage(pointerLocation != 0 ?
                            MSG_ENABLE_POINTER_LOCATION : MSG_DISABLE_POINTER_LOCATION);
                }

            }
            // use screen off timeout setting as the timeout for the lockscreen
            mLockScreenTimeout = Settings.System.getIntForUser(resolver,
                    Settings.System.SCREEN_OFF_TIMEOUT, 0, UserHandle.USER_CURRENT);
            String imId = Settings.Secure.getStringForUser(resolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD, UserHandle.USER_CURRENT);
            boolean hasSoftInput = imId != null && imId.length() > 0;
            if (mHasSoftInput != hasSoftInput) {
                mHasSoftInput = hasSoftInput;
                updateRotation = true;
            }
            if (mImmersiveModeConfirmation != null) {
                mImmersiveModeConfirmation.loadSetting(mCurrentUserId);
            }
            
            // +++ jeson_li: ViewFlipCover
            if(android.os.Build.FEATURES.HAS_TRANSCOVER){
                mIsTranscoverEnabled = Settings.System.getIntForUser(resolver,
                        Settings.System.ASUS_TRANSCOVER, 1, UserHandle.USER_CURRENT) != 0;
                mTranscoverAutomaticUnlock = Settings.System.getIntForUser(resolver,
                        Settings.System.ASUS_TRANSCOVER_AUTOMATIC_UNLOCK, 1, UserHandle.USER_CURRENT) != 0;
                boolean coverEnableChanged=(mIsTranscoverEnabledLastForCover!=mIsTranscoverEnabled);
                boolean coverUnlockChanged=(mTranscoverAutomaticUnlockLastForCover!=mTranscoverAutomaticUnlock);
                if(coverUnlockChanged){
                    mTranscoverAutomaticUnlockLastForCover=mTranscoverAutomaticUnlock;
                }
                if(coverEnableChanged||(coverUnlockChanged&&mHasTranscoverInfoFeature)){
                    if(mHandler!=null){//send msg (avoid dead lock) for hallsensor to enable/disable irq (fix TT-701354)
                        mHandler.removeMessages(MSG_FLIPCOVER_SET_PROP_FOR_HALL_SENSOR);
                        mHandler.sendEmptyMessage(MSG_FLIPCOVER_SET_PROP_FOR_HALL_SENSOR);
                    }
                }
                if(coverEnableChanged){
                    mIsTranscoverEnabledLastForCover=mIsTranscoverEnabled;
                    if(mSystemBooted&&mHasTranscoverInfoFeature&&mFlipCover2ServiceDelegate!=null&&(mCurrentUserIdLastForCover==mCurrentUserId)){
                        Log.i(TAG, "cover setting change, mIsTranscoverEnabledLastForCover="+mIsTranscoverEnabledLastForCover+", mIsTranscoverEnabled="+mIsTranscoverEnabled);
                        mFlipCover2ServiceDelegate.sendCoverServiceRebindMsg();//send msg (avoid dead lock and long time without response)to unbind or rebind cover service
                    }
                }
            }
            // --- jeson_li: ViewFlipCover
            
            //+++Chilin_Wang@asus.com, Instant camera porting
            mIsInstantCameraEnabled = Settings.System.getIntForUser(resolver,
                    Settings.System.ASUS_LOCKSCREEN_INSTANT_CAMERA, 0, UserHandle.USER_CURRENT) != 0;
            Slog.d(TAG, "mIsInstantCameraEnabled=" + mIsInstantCameraEnabled);

            mIsLongPressInstantCameraEnabled = Settings.System.getIntForUser(resolver,
                    Settings.System.ASUS_LOCKSCREEN_LONGPRESS_INSTANT_CAMERA, 0, UserHandle.USER_CURRENT) != 0;
            Slog.d(TAG, "mIsLongPressInstantCameraEnabled=" + mIsLongPressInstantCameraEnabled);
            //---Instant camera porting

            //BEGIN: Jeffrey_Chaing@asus.com
            mIsLaunchCameraFromFpEnabled = Settings.Global.getInt(resolver,
                    Settings.Global.ASUS_FINGERPRINT_SELFIE_CAMERA, 0) != 0;
            Slog.d(TAG, "mIsLaunchCameraFromFpEnabled=" + mIsLaunchCameraFromFpEnabled);
            //END: Jeffrey_Chaing@asus.com

            //BEGIN: Chris_Ye@asus.com add for disbale & enable home/back/recent key
            if(Build.FEATURES.ENABLE_SHIELD_KEYCODE) {
                mIsShieldKeyCode = Settings.Global.getInt(resolver,
                        Settings.Global.SHIELD_KEY_CODE, 0) != 0;
                Slog.d(TAG, "mIsShieldKeyCode=" + mIsShieldKeyCode);
            }
            //END: Chris_Ye@asus.com add for disbale & enable home/back/recent key
            
             //BEGIN : roy_huang@asus.com
             mIsFpNavigationKeysEnabled = Settings.Global.getInt(resolver,
                     Settings.Global.ASUS_FINGERPRINT_ENABLE_NAVIGATION_KEYS, 0) != 0;
             Slog.d(TAG, "mIsFpNavigationKeysEnabled=" + mIsFpNavigationKeysEnabled);
            
             mIsNavigationPaymentEnabled = Settings.Global.getInt(resolver,
                     Settings.Global.MOBILE_PAYMENT_ENABLE, 0) != 0;
             Slog.d(TAG, "mIsNavigationPaymentEnabled=" + mIsNavigationPaymentEnabled);
            
             String targetApp = Settings.Global.getStringForUser(resolver,
                     Settings.Global.MOBILE_PAYMENT_METHOD, UserHandle.USER_CURRENT);
             updateHomeDoubleTapTargetApp(targetApp);
             Slog.d(TAG, "mHomeDoubleTapTargetAppPackage=" + mHomeDoubleTapTargetAppPackage + " ; mHomeDoubleTapTargetAppClass=" + mHomeDoubleTapTargetAppClass);
             //END : roy_haung@asus.com
            
        }
        synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
            PolicyControl.reloadFromSetting(mContext);

            //+++Chilin_Wang@asus.com, long-pressing switch key porting
            mFuncWhenLongPressAppSwitch = Settings.System.getIntForUser(resolver,
                    Settings.System.LONG_PRESSED_FUNC, Settings.System.LONG_PRESSED_FUNC_DEFAULT, UserHandle.USER_CURRENT);
            //---long-pressing switch key porting

            //gaohui@wind-mobi add for asus patch 20170306 begin
            // BEGIN leo_liao@asus.com, One-hand control
            //updateOneHandCtrlSettings();
            // END leo_liao@asus.com
            
            if (mIsCNSku) {
                mDoubleTapOnHomeBehavior = DOUBLE_TAP_HOME_TARGET_APP;
            } else {
                // BEGIN leo_liao@asus.com, One-hand control
                updateOneHandCtrlSettings();
                // END leo_liao@asus.com
            }
            //gaohui@wind-mobi add for asus patch 20170306 end
        }
        if (updateRotation) {
            updateRotation(true);
        }
        //+++cenxingcan@wind-mobi.com, listen value WIND_DEF_DATA_OTG_SELECT_MODE begin
        if (WIND_DEF_OTG_REVERSE) {
            updateOtgCtrlSettings();
        }
        //+++cenxingcan@wind-mobi.com, listen value WIND_DEF_DATA_OTG_SELECT_MODE end
    }

    private void updateWakeGestureListenerLp() {
        if (shouldEnableWakeGestureLp()) {
            mWakeGestureListener.requestWakeUpTrigger();
        } else {
            mWakeGestureListener.cancelWakeUpTrigger();
        }
    }

    private boolean shouldEnableWakeGestureLp() {
        return mWakeGestureEnabledSetting && !mAwake
                && (!mLidControlsSleep || mLidState != LID_CLOSED)
                && mWakeGestureListener.isSupported();
    }

    private void enablePointerLocation() {
        if (mPointerLocationView == null) {
            mPointerLocationView = new PointerLocationView(mContext);
            mPointerLocationView.setPrintCoords(false);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            lp.type = WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
            lp.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            if (ActivityManager.isHighEndGfx()) {
                lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                lp.privateFlags |=
                        WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED;
            }
            lp.format = PixelFormat.TRANSLUCENT;
            lp.setTitle("PointerLocation");
            WindowManager wm = (WindowManager)
                    mContext.getSystemService(Context.WINDOW_SERVICE);
            lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
            wm.addView(mPointerLocationView, lp);
            mWindowManagerFuncs.registerPointerEventListener(mPointerLocationView);
        }
    }
    private void disablePointerLocation() {
        if (mPointerLocationView != null) {
            mWindowManagerFuncs.unregisterPointerEventListener(mPointerLocationView);
            WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(mPointerLocationView);
            mPointerLocationView = null;
        }
    }

    private int readRotation(int resID) {
        try {
            int rotation = mContext.getResources().getInteger(resID);
            switch (rotation) {
                case 0:
                    return Surface.ROTATION_0;
                case 90:
                    return Surface.ROTATION_90;
                case 180:
                    return Surface.ROTATION_180;
                case 270:
                    return Surface.ROTATION_270;
            }
        } catch (Resources.NotFoundException e) {
            // fall through
        }
        return -1;
    }

    /** {@inheritDoc} */
    @Override
    public int checkAddPermission(WindowManager.LayoutParams attrs, int[] outAppOp) {
        int type = attrs.type;

        outAppOp[0] = AppOpsManager.OP_NONE;

        if (!((type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW)
                || (type >= FIRST_SUB_WINDOW && type <= LAST_SUB_WINDOW)
                || (type >= FIRST_SYSTEM_WINDOW && type <= LAST_SYSTEM_WINDOW))) {
            return WindowManagerGlobal.ADD_INVALID_TYPE;
        }

        if (type < FIRST_SYSTEM_WINDOW || type > LAST_SYSTEM_WINDOW) {
            // Window manager will make sure these are okay.
            return WindowManagerGlobal.ADD_OKAY;
        }
        String permission = null;
        switch (type) {
            case TYPE_TOAST:
                // XXX right now the app process has complete control over
                // this...  should introduce a token to let the system
                // monitor/control what they are doing.
                outAppOp[0] = AppOpsManager.OP_TOAST_WINDOW;
                break;
            case TYPE_DREAM:
            case TYPE_INPUT_METHOD:
            case TYPE_WALLPAPER:
            case TYPE_PRIVATE_PRESENTATION:
            case TYPE_VOICE_INTERACTION:
            case TYPE_ACCESSIBILITY_OVERLAY:
            case TYPE_QS_DIALOG:
            /// M: Support IPO window.
            case TYPE_TOP_MOST:
                // The window manager will check these.
                break;
            case TYPE_PHONE:
            case TYPE_PRIORITY_PHONE:
            case TYPE_SYSTEM_ALERT:
            case TYPE_SYSTEM_ERROR:
            case TYPE_SYSTEM_OVERLAY:
                permission = android.Manifest.permission.SYSTEM_ALERT_WINDOW;
                outAppOp[0] = AppOpsManager.OP_SYSTEM_ALERT_WINDOW;
                break;
            default:
                permission = android.Manifest.permission.INTERNAL_SYSTEM_WINDOW;
        }
        if (permission != null) {
            if (android.Manifest.permission.SYSTEM_ALERT_WINDOW.equals(permission)) {
                final int callingUid = Binder.getCallingUid();
                // system processes will be automatically allowed privilege to draw
                if (callingUid == Process.SYSTEM_UID) {
                    return WindowManagerGlobal.ADD_OKAY;
                }

                // check if user has enabled this operation. SecurityException will be thrown if
                // this app has not been allowed by the user
                final int mode = mAppOpsManager.checkOpNoThrow(outAppOp[0], callingUid,
                        attrs.packageName);
                switch (mode) {
                    case AppOpsManager.MODE_ALLOWED:
                    case AppOpsManager.MODE_IGNORED:
                        // although we return ADD_OKAY for MODE_IGNORED, the added window will
                        // actually be hidden in WindowManagerService
                        return WindowManagerGlobal.ADD_OKAY;
                    case AppOpsManager.MODE_ERRORED:
                        try {
                            ApplicationInfo appInfo = mContext.getPackageManager()
                                    .getApplicationInfo(attrs.packageName,
                                            UserHandle.getUserId(callingUid));
                            // Don't crash legacy apps
                            if (appInfo.targetSdkVersion < Build.VERSION_CODES.M) {
                                return WindowManagerGlobal.ADD_OKAY;
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            /* ignore */
                        }
                        return WindowManagerGlobal.ADD_PERMISSION_DENIED;
                    default:
                        // in the default mode, we will make a decision here based on
                        // checkCallingPermission()
                        if (mContext.checkCallingPermission(permission) !=
                                PackageManager.PERMISSION_GRANTED) {
                            return WindowManagerGlobal.ADD_PERMISSION_DENIED;
                        } else {
                            return WindowManagerGlobal.ADD_OKAY;
                        }
                }
            }

            if (mContext.checkCallingOrSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return WindowManagerGlobal.ADD_PERMISSION_DENIED;
            }
        }
        return WindowManagerGlobal.ADD_OKAY;
    }

    @Override
    public boolean checkShowToOwnerOnly(WindowManager.LayoutParams attrs) {

        // If this switch statement is modified, modify the comment in the declarations of
        // the type in {@link WindowManager.LayoutParams} as well.
        switch (attrs.type) {
            default:
                // These are the windows that by default are shown only to the user that created
                // them. If this needs to be overridden, set
                // {@link WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS} in
                // {@link WindowManager.LayoutParams}. Note that permission
                // {@link android.Manifest.permission.INTERNAL_SYSTEM_WINDOW} is required as well.
                if ((attrs.privateFlags & PRIVATE_FLAG_SHOW_FOR_ALL_USERS) == 0) {
                    return true;
                }
                break;

            // These are the windows that by default are shown to all users. However, to
            // protect against spoofing, check permissions below.
            case TYPE_APPLICATION_STARTING:
            case TYPE_BOOT_PROGRESS:
            case TYPE_DISPLAY_OVERLAY:
            case TYPE_INPUT_CONSUMER:
            case TYPE_KEYGUARD_SCRIM:
            case TYPE_KEYGUARD_DIALOG:
            case TYPE_MAGNIFICATION_OVERLAY:
            case TYPE_NAVIGATION_BAR:
            case TYPE_NAVIGATION_BAR_PANEL:
            case TYPE_PHONE:
            case TYPE_POINTER:
            case TYPE_PRIORITY_PHONE:
            case TYPE_SEARCH_BAR:
            case TYPE_STATUS_BAR:
            case TYPE_STATUS_BAR_PANEL:
            case TYPE_STATUS_BAR_SUB_PANEL:
            case TYPE_SYSTEM_DIALOG:
            case TYPE_VOLUME_OVERLAY:
            case TYPE_PRIVATE_PRESENTATION:
            case TYPE_DOCK_DIVIDER:
                break;
        }

        // Check if third party app has set window to system window type.
        return mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.INTERNAL_SYSTEM_WINDOW)
                        != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void adjustWindowParamsLw(WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_SYSTEM_OVERLAY:
            case TYPE_SECURE_SYSTEM_OVERLAY:
                // These types of windows can't receive input events.
                attrs.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                attrs.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                break;
            case TYPE_STATUS_BAR:

                // If the Keyguard is in a hidden state (occluded by another window), we force to
                // remove the wallpaper and keyguard flag so that any change in-flight after setting
                // the keyguard as occluded wouldn't set these flags again.
                // See {@link #processKeyguardSetHiddenResultLw}.
                if (mKeyguardHidden) {
                    attrs.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
                    attrs.privateFlags &= ~WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
                }
                break;
            case TYPE_SCREENSHOT:
                attrs.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                break;
        }

        if (attrs.type != TYPE_STATUS_BAR) {
            // The status bar is the only window allowed to exhibit keyguard behavior.
            attrs.privateFlags &= ~WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
        }

        if (ActivityManager.isHighEndGfx()) {
            if ((attrs.flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0) {
                attrs.subtreeSystemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            }
            final boolean forceWindowDrawsStatusBarBackground =
                    (attrs.privateFlags & PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND)
                            != 0;
            if ((attrs.flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0
                    || forceWindowDrawsStatusBarBackground
                            && attrs.height == MATCH_PARENT && attrs.width == MATCH_PARENT) {
                attrs.subtreeSystemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            }
        }
    }
	
    //+++ Yuchen_Chang
    boolean adjustLidStateByHardwareFeature() {
        // If do not have hall sensor, set to absent
        if (!mHasHallSensorFeature) {
            mLidState = LID_ABSENT;
            return true;
        }

        return false;
    }
    //---

    void readLidState() {
        //+++ Yuchen_Chang
        if (adjustLidStateByHardwareFeature()) {
            return;
        }
        //---

        // +++ ASUS_BSP Shawn_Huang Avoid other feature changing mLidstate, except first boot
        if ((-1) == mLidState){
            mLidState = mWindowManagerFuncs.getLidState();
        }
        // --- ASUS_BSP Shawn_Huang Avoid other feature changing mLidstate, except first boot
    }

    private void readCameraLensCoverState() {
        mCameraLensCoverState = mWindowManagerFuncs.getCameraLensCoverState();
    }

    private boolean isHidden(int accessibilityMode) {
        switch (accessibilityMode) {
            case 1:
                return mLidState == LID_CLOSED;
            case 2:
                return mLidState == LID_OPEN;
            default:
                return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void adjustConfigurationLw(Configuration config, int keyboardPresence,
            int navigationPresence) {
        mHaveBuiltInKeyboard = (keyboardPresence & PRESENCE_INTERNAL) != 0;

        readConfigurationDependentBehaviors();
        // +++ Willie_Huang
        // Avoid reading lid state earlier than callback of lid switch changed.
        // Since mLidKeyboardAccessibility is normally set 0,
        // isHidden() is no longer affect the judgement of
        // config.keyboardHidden and config.navigationHidden.
//        readLidState();
        // ---
        applyLidSwitchState();

        if (config.keyboard == Configuration.KEYBOARD_NOKEYS
                || (keyboardPresence == PRESENCE_INTERNAL
                        && isHidden(mLidKeyboardAccessibility))) {
            config.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES;
            if (!mHasSoftInput) {
                config.keyboardHidden = Configuration.KEYBOARDHIDDEN_YES;
            }
        }

        // +++
        Log.d(TAG, "adjustConfigurationLw, config:" + config +
                   " mLidState:" + mLidState +
                   " mHasDockFeature:" + mHasDockFeature +
                   " mHasKeyboardFeature:" + mHasKeyboardFeature +
                   " mHasHallSensorFeature:" + mHasHallSensorFeature +
                   " config.hardKeyboardHidden:" + config.hardKeyboardHidden);
        // ---

        if (config.navigation == Configuration.NAVIGATION_NONAV
                || (navigationPresence == PRESENCE_INTERNAL
                        && isHidden(mLidNavigationAccessibility))) {
            config.navigationHidden = Configuration.NAVIGATIONHIDDEN_YES;
        }
    }

    @Override
    public void onConfigurationChanged() {
        final Resources res = mContext.getResources();
        
        mStatusBarHeight =
                res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);

        // Height of the navigation bar when presented horizontally at bottom
        mNavigationBarHeightForRotationDefault[mPortraitRotation] =
        mNavigationBarHeightForRotationDefault[mUpsideDownRotation] =
                res.getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_height);
        mNavigationBarHeightForRotationDefault[mLandscapeRotation] =
        mNavigationBarHeightForRotationDefault[mSeascapeRotation] = res.getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height_landscape);

        // Width of the navigation bar when presented vertically along one side
        mNavigationBarWidthForRotationDefault[mPortraitRotation] =
        mNavigationBarWidthForRotationDefault[mUpsideDownRotation] =
        mNavigationBarWidthForRotationDefault[mLandscapeRotation] =
        mNavigationBarWidthForRotationDefault[mSeascapeRotation] =
                res.getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_width);

        // Height of the navigation bar when presented horizontally at bottom
        mNavigationBarHeightForRotationInCarMode[mPortraitRotation] =
        mNavigationBarHeightForRotationInCarMode[mUpsideDownRotation] =
                res.getDimensionPixelSize(
                        com.android.internal.R.dimen.navigation_bar_height_car_mode);
        mNavigationBarHeightForRotationInCarMode[mLandscapeRotation] =
        mNavigationBarHeightForRotationInCarMode[mSeascapeRotation] = res.getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height_landscape_car_mode);

        // Width of the navigation bar when presented vertically along one side
        mNavigationBarWidthForRotationInCarMode[mPortraitRotation] =
        mNavigationBarWidthForRotationInCarMode[mUpsideDownRotation] =
        mNavigationBarWidthForRotationInCarMode[mLandscapeRotation] =
        mNavigationBarWidthForRotationInCarMode[mSeascapeRotation] =
                res.getDimensionPixelSize(
                        com.android.internal.R.dimen.navigation_bar_width_car_mode);
    }

    /** {@inheritDoc} */
    @Override
    public int windowTypeToLayerLw(int type) {
        if (type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW) {
            return 2;
        }
        switch (type) {
        case TYPE_PRIVATE_PRESENTATION:
            return 2;
        case TYPE_WALLPAPER:
            // wallpaper is at the bottom, though the window manager may move it.
            return 2;
        case TYPE_DOCK_DIVIDER:
            return 2;
        case TYPE_QS_DIALOG:
            return 2;
        case TYPE_PHONE:
            return 3;
        case TYPE_SEARCH_BAR:
        case TYPE_VOICE_INTERACTION_STARTING:
            return 4;
        case TYPE_VOICE_INTERACTION:
            // voice interaction layer is almost immediately above apps.
            return 5;
        case TYPE_INPUT_CONSUMER:
            return 6;
        case TYPE_SYSTEM_DIALOG:
            return 7;
        case TYPE_TOAST:
            // toasts and the plugged-in battery thing
            return 8;
        case TYPE_PRIORITY_PHONE:
            // SIM errors and unlock.  Not sure if this really should be in a high layer.
            return 9;
        case TYPE_DREAM:
            // used for Dreams (screensavers with TYPE_DREAM windows)
            return 10;
        case TYPE_SYSTEM_ALERT:
            // like the ANR / app crashed dialogs
            return 11;
        case TYPE_INPUT_METHOD:
            // on-screen keyboards and other such input method user interfaces go here.
            return 12;
        case TYPE_INPUT_METHOD_DIALOG:
            // on-screen keyboards and other such input method user interfaces go here.
            return 13;
        case TYPE_KEYGUARD_SCRIM:
            // the safety window that shows behind keyguard while keyguard is starting
            return 14;
        case TYPE_STATUS_BAR_SUB_PANEL:
            return 15;
        case TYPE_STATUS_BAR:
            return 16;
        case TYPE_STATUS_BAR_PANEL:
            return 17;
        case TYPE_KEYGUARD_DIALOG:
            return 18;
        case TYPE_VOLUME_OVERLAY:
            // the on-screen volume indicator and controller shown when the user
            // changes the device volume
            return 19;
        case TYPE_SYSTEM_OVERLAY:
            // the on-screen volume indicator and controller shown when the user
            // changes the device volume
            return 20;
        case TYPE_NAVIGATION_BAR:
            // the navigation bar, if available, shows atop most things
            return 21;
        case TYPE_NAVIGATION_BAR_PANEL:
            // some panels (e.g. search) need to show on top of the navigation bar
            return 22;
        case TYPE_SCREENSHOT:
            // screenshot selection layer shouldn't go above system error, but it should cover
            // navigation bars at the very least.
            return 23;
        case TYPE_SYSTEM_ERROR:
            // system-level error dialogs
            return 24;
        case TYPE_MAGNIFICATION_OVERLAY:
            // used to highlight the magnified portion of a display
            return 25;
        case TYPE_DISPLAY_OVERLAY:
            // used to simulate secondary display devices
            return 26;
        case TYPE_DRAG:
            // the drag layer: input for drag-and-drop is associated with this window,
            // which sits above all other focusable windows
            return 27;
        case TYPE_ACCESSIBILITY_OVERLAY:
            // overlay put by accessibility services to intercept user interaction
            return 28;
        case TYPE_SECURE_SYSTEM_OVERLAY:
            return 29;
        case TYPE_BOOT_PROGRESS:
            return 30;
        case TYPE_POINTER:
            // the (mouse) pointer layer
            return 31;
        /// M: Support IPO window.
        case TYPE_TOP_MOST:
            return 32;
        }
        Log.e(TAG, "Unknown window type: " + type);
        return 2;
    }

    /** {@inheritDoc} */
    @Override
    public int subWindowTypeToLayerLw(int type) {
        switch (type) {
        case TYPE_APPLICATION_PANEL:
        case TYPE_APPLICATION_ATTACHED_DIALOG:
            return APPLICATION_PANEL_SUBLAYER;
        case TYPE_APPLICATION_MEDIA:
            return APPLICATION_MEDIA_SUBLAYER;
        case TYPE_APPLICATION_MEDIA_OVERLAY:
            return APPLICATION_MEDIA_OVERLAY_SUBLAYER;
        case TYPE_APPLICATION_SUB_PANEL:
            return APPLICATION_SUB_PANEL_SUBLAYER;
        case TYPE_APPLICATION_ABOVE_SUB_PANEL:
            return APPLICATION_ABOVE_SUB_PANEL_SUBLAYER;
        }
        Log.e(TAG, "Unknown sub-window type: " + type);
        return 0;
    }

    @Override
    public int getMaxWallpaperLayer() {
        return windowTypeToLayerLw(TYPE_STATUS_BAR);
    }

    private int getNavigationBarWidth(int rotation, int uiMode) {
        if ((uiMode & UI_MODE_TYPE_MASK) == UI_MODE_TYPE_CAR) {
            return mNavigationBarWidthForRotationInCarMode[rotation];
        } else {
            return mNavigationBarWidthForRotationDefault[rotation];
        }
    }

    @Override
    public int getNonDecorDisplayWidth(int fullWidth, int fullHeight, int rotation,
            int uiMode) {
        if (mHasNavigationBar) {
            // For a basic navigation bar, when we are in landscape mode we place
            // the navigation bar to the side.
            if (mNavigationBarCanMove && fullWidth > fullHeight) {
                return fullWidth - getNavigationBarWidth(rotation, uiMode);
            }
        }
        return fullWidth;
    }

    private int getNavigationBarHeight(int rotation, int uiMode) {
        if ((uiMode & UI_MODE_TYPE_MASK) == UI_MODE_TYPE_CAR) {
            return mNavigationBarHeightForRotationInCarMode[rotation];
        } else {
            return mNavigationBarHeightForRotationDefault[rotation];
        }
    }

    @Override
    public int getNonDecorDisplayHeight(int fullWidth, int fullHeight, int rotation,
            int uiMode) {
        if (mHasNavigationBar) {
            // For a basic navigation bar, when we are in portrait mode we place
            // the navigation bar to the bottom.
            if (!mNavigationBarCanMove || fullWidth < fullHeight) {
                return fullHeight - getNavigationBarHeight(rotation, uiMode);
            }
        }
        return fullHeight;
    }

    @Override
    public int getConfigDisplayWidth(int fullWidth, int fullHeight, int rotation, int uiMode) {
        return getNonDecorDisplayWidth(fullWidth, fullHeight, rotation, uiMode);
    }

    @Override
    public int getConfigDisplayHeight(int fullWidth, int fullHeight, int rotation, int uiMode) {
        // There is a separate status bar at the top of the display.  We don't count that as part
        // of the fixed decor, since it can hide; however, for purposes of configurations,
        // we do want to exclude it since applications can't generally use that part
        // of the screen.
        return getNonDecorDisplayHeight(
                fullWidth, fullHeight, rotation, uiMode) - mStatusBarHeight;
    }

    @Override
    public boolean isForceHiding(WindowManager.LayoutParams attrs) {
        return (attrs.privateFlags & PRIVATE_FLAG_KEYGUARD) != 0 ||
        /// M: [ALPS01939364][ALPS01948669] Fix app window is hidden even when Keyguard is occluded
            (isKeyguardHostWindow(attrs) && isKeyguardShowingAndNotOccluded()) ||
            (attrs.type == TYPE_KEYGUARD_SCRIM);
    }

    @Override
    public boolean isKeyguardHostWindow(WindowManager.LayoutParams attrs) {
        return attrs.type == TYPE_STATUS_BAR;
    }

    @Override
    public boolean canBeForceHidden(WindowState win, WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_STATUS_BAR:
            case TYPE_NAVIGATION_BAR:
            case TYPE_WALLPAPER:
            case TYPE_DREAM:
            case TYPE_KEYGUARD_SCRIM:
                return false;
            default:
                // Hide only windows below the keyguard host window.
                return windowTypeToLayerLw(win.getBaseType())
                        < windowTypeToLayerLw(TYPE_STATUS_BAR);
        }
    }

    @Override
    public WindowState getWinShowWhenLockedLw() {
        return mWinShowWhenLocked;
    }

    /** {@inheritDoc} */
    @Override
    public View addStartingWindow(IBinder appToken, String packageName, int theme,
            CompatibilityInfo compatInfo, CharSequence nonLocalizedLabel, int labelRes,
            int icon, int logo, int windowFlags, Configuration overrideConfig) {
        if (!SHOW_STARTING_ANIMATIONS) {
            return null;
        }
        if (packageName == null) {
            return null;
        }

        WindowManager wm = null;
        View view = null;

        try {
            Context context = mContext;
            if (DEBUG_STARTING_WINDOW) Slog.d(TAG, "addStartingWindow " + packageName
                    + ": nonLocalizedLabel=" + nonLocalizedLabel + " theme="
                    + Integer.toHexString(theme));
            if (theme != context.getThemeResId() || labelRes != 0) {
                try {
                    context = context.createPackageContext(packageName, 0);
                    context.setTheme(theme);
                } catch (PackageManager.NameNotFoundException e) {
                    // Ignore
                }
            }

            if (overrideConfig != null && overrideConfig != EMPTY) {
                if (DEBUG_STARTING_WINDOW) Slog.d(TAG, "addStartingWindow: creating context based"
                        + " on overrideConfig" + overrideConfig + " for starting window");
                final Context overrideContext = context.createConfigurationContext(overrideConfig);
                overrideContext.setTheme(theme);
                final TypedArray typedArray = overrideContext.obtainStyledAttributes(
                        com.android.internal.R.styleable.Window);
                final int resId = typedArray.getResourceId(R.styleable.Window_windowBackground, 0);
                if (resId != 0 && overrideContext.getDrawable(resId) != null) {
                    // We want to use the windowBackground for the override context if it is
                    // available, otherwise we use the default one to make sure a themed starting
                    // window is displayed for the app.
                    if (DEBUG_STARTING_WINDOW) Slog.d(TAG, "addStartingWindow: apply overrideConfig"
                            + overrideConfig + " to starting window resId=" + resId);
                    context = overrideContext;
                }
            }

            final PhoneWindow win = new PhoneWindow(context);
            win.setIsStartingWindow(true);

            CharSequence label = context.getResources().getText(labelRes, null);
            // Only change the accessibility title if the label is localized
            if (label != null) {
                win.setTitle(label, true);
            } else {
                win.setTitle(nonLocalizedLabel, false);
            }

            win.setType(
                WindowManager.LayoutParams.TYPE_APPLICATION_STARTING);

            synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                // Assumes it's safe to show starting windows of launched apps while
                // the keyguard is being hidden. This is okay because starting windows never show
                // secret information.
                if (mKeyguardHidden) {
                    windowFlags |= FLAG_SHOW_WHEN_LOCKED;
                }
            }

            // Force the window flags: this is a fake window, so it is not really
            // touchable or focusable by the user.  We also add in the ALT_FOCUSABLE_IM
            // flag because we do know that the next window will take input
            // focus, so we want to get the IME window up on top of us right away.
            win.setFlags(
                windowFlags|
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                windowFlags|
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

            win.setDefaultIcon(icon);
            win.setDefaultLogo(logo);

            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);

            final WindowManager.LayoutParams params = win.getAttributes();
            params.token = appToken;
            params.packageName = packageName;
            params.windowAnimations = win.getWindowStyle().getResourceId(
                    com.android.internal.R.styleable.Window_windowAnimationStyle, 0);
            params.privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_FAKE_HARDWARE_ACCELERATED;
            params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;

            if (!compatInfo.supportsScreen()) {
                params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_COMPATIBLE_WINDOW;
            }

            params.setTitle("Starting " + packageName);

            wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
            view = win.getDecorView();

            if (DEBUG_STARTING_WINDOW) Slog.d(TAG, "Adding starting window for "
                + packageName + " / " + appToken + ": " + (view.getParent() != null ? view : null));

            wm.addView(view, params);

            /// M: [App Launch Reponse Time Enhancement] Merge Traversal. {@
            if (mAppLaunchTimeEnabled) {
                WindowManagerGlobal.getInstance().doTraversal(view, true);
            }
            /// @}
            // Only return the view if it was successfully added to the
            // window manager... which we can tell by it having a parent.
            return view.getParent() != null ? view : null;
        } catch (WindowManager.BadTokenException e) {
            // ignore
            Log.w(TAG, appToken + " already running, starting window not displayed. " +
                    e.getMessage());
        } catch (RuntimeException e) {
            // don't crash if something else bad happens, for example a
            // failure loading resources because we are loading from an app
            // on external storage that has been unmounted.
            Log.w(TAG, appToken + " failed creating starting window", e);
        } finally {
            if (view != null && view.getParent() == null) {
                Log.w(TAG, "view not successfully added to wm, removing view");
                wm.removeViewImmediate(view);
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void removeStartingWindow(IBinder appToken, View window) {
        if (DEBUG_STARTING_WINDOW) Slog.v(TAG, "Removing starting window for " + appToken + ": "
                + window + " Callers=" + Debug.getCallers(4));

        if (window != null) {
            WindowManager wm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(window);
        }
    }

    /**
     * Preflight adding a window to the system.
     *
     * Currently enforces that three window types are singletons:
     * <ul>
     * <li>STATUS_BAR_TYPE</li>
     * <li>KEYGUARD_TYPE</li>
     * </ul>
     *
     * @param win The window to be added
     * @param attrs Information about the window to be added
     *
     * @return If ok, WindowManagerImpl.ADD_OKAY.  If too many singletons,
     * WindowManagerImpl.ADD_MULTIPLE_SINGLETON
     */
    @Override
    public int prepareAddWindowLw(WindowState win, WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_STATUS_BAR:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                if (mStatusBar != null) {
                    if (mStatusBar.isAlive()) {
                        return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                    }
                }
                mStatusBar = win;
                mStatusBarController.setWindow(win);
                break;
            case TYPE_NAVIGATION_BAR:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                if (mNavigationBar != null) {
                    if (mNavigationBar.isAlive()) {
                        return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                    }
                }
                mNavigationBar = win;
                mNavigationBarController.setWindow(win);
                if (DEBUG_LAYOUT) Slog.i(TAG, "NAVIGATION BAR: " + mNavigationBar);
                break;
            case TYPE_NAVIGATION_BAR_PANEL:
                //+++Chilin_Wang@asus.com, Porting of lock physical key when the screen pinning request is show.
                handleLockPhysicalKeyStatusMessage(true);
                //---Chilin_Wang@asus.com
            case TYPE_STATUS_BAR_PANEL:
            case TYPE_STATUS_BAR_SUB_PANEL:
            case TYPE_VOICE_INTERACTION_STARTING:
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.STATUS_BAR_SERVICE,
                        "PhoneWindowManager");
                break;
            case TYPE_KEYGUARD_SCRIM:
                if (mKeyguardScrim != null) {
                    return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                }
                mKeyguardScrim = win;
                break;
            //Begin:HJ@asus.com.Adding for inadvertentTouch
            case TYPE_SYSTEM_ERROR: {
                if(Build.FEATURES.ENABLE_INADVERTENTTOUCH && (attrs.format == INADVERTENTTOUCH_WINDOW_FORMAT)) {
                    Slog.d(TAG,"Detect Inadvertent Touch window add. Disable physical key");
                    handleLockPhysicalKeyStatusMessage(true);
                    mPowerManagerInternal.setInadvertentTouch(true);
                    mImmersiveModeConfirmation.setInadvertentTouchEnableLw(true);
                }
            } break;
            //End:HJ@asus.com.Adding for inadvertentTouch
        }
        return WindowManagerGlobal.ADD_OKAY;
    }

    /** {@inheritDoc} */
    @Override
    public void removeWindowLw(WindowState win) {
        if (mStatusBar == win) {
            mStatusBar = null;
            mStatusBarController.setWindow(null);
            mKeyguardDelegate.showScrim();
        } else if (mKeyguardScrim == win) {
            Log.v(TAG, "Removing keyguard scrim");
            mKeyguardScrim = null;
        } if (mNavigationBar == win) {
            mNavigationBar = null;
            mNavigationBarController.setWindow(null);
        }
        //+++Chilin_Wang@asus.com, Porting of lock physical key when the screen pinning request is show.
        if (win != null && win.getBaseType() == TYPE_NAVIGATION_BAR_PANEL) {
            handleLockPhysicalKeyStatusMessage(false);
        }
        //---Chilin_Wang@asus.com
        //Begin:HJ@asus.com.Adding for inadvertentTouch
        if (Build.FEATURES.ENABLE_INADVERTENTTOUCH && (win != null && win.getBaseType() == TYPE_SYSTEM_ERROR
                && win.getAttrs().format == INADVERTENTTOUCH_WINDOW_FORMAT)) {
            Slog.d(TAG, "Detect Inadvertent Touch window remove. enable physical key");
            handleLockPhysicalKeyStatusMessage(false);
            mPowerManagerInternal.setInadvertentTouch(false);
            mImmersiveModeConfirmation.setInadvertentTouchEnableLw(false);
        }
        //End:HJ@asus.com.Adding for inadvertentTouch
    }

    static final boolean PRINT_ANIM = false;

    /** {@inheritDoc} */
    @Override
    public int selectAnimationLw(WindowState win, int transit) {
        if (PRINT_ANIM) Log.i(TAG, "selectAnimation in " + win
              + ": transit=" + transit);
        if (win == mStatusBar) {
            boolean isKeyguard = (win.getAttrs().privateFlags & PRIVATE_FLAG_KEYGUARD) != 0;
            if (transit == TRANSIT_EXIT
                    || transit == TRANSIT_HIDE) {
                return isKeyguard ? -1 : R.anim.dock_top_exit;
            } else if (transit == TRANSIT_ENTER
                    || transit == TRANSIT_SHOW) {
                return isKeyguard ? -1 : R.anim.dock_top_enter;
            }
        } else if (win == mNavigationBar) {
            if (win.getAttrs().windowAnimations != 0) {
                return 0;
            }
            // This can be on either the bottom or the right or the left.
            if (mNavigationBarPosition == NAV_BAR_BOTTOM) {
                if (transit == TRANSIT_EXIT
                        || transit == TRANSIT_HIDE) {
                    return R.anim.dock_bottom_exit;
                } else if (transit == TRANSIT_ENTER
                        || transit == TRANSIT_SHOW) {
                    return R.anim.dock_bottom_enter;
                }
            } else if (mNavigationBarPosition == NAV_BAR_RIGHT) {
                if (transit == TRANSIT_EXIT
                        || transit == TRANSIT_HIDE) {
                    return R.anim.dock_right_exit;
                } else if (transit == TRANSIT_ENTER
                        || transit == TRANSIT_SHOW) {
                    return R.anim.dock_right_enter;
                }
            }else if (mNavigationBarPosition == NAV_BAR_LEFT) {
                if (transit == TRANSIT_EXIT
                        || transit == TRANSIT_HIDE) {
                    return R.anim.dock_left_exit;
                } else if (transit == TRANSIT_ENTER
                        || transit == TRANSIT_SHOW) {
                    return R.anim.dock_left_enter;
                }
            }
        } else if (win.getAttrs().type == TYPE_DOCK_DIVIDER) {
            return selectDockedDividerAnimationLw(win, transit);
        }

        if (transit == TRANSIT_PREVIEW_DONE) {
            if (win.hasAppShownWindows()) {
                if (PRINT_ANIM) Log.i(TAG, "**** STARTING EXIT");
                return com.android.internal.R.anim.app_starting_exit;
            }
        } else if (win.getAttrs().type == TYPE_DREAM && mDreamingLockscreen
                && transit == TRANSIT_ENTER) {
            // Special case: we are animating in a dream, while the keyguard
            // is shown.  We don't want an animation on the dream, because
            // we need it shown immediately with the keyguard animating away
            // to reveal it.
            return -1;
        }

        return 0;
    }

    private int selectDockedDividerAnimationLw(WindowState win, int transit) {
        int insets = mWindowManagerFuncs.getDockedDividerInsetsLw();

        // If the divider is behind the navigation bar, don't animate.
        final Rect frame = win.getFrameLw();
        final boolean behindNavBar = mNavigationBar != null
                && ((mNavigationBarPosition == NAV_BAR_BOTTOM
                        && frame.top + insets >= mNavigationBar.getFrameLw().top)
                || (mNavigationBarPosition == NAV_BAR_RIGHT
                && frame.left + insets >= mNavigationBar.getFrameLw().left)
                || (mNavigationBarPosition == NAV_BAR_LEFT
                && frame.right - insets <= mNavigationBar.getFrameLw().right));
        final boolean landscape = frame.height() > frame.width();
        final boolean offscreenLandscape = landscape && (frame.right - insets <= 0
                || frame.left + insets >= win.getDisplayFrameLw().right);
        final boolean offscreenPortrait = !landscape && (frame.top - insets <= 0
                || frame.bottom + insets >= win.getDisplayFrameLw().bottom);
        final boolean offscreen = offscreenLandscape || offscreenPortrait;
        if (behindNavBar || offscreen) {
            return 0;
        }
        if (transit == TRANSIT_ENTER || transit == TRANSIT_SHOW) {
            return R.anim.fade_in;
        } else if (transit == TRANSIT_EXIT) {
            return R.anim.fade_out;
        } else {
            return 0;
        }
    }

    @Override
    public void selectRotationAnimationLw(int anim[]) {
        if (PRINT_ANIM) Slog.i(TAG, "selectRotationAnimation mTopFullscreen="
                + mTopFullscreenOpaqueWindowState + " rotationAnimation="
                + (mTopFullscreenOpaqueWindowState == null ?
                        "0" : mTopFullscreenOpaqueWindowState.getAttrs().rotationAnimation));
        if (mTopFullscreenOpaqueWindowState != null && mTopIsFullscreen) {
            switch (mTopFullscreenOpaqueWindowState.getAttrs().rotationAnimation) {
                case ROTATION_ANIMATION_CROSSFADE:
                    anim[0] = R.anim.rotation_animation_xfade_exit;
                    anim[1] = R.anim.rotation_animation_enter;
                    break;
                case ROTATION_ANIMATION_JUMPCUT:
                    anim[0] = R.anim.rotation_animation_jump_exit;
                    anim[1] = R.anim.rotation_animation_enter;
                    break;
                case ROTATION_ANIMATION_ROTATE:
                default:
                    anim[0] = anim[1] = 0;
                    break;
            }
        } else {
            anim[0] = anim[1] = 0;
        }
    }

    @Override
    public boolean validateRotationAnimationLw(int exitAnimId, int enterAnimId,
            boolean forceDefault) {
        switch (exitAnimId) {
            case R.anim.rotation_animation_xfade_exit:
            case R.anim.rotation_animation_jump_exit:
                // These are the only cases that matter.
                if (forceDefault) {
                    return false;
                }
                int anim[] = new int[2];
                selectRotationAnimationLw(anim);
                return (exitAnimId == anim[0] && enterAnimId == anim[1]);
            default:
                return true;
        }
    }

    @Override
    public Animation createForceHideEnterAnimation(boolean onWallpaper,
            boolean goingToNotificationShade) {
        if (goingToNotificationShade) {
            return AnimationUtils.loadAnimation(mContext, R.anim.lock_screen_behind_enter_fade_in);
        }

        AnimationSet set = (AnimationSet) AnimationUtils.loadAnimation(mContext, onWallpaper ?
                    R.anim.lock_screen_behind_enter_wallpaper :
                    R.anim.lock_screen_behind_enter);

        // TODO: Use XML interpolators when we have log interpolators available in XML.
        final List<Animation> animations = set.getAnimations();
        for (int i = animations.size() - 1; i >= 0; --i) {
            animations.get(i).setInterpolator(mLogDecelerateInterpolator);
        }

        return set;
    }


    @Override
    public Animation createForceHideWallpaperExitAnimation(boolean goingToNotificationShade) {
        if (goingToNotificationShade) {
            return null;
        } else {
            return AnimationUtils.loadAnimation(mContext, R.anim.lock_screen_wallpaper_exit);
        }
    }

    private static void awakenDreams() {
        IDreamManager dreamManager = getDreamManager();
        if (dreamManager != null) {
            try {
                dreamManager.awaken();
            } catch (RemoteException e) {
                // fine, stay asleep then
            }
        }
    }

    static IDreamManager getDreamManager() {
        return IDreamManager.Stub.asInterface(
                ServiceManager.checkService(DreamService.DREAM_SERVICE));
    }

    TelecomManager getTelecommService() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    static IAudioService getAudioService() {
        IAudioService audioService = IAudioService.Stub.asInterface(
                ServiceManager.checkService(Context.AUDIO_SERVICE));
        if (audioService == null) {
            Log.w(TAG, "Unable to find IAudioService interface.");
        }
        return audioService;
    }

    boolean keyguardOn() {
        return isKeyguardShowingAndNotOccluded() || inKeyguardRestrictedKeyInputMode();
    }

    private static final int[] WINDOW_TYPES_WHERE_HOME_DOESNT_WORK = {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
        };

    /** {@inheritDoc} */
    @Override
    public long interceptKeyBeforeDispatching(WindowState win, KeyEvent event, int policyFlags) {
        final boolean keyguardOn = keyguardOn();
        final int keyCode = event.getKeyCode();
        final int repeatCount = event.getRepeatCount();
        final int metaState = event.getMetaState();
        final int flags = event.getFlags();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();

        /// M: Add more log at WMS
        if ((false == IS_USER_BUILD) || DEBUG_INPUT) {
            Log.d(TAG, "interceptKeyTi keyCode=" + keyCode + " down=" + down + " repeatCount="
                    + repeatCount + " keyguardOn=" + keyguardOn + " mHomePressed=" + mHomePressed
                    + " canceled=" + canceled + " metaState:" + metaState);
        }

        /// M: Key remapping {@
        if (SystemProperties.get("ro.mtk_hw_key_remapping").equals("1")) {
            // Check keys
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                // Ignore these keyes, handled when queuing
                return -1;
            }
        }
        /// @}

        // If we think we might have a volume down & power key chord on the way
        // but we're not sure, then tell the dispatcher to wait a little while and
        // try again later before dispatching.
        if (mScreenshotChordEnabled && (flags & KeyEvent.FLAG_FALLBACK) == 0) {
            if (mScreenshotChordVolumeDownKeyTriggered && !mScreenshotChordPowerKeyTriggered) {
                final long now = SystemClock.uptimeMillis();
                final long timeoutTime = mScreenshotChordVolumeDownKeyTime
                        + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS;
                if (now < timeoutTime) {
                    return timeoutTime - now;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                    && mScreenshotChordVolumeDownKeyConsumed) {
                if (!down) {
                    mScreenshotChordVolumeDownKeyConsumed = false;
                }
                return -1;
            }
        }

        /// M: Screen unpinning @{
        if (!mHasNavigationBar
                && (flags & KeyEvent.FLAG_FALLBACK) == 0
                && keyCode == DISMISS_SCREEN_PINNING_KEY_CODE
                && (down && repeatCount == 1)) { // long press
            interceptDismissPinningChord();
        }
        /// @}

        // Cancel any pending meta actions if we see any other keys being pressed between the down
        // of the meta key and its corresponding up.
        if (mPendingMetaAction && !KeyEvent.isMetaKey(keyCode)) {
            mPendingMetaAction = false;
        }
        // Any key that is not Alt or Meta cancels Caps Lock combo tracking.
        if (mPendingCapsLockToggle && !KeyEvent.isMetaKey(keyCode) && !KeyEvent.isAltKey(keyCode)) {
            mPendingCapsLockToggle = false;
        }
        // chih-hsuan add for virtual key light behavior +++
        // light virtual key first
        if ((keyCode == KeyEvent.KEYCODE_HOME)
            ||(keyCode == KeyEvent.KEYCODE_APP_SWITCH)
            ||(keyCode == KeyEvent.KEYCODE_BACK)) {
                if (mVirtualKeyBackLight == null) {
                    mVirtualKeyBackLight = LocalServices.getService(VirtualKeyBackLightService.class);
                }
                mVirtualKeyBackLight.flashVirtualKeybyKeyEvent();
        }
        // chih-hsuan add for virtual key light behavior ---

        // First we always handle the home key here, so applications
        // can never break it, although if keyguard is on, we do let
        // it handle it, because that gives us the correct 5 second
        // timeout.
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            // +++ ASUS_BSP Shawn_Huang, disable Back/Home/Switch key when cover is closed.
            if (mHasTranscoverFeature && mLidState == LID_CLOSED) {
                return -1;
            }
            // --- ASUS_BSP Shawn_Huang, disable Back/Home/Switch key when cover is closed.
            //BEGIN: Chris_Ye@asus.com add for disbale & enable home/back/recent key
            if(Build.FEATURES.ENABLE_SHIELD_KEYCODE && mIsShieldKeyCode) {
                return -1;
            }
            //END: Chris_Ye@asus.com add for disbale & enable home/back/recent key
        //add by xulinchao@wind-mobi.com 2016.10.12 emode start
            if (win != null && win.getAttrs() != null){
                if ("com.wind.emode".equals(win.getAttrs().packageName)||"com.goodix.gftest".equals(win.getAttrs().packageName)) {
                    return 0;
                }      
            }
        //add by xulinchao@wind-mobi.com 2016.10.12 emode end

            if (mIsGameGenieLock) {
                Log.i(TAG, "Now is in game genie lock mode ! Lock physical key = " + keyCode + ", down = " + down);
                if (down) {
                    mHandler.removeCallbacks(mNotifyGameGenieLockModeRunnable);
                    mNotifyGameGenieLockModeRunnable.setNotifyMessage(GAMEGENIE_KEY_LOCKED);
                    mHandler.post(mNotifyGameGenieLockModeRunnable);
                }
                return -1;
            }

            // If an incoming call is ringing, HOME is totally disabled.
            // (The user is already on the InCallUI at this point,
            // and his ONLY options are to answer or reject the call.)
            TelecomManager telecomManager = getTelecommService();
            final String focusCls = getCurrentFocusClassName();
            //if (focusCls != null) Log.i(TAG, "Focus on: " + focusCls);
            if (telecomManager != null && telecomManager.isRinging() && (focusCls != null && focusCls.endsWith("InCallActivity"))) {
                Log.i(TAG, "Ignoring HOME; there's a ringing incoming call.");
                return -1;
            }

            // If we have released the home key, and didn't do anything else
            // while it was pressed, then it is time to go home!
            Log.i(TAG,"beforeDispatching keycode is KEYCODE_HOME");
            if (!down) {
                cancelPreloadRecentApps();

                mHomePressed = false;
                if (mHomeConsumed) {
                    mHomeConsumed = false;
                    return -1;
                }

                if (canceled) {
                    Log.i(TAG, "Ignoring HOME; event canceled.");
                    return -1;
                }

                //BEGIN : roy_huang@asus.com
                if (mIsCNSku) {
                    if (mIsFpNavigationKeysEnabled) {
                        mShortPressOnHome = true;
                        mHandler.postDelayed(mShortPressHomeTimeoutRunnable,
                                1000L);
                        mHandler.removeCallbacks(mHomeDoubleTapTimeoutRunnable); // just in case
                        mHomeDoubleTapPending = true;
                        mHandler.postDelayed(mHomeDoubleTapTimeoutRunnable,
                                ViewConfiguration.getDoubleTapTimeout());
                        return -1;
                    }
                } else {
                    // Delay handling home if a double-tap is possible.
                    if (mDoubleTapOnHomeBehavior != DOUBLE_TAP_HOME_NOTHING) {
                        mHandler.removeCallbacks(mHomeDoubleTapTimeoutRunnable); // just in case
                        mHomeDoubleTapPending = true;
                        mHandler.postDelayed(mHomeDoubleTapTimeoutRunnable,
                                ViewConfiguration.getDoubleTapTimeout());
                        return -1;
                    }
                }

                handleShortPressOnHome();
                return -1;
            }

            // If a system window has focus, then it doesn't make sense
            // right now to interact with applications.
            WindowManager.LayoutParams attrs = win != null ? win.getAttrs() : null;
            if (attrs != null) {
                final int type = attrs.type;
                if (type == WindowManager.LayoutParams.TYPE_KEYGUARD_SCRIM
                        || type == WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG
                        || (attrs.privateFlags & PRIVATE_FLAG_KEYGUARD) != 0) {
                    // the "app" is keyguard, so give it the key
                    return 0;
                }
                final int typeCount = WINDOW_TYPES_WHERE_HOME_DOESNT_WORK.length;
                for (int i=0; i<typeCount; i++) {
                    if (type == WINDOW_TYPES_WHERE_HOME_DOESNT_WORK[i]) {
                        // don't do anything, but also don't pass it to the app
                        return -1;
                    }
                }
            }

            // Remember that home is pressed and handle special actions.
            if (repeatCount == 0) {
                mHomePressed = true;
                if (mHomeDoubleTapPending) {
                    mHomeDoubleTapPending = false;
                    mHandler.removeCallbacks(mHomeDoubleTapTimeoutRunnable);
                    handleDoubleTapOnHome();
                } else if (mLongPressOnHomeBehavior == LONG_PRESS_HOME_RECENT_SYSTEM_UI
                        || mDoubleTapOnHomeBehavior == DOUBLE_TAP_HOME_RECENT_SYSTEM_UI
                        || mDoubleTapOnHomeBehavior == DOUBLE_TAP_HOME_TARGET_APP) {
                     preloadRecentApps();
                }
            } else if ((event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                if (!keyguardOn) {
                    handleLongPressOnHome(event.getDeviceId());
                }
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            // Hijack modified menu keys for debugging features
            final int chordBug = KeyEvent.META_SHIFT_ON;

            if (down && repeatCount == 0) {
                if (mEnableShiftMenuBugReports && (metaState & chordBug) == chordBug) {
                    Intent intent = new Intent(Intent.ACTION_BUG_REPORT);
                    mContext.sendOrderedBroadcastAsUser(intent, UserHandle.CURRENT,
                            null, null, null, 0, null, null);
                    return -1;
                } else if (SHOW_PROCESSES_ON_ALT_MENU &&
                        (metaState & KeyEvent.META_ALT_ON) == KeyEvent.META_ALT_ON) {
                    Intent service = new Intent();
                    service.setClassName(mContext, "com.android.server.LoadAverageService");
                    ContentResolver res = mContext.getContentResolver();
                    boolean shown = Settings.Global.getInt(
                            res, Settings.Global.SHOW_PROCESSES, 0) != 0;
                    if (!shown) {
                        mContext.startService(service);
                    } else {
                        mContext.stopService(service);
                    }
                    Settings.Global.putInt(
                            res, Settings.Global.SHOW_PROCESSES, shown ? 0 : 1);
                    return -1;
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (down) {
                if (repeatCount == 0) {
                    mSearchKeyShortcutPending = true;
                    mConsumeSearchKeyUp = false;
                }
            } else {
                mSearchKeyShortcutPending = false;
                if (mConsumeSearchKeyUp) {
                    mConsumeSearchKeyUp = false;
                    return -1;
                }
            }
            return 0;
        } else if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            // +++ ASUS_BSP Shawn_Huang, disable Back/Home/Switch key when cover is closed.
            if (mHasTranscoverFeature && mLidState == LID_CLOSED) {
                return -1;
            }
            // ---
            // --- ASUS_BSP Shawn_Huang, disable Back/Home/Switch key when cover is closed.

            //BEGIN: Chris_Ye@asus.com add for disbale & enable home/back/recent key
            if(Build.FEATURES.ENABLE_SHIELD_KEYCODE && mIsShieldKeyCode) {
                return -1;
            }
            //END: Chris_Ye@asus.com add for disbale & enable home/back/recent key

            if (mIsGameGenieLock) {
                Log.i(TAG, "Now is in game genie lock mode ! Lock physical key = " + keyCode + ", down = " + down);
                mHandler.removeCallbacks(mRecentLongPressRunnable);
                if (down) {
                    mHandler.removeCallbacks(mNotifyGameGenieLockModeRunnable);
                    mNotifyGameGenieLockModeRunnable.setNotifyMessage(GAMEGENIE_KEY_LOCKED);
                    mHandler.post(mNotifyGameGenieLockModeRunnable);
                }
                return -1;
            }

            // If an incoming call is ringing, APP_SWITCH is totally disabled.
            // (The user is already on the InCallUI at this point,
            // and his ONLY options are to answer or reject the call.)
            TelecomManager telecomManager = getTelecommService();
            final String focusCls = getCurrentFocusClassName();
            //if (focusCls != null) Log.i(TAG, "Focus on: " + focusCls);
            if (telecomManager != null && telecomManager.isRinging() && (focusCls != null && focusCls.endsWith("InCallActivity"))) {
                Log.i(TAG, "Ignoring APP_SWITCH; there's a ringing incoming call.");
                return -1;
            }

            //+++Chilin_Wang@asus.com, Porting of long-pressing switch key event
            Log.i(TAG,"beforeDispatching keycode is KEYCODE_APP_SWITCH");
            if (down && repeatCount == 0) {
                if (!keyguardOn) {
                    preloadRecentApps();
                }
                if (!keyguardOn && mSupportShutterOrRecordKeyDevice && !mLockRecentKeyEnabled && isCameraActive()) {
                    notifyCameraRecentKey("down");
                }
                mSwitchKeyHandled = false;
                if (isDeviceProvisioned() && mFuncWhenLongPressAppSwitch != Settings.System.LONG_PRESSED_FUNC_RECENTLIST) {
                    mHandler.postDelayed(mRecentLongPressRunnable, getScreenshotChordLongPressDelay());
                }
            } else if (!down) {
                Log.i(TAG,"mSwitchKeyHandled = "+mSwitchKeyHandled+", keyguardOn = "+keyguardOn);
                if (!mSwitchKeyHandled) {
                    mHandler.removeCallbacks(mRecentLongPressRunnable);
                    if (!keyguardOn) {
                        if (mSupportShutterOrRecordKeyDevice && isCameraActive()) {
                            notifyCameraRecentKey("up");
                            if (!mLockRecentKeyEnabled) {
                                mLockRecentKeyEnabled = true;
                                mHandler.removeCallbacks(mLockRecentKeyTimoutRunnable);
                                mHandler.postDelayed(mLockRecentKeyTimoutRunnable, 5000);
                            } else {
                                mHandler.removeCallbacks(mLockRecentKeyTimoutRunnable);
                                mLockRecentKeyEnabled = false;
                                Log.i(TAG,"trigger toggleRecentApps");
                                toggleRecentApps();
                            }
                        } else {
                            toggleRecentApps();
                        }
                    }
                } else {
                    cancelPreloadRecentApps();
                }
            }
            //---Chilin_Wang@asus.com
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_N && event.isMetaPressed()) {
            if (down) {
                IStatusBarService service = getStatusBarService();
                if (service != null) {
                    try {
                        service.expandNotificationsPanel();
                    } catch (RemoteException e) {
                        // do nothing.
                    }
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_S && event.isMetaPressed()
                && event.isCtrlPressed()) {
            if (down && repeatCount == 0) {
                int type = event.isShiftPressed() ? TAKE_SCREENSHOT_SELECTED_REGION
                        : TAKE_SCREENSHOT_FULLSCREEN;
                mScreenshotRunnable.setScreenshotType(type);
                mHandler.post(mScreenshotRunnable);
                return -1;
            }
        } else if (keyCode == KeyEvent.KEYCODE_SLASH && event.isMetaPressed()) {
            if (down && repeatCount == 0 && !isKeyguardLocked()) {
                toggleKeyboardShortcutsMenu(event.getDeviceId());
            }
        } else if (keyCode == KeyEvent.KEYCODE_ASSIST) {
            if (down) {
                if (repeatCount == 0) {
                    mAssistKeyLongPressed = false;
                } else if (repeatCount == 1) {
                    mAssistKeyLongPressed = true;
                    if (!keyguardOn) {
                         launchAssistLongPressAction();
                    }
                }
            } else {
                if (mAssistKeyLongPressed) {
                    mAssistKeyLongPressed = false;
                } else {
                    if (!keyguardOn) {
                        launchAssistAction(null, event.getDeviceId());
                    }
                }
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
            if (!down) {
                Intent voiceIntent;
                if (!keyguardOn) {
                    voiceIntent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
                } else {
                    IDeviceIdleController dic = IDeviceIdleController.Stub.asInterface(
                            ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
                    if (dic != null) {
                        try {
                            dic.exitIdle("voice-search");
                        } catch (RemoteException e) {
                        }
                    }
                    voiceIntent = new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
                    voiceIntent.putExtra(RecognizerIntent.EXTRA_SECURE, true);
                }
                startActivityAsUser(voiceIntent, UserHandle.CURRENT_OR_SELF);
            }
        } else if (keyCode == KeyEvent.KEYCODE_SYSRQ) {
            if (down && repeatCount == 0) {
                mScreenshotRunnable.setScreenshotType(TAKE_SCREENSHOT_FULLSCREEN);
                mHandler.post(mScreenshotRunnable);
            }
            return -1;
        } else if (keyCode == KeyEvent.KEYCODE_BRIGHTNESS_UP
                || keyCode == KeyEvent.KEYCODE_BRIGHTNESS_DOWN) {
            if (down) {
                int direction = keyCode == KeyEvent.KEYCODE_BRIGHTNESS_UP ? 1 : -1;

                // Disable autobrightness if it's on
                int auto = Settings.System.getIntForUser(
                        mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                        UserHandle.USER_CURRENT_OR_SELF);
                if (auto != 0) {
                    Settings.System.putIntForUser(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                            UserHandle.USER_CURRENT_OR_SELF);
                }

                int min = mPowerManager.getMinimumScreenBrightnessSetting();
                int max = mPowerManager.getMaximumScreenBrightnessSetting();
                int step = (max - min + BRIGHTNESS_STEPS - 1) / BRIGHTNESS_STEPS * direction;
                int brightness = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS,
                        mPowerManager.getDefaultScreenBrightnessSetting(),
                        UserHandle.USER_CURRENT_OR_SELF);
                brightness += step;
                // Make sure we don't go beyond the limits.
                brightness = Math.min(max, brightness);
                brightness = Math.max(min, brightness);

                Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, brightness,
                        UserHandle.USER_CURRENT_OR_SELF);
                startActivityAsUser(new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG),
                        UserHandle.CURRENT_OR_SELF);
            }
            return -1;
        } else if(keyCode == KeyEvent.KEYCODE_BACK) {
            // +++ ASUS_BSP Shawn_Huang, disable Back/Home/Switch key when cover is closed.
            if (mHasTranscoverFeature && mLidState == LID_CLOSED) {
                return -1;
            }
            // --- ASUS_BSP Shawn_Huang, disable Back/Home/Switch key when cover is closed.
            //BEGIN: Chris_Ye@asus.com add for disbale & enable home/back/recent key
            if(Build.FEATURES.ENABLE_SHIELD_KEYCODE && mIsShieldKeyCode) {
                return -1;
            }
            //END: Chris_Ye@asus.com add for disbale & enable home/back/recent key

            if (mIsGameGenieLock) {
                Log.i(TAG, "Now is in game genie lock mode ! Lock physical key = " + keyCode + ", down = " + down);
                if (down) {
                    mHandler.removeCallbacks(mNotifyGameGenieLockModeRunnable);
                    mNotifyGameGenieLockModeRunnable.setNotifyMessage(GAMEGENIE_KEY_LOCKED);
                    mHandler.post(mNotifyGameGenieLockModeRunnable);
                }
                return -1;
            }

            //+++Chilin_Wang@asus.com, add long-press backkey function to unpin screen to follow android N's rule
            if(down && repeatCount == 0){
                try {
                    IActivityManager activityManager = ActivityManagerNative.getDefault();
                    if (activityManager.isInLockTaskMode() || mAccessibilityManager.isEnabled()) {
                        mHandler.postDelayed(mScreenUnpinningRunnable, ViewConfiguration.getLongPressTimeout());
                    }
                } catch (RemoteException e) {
                    Log.i(TAG,"Remote exception : "+e.getMessage());
                }
            } else if (!down) {
                cancelPendingScreenUnpinningAction();
                if(mIsScreenUnpinning){
                    mIsScreenUnpinning = false;
                    return -1;
                }
            }
            //---Chilin_Wang@asus.com
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            //BEGIN : roy_huang@asus.com
            if (Build.FEATURES.ENABLE_INADVERTENTTOUCH) {
                WindowManager.LayoutParams attrs = win != null ? win.getAttrs() : null;
                if (attrs != null && (attrs.format == INADVERTENTTOUCH_WINDOW_FORMAT)) {
                    //Begin:HJ@asus.com
                    if(down && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
                        notifyHardwareKeyPressed();
                    }
                    //End:HJ@asus.com
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        if (repeatCount != 0 && (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                            mVolumeUpKeyLongPressed = true;
                            handleLongPressOnVolumeUp();
                        }
                    }
                    return -1;
                }
                if (mVolumeUpKeyLongPressed) {
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && !down) {
                        mVolumeUpKeyLongPressed = false;
                    }
                    return -1;
                }
            }
            //END : roy_huang@asus.com

            if (mUseTvRouting) {
                // On TVs volume keys never go to the foreground app.
                dispatchDirectAudioEvent(event);
                return -1;
            }
        }

        // Toggle Caps Lock on META-ALT.
        boolean actionTriggered = false;
        if (KeyEvent.isModifierKey(keyCode)) {
            if (!mPendingCapsLockToggle) {
                // Start tracking meta state for combo.
                mInitialMetaState = mMetaState;
                mPendingCapsLockToggle = true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                int altOnMask = mMetaState & KeyEvent.META_ALT_MASK;
                int metaOnMask = mMetaState & KeyEvent.META_META_MASK;

                // Check for Caps Lock toggle
                if ((metaOnMask != 0) && (altOnMask != 0)) {
                    // Check if nothing else is pressed
                    if (mInitialMetaState == (mMetaState ^ (altOnMask | metaOnMask))) {
                        // Handle Caps Lock Toggle
                        mInputManagerInternal.toggleCapsLock(event.getDeviceId());
                        actionTriggered = true;
                    }
                }

                // Always stop tracking when key goes up.
                mPendingCapsLockToggle = false;
            }
        }
        // Store current meta state to be able to evaluate it later.
        mMetaState = metaState;

        if (actionTriggered) {
            return -1;
        }

        if (KeyEvent.isMetaKey(keyCode)) {
            if (down) {
                mPendingMetaAction = true;
            } else if (mPendingMetaAction) {
                launchAssistAction(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, event.getDeviceId());
            }
            return -1;
        }

        // Shortcuts are invoked through Search+key, so intercept those here
        // Any printing key that is chorded with Search should be consumed
        // even if no shortcut was invoked.  This prevents text from being
        // inadvertently inserted when using a keyboard that has built-in macro
        // shortcut keys (that emit Search+x) and some of them are not registered.
        if (mSearchKeyShortcutPending) {
            final KeyCharacterMap kcm = event.getKeyCharacterMap();
            if (kcm.isPrintingKey(keyCode)) {
                mConsumeSearchKeyUp = true;
                mSearchKeyShortcutPending = false;
                if (down && repeatCount == 0 && !keyguardOn) {
                    Intent shortcutIntent = mShortcutManager.getIntent(kcm, keyCode, metaState);
                    if (shortcutIntent != null) {
                        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivityAsUser(shortcutIntent, UserHandle.CURRENT);
                            dismissKeyboardShortcutsMenu();
                        } catch (ActivityNotFoundException ex) {
                            Slog.w(TAG, "Dropping shortcut key combination because "
                                    + "the activity to which it is registered was not found: "
                                    + "SEARCH+" + KeyEvent.keyCodeToString(keyCode), ex);
                        }
                    } else {
                        Slog.i(TAG, "Dropping unregistered shortcut key combination: "
                                + "SEARCH+" + KeyEvent.keyCodeToString(keyCode));
                    }
                }
                return -1;
            }
        }

        // Invoke shortcuts using Meta.
        if (down && repeatCount == 0 && !keyguardOn
                && (metaState & KeyEvent.META_META_ON) != 0) {
            final KeyCharacterMap kcm = event.getKeyCharacterMap();
            if (kcm.isPrintingKey(keyCode)) {
                Intent shortcutIntent = mShortcutManager.getIntent(kcm, keyCode,
                        metaState & ~(KeyEvent.META_META_ON
                                | KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON));
                if (shortcutIntent != null) {
                    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivityAsUser(shortcutIntent, UserHandle.CURRENT);
                        dismissKeyboardShortcutsMenu();
                    } catch (ActivityNotFoundException ex) {
                        Slog.w(TAG, "Dropping shortcut key combination because "
                                + "the activity to which it is registered was not found: "
                                + "META+" + KeyEvent.keyCodeToString(keyCode), ex);
                    }
                    return -1;
                }
            }
        }

        // Handle application launch keys.
        if (down && repeatCount == 0 && !keyguardOn) {
            String category = sApplicationLaunchKeyCategories.get(keyCode);
            if (category != null) {
                Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivityAsUser(intent, UserHandle.CURRENT);
                    dismissKeyboardShortcutsMenu();
                } catch (ActivityNotFoundException ex) {
                    Slog.w(TAG, "Dropping application launch key because "
                            + "the activity to which it is registered was not found: "
                            + "keyCode=" + keyCode + ", category=" + category, ex);
                }
                return -1;
            }
        }

        // Display task switcher for ALT-TAB.
        if (down && repeatCount == 0 && keyCode == KeyEvent.KEYCODE_TAB) {
            if (mRecentAppsHeldModifiers == 0 && !keyguardOn && isUserSetupComplete()) {
                final int shiftlessModifiers = event.getModifiers() & ~KeyEvent.META_SHIFT_MASK;
                if (KeyEvent.metaStateHasModifiers(shiftlessModifiers, KeyEvent.META_ALT_ON)) {
                    mRecentAppsHeldModifiers = shiftlessModifiers;
                    showRecentApps(true, false);
                    return -1;
                }
            }
        } else if (!down && mRecentAppsHeldModifiers != 0
                && (metaState & mRecentAppsHeldModifiers) == 0) {
            mRecentAppsHeldModifiers = 0;
            hideRecentApps(true, false);
        }

        // Handle input method switching.
        if (down && repeatCount == 0
                && (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH
                        || (keyCode == KeyEvent.KEYCODE_SPACE
                                && (metaState & KeyEvent.META_META_MASK) != 0))) {
            final boolean forwardDirection = (metaState & KeyEvent.META_SHIFT_MASK) == 0;
            mWindowManagerFuncs.switchInputMethod(forwardDirection);
            return -1;
        }
        if (mLanguageSwitchKeyPressed && !down
                && (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH
                        || keyCode == KeyEvent.KEYCODE_SPACE)) {
            mLanguageSwitchKeyPressed = false;
            return -1;
        }

        if (isValidGlobalKey(keyCode)
                && mGlobalKeyManager.handleGlobalKey(mContext, keyCode, event)) {
            return -1;
        }

        if (down) {
            long shortcutCode = keyCode;
            if (event.isCtrlPressed()) {
                shortcutCode |= ((long) KeyEvent.META_CTRL_ON) << Integer.SIZE;
            }

            if (event.isAltPressed()) {
                shortcutCode |= ((long) KeyEvent.META_ALT_ON) << Integer.SIZE;
            }

            if (event.isShiftPressed()) {
                shortcutCode |= ((long) KeyEvent.META_SHIFT_ON) << Integer.SIZE;
            }

            if (event.isMetaPressed()) {
                shortcutCode |= ((long) KeyEvent.META_META_ON) << Integer.SIZE;
            }

            IShortcutService shortcutService = mShortcutKeyServices.get(shortcutCode);
            if (shortcutService != null) {
                try {
                    if (isUserSetupComplete()) {
                        shortcutService.notifyShortcutKeyPressed(shortcutCode);
                    }
                } catch (RemoteException e) {
                    mShortcutKeyServices.delete(shortcutCode);
                }
                return -1;
            }
        }

        // Reserve all the META modifier combos for system behavior
        if ((metaState & KeyEvent.META_META_ON) != 0) {
            return -1;
        }

        // Let the application handle the key.
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public KeyEvent dispatchUnhandledKey(WindowState win, KeyEvent event, int policyFlags) {
        // Note: This method is only called if the initial down was unhandled.
        if (DEBUG_INPUT) {
            Slog.d(TAG, "Unhandled key: win=" + win + ", action=" + event.getAction()
                    + ", flags=" + event.getFlags()
                    + ", keyCode=" + event.getKeyCode()
                    + ", scanCode=" + event.getScanCode()
                    + ", metaState=" + event.getMetaState()
                    + ", repeatCount=" + event.getRepeatCount()
                    + ", policyFlags=" + policyFlags);
        }

        KeyEvent fallbackEvent = null;
        if ((event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
            final KeyCharacterMap kcm = event.getKeyCharacterMap();
            final int keyCode = event.getKeyCode();
            final int metaState = event.getMetaState();
            final boolean initialDown = event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0;

            // Check for fallback actions specified by the key character map.
            final FallbackAction fallbackAction;
            if (initialDown) {
                fallbackAction = kcm.getFallbackAction(keyCode, metaState);
            } else {
                fallbackAction = mFallbackActions.get(keyCode);
            }

            if (fallbackAction != null) {
                if (DEBUG_INPUT) {
                    Slog.d(TAG, "Fallback: keyCode=" + fallbackAction.keyCode
                            + " metaState=" + Integer.toHexString(fallbackAction.metaState));
                }

                final int flags = event.getFlags() | KeyEvent.FLAG_FALLBACK;
                fallbackEvent = KeyEvent.obtain(
                        event.getDownTime(), event.getEventTime(),
                        event.getAction(), fallbackAction.keyCode,
                        event.getRepeatCount(), fallbackAction.metaState,
                        event.getDeviceId(), event.getScanCode(),
                        flags, event.getSource(), null);

                if (!interceptFallback(win, fallbackEvent, policyFlags)) {
                    fallbackEvent.recycle();
                    fallbackEvent = null;
                }

                if (initialDown) {
                    mFallbackActions.put(keyCode, fallbackAction);
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    mFallbackActions.remove(keyCode);
                    fallbackAction.recycle();
                }
            }
        }

        if (DEBUG_INPUT) {
            if (fallbackEvent == null) {
                Slog.d(TAG, "No fallback.");
            } else {
                Slog.d(TAG, "Performing fallback: " + fallbackEvent);
            }
        }
        return fallbackEvent;
    }

    private boolean interceptFallback(WindowState win, KeyEvent fallbackEvent, int policyFlags) {
        int actions = interceptKeyBeforeQueueing(fallbackEvent, policyFlags);
        if ((actions & ACTION_PASS_TO_USER) != 0) {
            long delayMillis = interceptKeyBeforeDispatching(
                    win, fallbackEvent, policyFlags);
            if (delayMillis == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void registerShortcutKey(long shortcutCode, IShortcutService shortcutService)
            throws RemoteException {
        synchronized (mLock) {
            IShortcutService service = mShortcutKeyServices.get(shortcutCode);
            if (service != null && service.asBinder().pingBinder()) {
                throw new RemoteException("Key already exists.");
            }

            mShortcutKeyServices.put(shortcutCode, shortcutService);
        }
    }

    private void launchAssistLongPressAction() {
        performHapticFeedbackLw(null, HapticFeedbackConstants.LONG_PRESS, false);
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_ASSIST);

        // launch the search activity
        Intent intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            // TODO: This only stops the factory-installed search manager.
            // Need to formalize an API to handle others
            SearchManager searchManager = getSearchManager();
            if (searchManager != null) {
                searchManager.stopSearch();
            }
            startActivityAsUser(intent, UserHandle.CURRENT);
        } catch (ActivityNotFoundException e) {
            Slog.w(TAG, "No activity to handle assist long press action.", e);
        }
    }

    private void launchAssistAction(String hint, int deviceId) {
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_ASSIST);
        if (!isUserSetupComplete()) {
            // Disable opening assist window during setup
            return;
        }
        Bundle args = null;
        if (deviceId > Integer.MIN_VALUE) {
            args = new Bundle();
            args.putInt(Intent.EXTRA_ASSIST_INPUT_DEVICE_ID, deviceId);
        }
        if ((mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) {
            // On TV, use legacy handling until assistants are implemented in the proper way.
            ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                    .launchLegacyAssist(hint, UserHandle.myUserId(), args);
        } else {
            if (hint != null) {
                if (args == null) {
                    args = new Bundle();
                }
                args.putBoolean(hint, true);
            }
            StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
            if (statusbar != null) {
                statusbar.startAssist(args);
            }
        }
    }

    private void startActivityAsUser(Intent intent, UserHandle handle) {
        if (isUserSetupComplete()) {
            mContext.startActivityAsUser(intent, handle);
        } else {
            Slog.i(TAG, "Not starting activity because user setup is in progress: " + intent);
        }
    }

    private SearchManager getSearchManager() {
        if (mSearchManager == null) {
            mSearchManager = (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
        }
        return mSearchManager;
    }

    private void preloadRecentApps() {
        mPreloadedRecentApps = true;
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.preloadRecentApps();
        }
    }

    private void cancelPreloadRecentApps() {
        if (mPreloadedRecentApps) {
            mPreloadedRecentApps = false;
            StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
            if (statusbar != null) {
                statusbar.cancelPreloadRecentApps();
            }
        }
    }

    private void toggleRecentApps() {
        mPreloadedRecentApps = false; // preloading no longer needs to be canceled
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.toggleRecentApps();
        }
    }

    @Override
    public void showRecentApps(boolean fromHome) {
        mHandler.removeMessages(MSG_DISPATCH_SHOW_RECENTS);
        mHandler.obtainMessage(MSG_DISPATCH_SHOW_RECENTS, fromHome ? 1 : 0, 0).sendToTarget();
    }

    private void showRecentApps(boolean triggeredFromAltTab, boolean fromHome) {
        mPreloadedRecentApps = false; // preloading no longer needs to be canceled
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.showRecentApps(triggeredFromAltTab, fromHome);
        }
    }

    private void toggleKeyboardShortcutsMenu(int deviceId) {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.toggleKeyboardShortcutsMenu(deviceId);
        }
    }

    private void dismissKeyboardShortcutsMenu() {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.dismissKeyboardShortcutsMenu();
        }
    }

    private void hideRecentApps(boolean triggeredFromAltTab, boolean triggeredFromHome) {
        mPreloadedRecentApps = false; // preloading no longer needs to be canceled
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.hideRecentApps(triggeredFromAltTab, triggeredFromHome);
        }
    }

    void launchHomeFromHotKey() {
        launchHomeFromHotKey(true /* awakenFromDreams */, true /*respectKeyguard*/);
    }

    /**
     * A home key -> launch home action was detected.  Take the appropriate action
     * given the situation with the keyguard.
     */
    void launchHomeFromHotKey(final boolean awakenFromDreams, final boolean respectKeyguard) {
        if (respectKeyguard) {
            if (isKeyguardShowingAndNotOccluded()) {
                // don't launch home if keyguard showing
                return;
            }

            if (!mHideLockScreen && mKeyguardDelegate.isInputRestricted()) {
                // when in keyguard restricted mode, must first verify unlock
                // before launching home
                mKeyguardDelegate.verifyUnlock(new OnKeyguardExitResult() {
                    @Override
                    public void onKeyguardExitResult(boolean success) {
                        if (success) {
                            try {
                                ActivityManagerNative.getDefault().stopAppSwitches();
                            } catch (RemoteException e) {
                            }
                            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_HOME_KEY);
                            startDockOrHome(true /*fromHomeKey*/, awakenFromDreams);
                        }
                    }
                });
                return;
            }
        }

        // no keyguard stuff to worry about, just launch home!
        try {
            ActivityManagerNative.getDefault().stopAppSwitches();
        } catch (RemoteException e) {
        }
        if (mRecentsVisible) {
            // Hide Recents and notify it to launch Home
            if (awakenFromDreams) {
                awakenDreams();
            }
            hideRecentApps(false, true);
        } else {
            // Otherwise, just launch Home
            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_HOME_KEY);
            startDockOrHome(true /*fromHomeKey*/, awakenFromDreams);
        }
    }

    private final Runnable mClearHideNavigationFlag = new Runnable() {
        @Override
        public void run() {
            synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                // Clear flags.
                mForceClearedSystemUiFlags &=
                        ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }
            mWindowManagerFuncs.reevaluateStatusBarVisibility();
        }
    };

    /**
     * Input handler used while nav bar is hidden.  Captures any touch on the screen,
     * to determine when the nav bar should be shown and prevent applications from
     * receiving those touches.
     */
    final class HideNavInputEventReceiver extends InputEventReceiver {
        public HideNavInputEventReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event) {
            boolean handled = false;
            try {
                if (event instanceof MotionEvent
                        && (event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    final MotionEvent motionEvent = (MotionEvent)event;
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        // When the user taps down, we re-show the nav bar.
                        boolean changed = false;
                        synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                            if (mInputConsumer == null) {
                                return;
                            }
                            // Any user activity always causes us to show the
                            // navigation controls, if they had been hidden.
                            // We also clear the low profile and only content
                            // flags so that tapping on the screen will atomically
                            // restore all currently hidden screen decorations.
                            int newVal = mResettingSystemUiFlags |
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                    View.SYSTEM_UI_FLAG_LOW_PROFILE |
                                    View.SYSTEM_UI_FLAG_FULLSCREEN;
                            if (mResettingSystemUiFlags != newVal) {
                                mResettingSystemUiFlags = newVal;
                                changed = true;
                            }
                            // We don't allow the system's nav bar to be hidden
                            // again for 1 second, to prevent applications from
                            // spamming us and keeping it from being shown.
                            newVal = mForceClearedSystemUiFlags |
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                            if (mForceClearedSystemUiFlags != newVal) {
                                mForceClearedSystemUiFlags = newVal;
                                changed = true;
                                mHandler.postDelayed(mClearHideNavigationFlag, 1000);
                            }
                        }
                        if (changed) {
                            mWindowManagerFuncs.reevaluateStatusBarVisibility();
                        }
                    }
                }
            } finally {
                finishInputEvent(event, handled);
            }
        }
    }
    final InputEventReceiver.Factory mHideNavInputEventReceiverFactory =
            new InputEventReceiver.Factory() {
        @Override
        public InputEventReceiver createInputEventReceiver(
                InputChannel inputChannel, Looper looper) {
            return new HideNavInputEventReceiver(inputChannel, looper);
        }
    };

    // BEGIN: archie_huang@asus.com
    // For feature: Navigation visibility control
    final public class GestureInputEventReceiver extends InputEventReceiver {
        public GestureInputEventReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event) {
            finishInputEvent(event, false);
        }
    }

    final InputEventReceiver.Factory mGestureInputEventReceiverFactory =
            new InputEventReceiver.Factory() {
        @Override
        public InputEventReceiver createInputEventReceiver(
                InputChannel inputChannel, Looper looper) {
            return new GestureInputEventReceiver(inputChannel, looper);
        }
    };
    // END: archie_huang@asus.com

    @Override
    public int adjustSystemUiVisibilityLw(int visibility) {
        mStatusBarController.adjustSystemUiVisibilityLw(mLastSystemUiFlags, visibility);
        mNavigationBarController.adjustSystemUiVisibilityLw(mLastSystemUiFlags, visibility);
        mRecentsVisible = (visibility & View.RECENT_APPS_VISIBLE) > 0;
        mTvPictureInPictureVisible = (visibility & View.TV_PICTURE_IN_PICTURE_VISIBLE) > 0;

        // BEGIN: archie_huang@asus.com
        // For feature: Navigation visibility control
        if (Build.FEATURES.ENABLE_NAV_VIS_CTRL
                && (mLastSystemUiFlags & View.NAVIGATION_BAR_UNHIDE) != 0
                && (visibility & View.NAVIGATION_BAR_UNHIDE) == 0) {
            mLastSystemUiFlags &= ~View.NAVIGATION_BAR_UNHIDE;
        }
        // END: archie_huang@asus.com

        // Reset any bits in mForceClearingStatusBarVisibility that
        // are now clear.
        mResettingSystemUiFlags &= visibility;
        // Clear any bits in the new visibility that are currently being
        // force cleared, before reporting it.
        return visibility & ~mResettingSystemUiFlags
                & ~mForceClearedSystemUiFlags;
    }

    @Override
    public boolean getInsetHintLw(WindowManager.LayoutParams attrs, Rect taskBounds,
            int displayRotation, int displayWidth, int displayHeight, Rect outContentInsets,
            Rect outStableInsets, Rect outOutsets) {
        final int fl = PolicyControl.getWindowFlags(null, attrs);
        final int sysuiVis = PolicyControl.getSystemUiVisibility(null, attrs);
        final int systemUiVisibility = (sysuiVis | attrs.subtreeSystemUiVisibility);

        final boolean useOutsets = outOutsets != null && shouldUseOutsets(attrs, fl);
        if (useOutsets) {
            int outset = ScreenShapeHelper.getWindowOutsetBottomPx(mContext.getResources());
            if (outset > 0) {
                if (displayRotation == Surface.ROTATION_0) {
                    outOutsets.bottom += outset;
                } else if (displayRotation == Surface.ROTATION_90) {
                    outOutsets.right += outset;
                } else if (displayRotation == Surface.ROTATION_180) {
                    outOutsets.top += outset;
                } else if (displayRotation == Surface.ROTATION_270) {
                    outOutsets.left += outset;
                }
            }
        }

        if ((fl & (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR))
                == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)) {
            int availRight, availBottom;
            if (canHideNavigationBar() &&
                    (systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0) {
                availRight = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                availBottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            } else {
                availRight = mRestrictedScreenLeft + mRestrictedScreenWidth;
                availBottom = mRestrictedScreenTop + mRestrictedScreenHeight;
            }
            if ((systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
                if ((fl & FLAG_FULLSCREEN) != 0) {
                    outContentInsets.set(mStableFullscreenLeft, mStableFullscreenTop,
                            availRight - mStableFullscreenRight,
                            availBottom - mStableFullscreenBottom);
                } else {
                    outContentInsets.set(mStableLeft, mStableTop,
                            availRight - mStableRight, availBottom - mStableBottom);
                }
            } else if ((fl & FLAG_FULLSCREEN) != 0 || (fl & FLAG_LAYOUT_IN_OVERSCAN) != 0) {
                outContentInsets.setEmpty();
            } else if ((systemUiVisibility & (View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)) == 0) {
                outContentInsets.set(mCurLeft, mCurTop,
                        availRight - mCurRight, availBottom - mCurBottom);
            } else {
                outContentInsets.set(mCurLeft, mCurTop,
                        availRight - mCurRight, availBottom - mCurBottom);
            }

            outStableInsets.set(mStableLeft, mStableTop,
                    availRight - mStableRight, availBottom - mStableBottom);
            if (taskBounds != null) {
                calculateRelevantTaskInsets(taskBounds, outContentInsets,
                        displayWidth, displayHeight);
                calculateRelevantTaskInsets(taskBounds, outStableInsets,
                        displayWidth, displayHeight);
            }
            return mForceShowSystemBars;
        }
        outContentInsets.setEmpty();
        outStableInsets.setEmpty();
        return mForceShowSystemBars;
    }

    /**
     * For any given task bounds, the insets relevant for these bounds given the insets relevant
     * for the entire display.
     */
    private void calculateRelevantTaskInsets(Rect taskBounds, Rect inOutInsets, int displayWidth,
            int displayHeight) {
        mTmpRect.set(0, 0, displayWidth, displayHeight);
        mTmpRect.inset(inOutInsets);
        mTmpRect.intersect(taskBounds);
        int leftInset = mTmpRect.left - taskBounds.left;
        int topInset = mTmpRect.top - taskBounds.top;
        int rightInset = taskBounds.right - mTmpRect.right;
        int bottomInset = taskBounds.bottom - mTmpRect.bottom;
        inOutInsets.set(leftInset, topInset, rightInset, bottomInset);
    }

    private boolean shouldUseOutsets(WindowManager.LayoutParams attrs, int fl) {
        return attrs.type == TYPE_WALLPAPER || (fl & (WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN)) != 0;
    }

    /** {@inheritDoc} */
    @Override
    public void beginLayoutLw(boolean isDefaultDisplay, int displayWidth, int displayHeight,
                              int displayRotation, int uiMode) {
        mDisplayRotation = displayRotation;
        final int overscanLeft, overscanTop, overscanRight, overscanBottom;
        if (isDefaultDisplay) {
            switch (displayRotation) {
                case Surface.ROTATION_90:
                    overscanLeft = mOverscanTop;
                    overscanTop = mOverscanRight;
                    overscanRight = mOverscanBottom;
                    overscanBottom = mOverscanLeft;
                    break;
                case Surface.ROTATION_180:
                    overscanLeft = mOverscanRight;
                    overscanTop = mOverscanBottom;
                    overscanRight = mOverscanLeft;
                    overscanBottom = mOverscanTop;
                    break;
                case Surface.ROTATION_270:
                    overscanLeft = mOverscanBottom;
                    overscanTop = mOverscanLeft;
                    overscanRight = mOverscanTop;
                    overscanBottom = mOverscanRight;
                    break;
                default:
                    overscanLeft = mOverscanLeft;
                    overscanTop = mOverscanTop;
                    overscanRight = mOverscanRight;
                    overscanBottom = mOverscanBottom;
                    break;
            }
        } else {
            overscanLeft = 0;
            overscanTop = 0;
            overscanRight = 0;
            overscanBottom = 0;
        }
        mOverscanScreenLeft = mRestrictedOverscanScreenLeft = 0;
        mOverscanScreenTop = mRestrictedOverscanScreenTop = 0;
        mOverscanScreenWidth = mRestrictedOverscanScreenWidth = displayWidth;
        mOverscanScreenHeight = mRestrictedOverscanScreenHeight = displayHeight;
        mSystemLeft = 0;
        mSystemTop = 0;
        mSystemRight = displayWidth;
        mSystemBottom = displayHeight;
        mUnrestrictedScreenLeft = overscanLeft;
        mUnrestrictedScreenTop = overscanTop;
        mUnrestrictedScreenWidth = displayWidth - overscanLeft - overscanRight;
        mUnrestrictedScreenHeight = displayHeight - overscanTop - overscanBottom;
        mRestrictedScreenLeft = mUnrestrictedScreenLeft;
        mRestrictedScreenTop = mUnrestrictedScreenTop;
        mRestrictedScreenWidth = mSystemGestures.screenWidth = mUnrestrictedScreenWidth;
        mRestrictedScreenHeight = mSystemGestures.screenHeight = mUnrestrictedScreenHeight;
        mDockLeft = mContentLeft = mVoiceContentLeft = mStableLeft = mStableFullscreenLeft
                = mCurLeft = mUnrestrictedScreenLeft;
        mDockTop = mContentTop = mVoiceContentTop = mStableTop = mStableFullscreenTop
                = mCurTop = mUnrestrictedScreenTop;
        mDockRight = mContentRight = mVoiceContentRight = mStableRight = mStableFullscreenRight
                = mCurRight = displayWidth - overscanRight;
        mDockBottom = mContentBottom = mVoiceContentBottom = mStableBottom = mStableFullscreenBottom
                = mCurBottom = displayHeight - overscanBottom;
        mDockLayer = 0x10000000;
        mStatusBarLayer = -1;

        // start with the current dock rect, which will be (0,0,displayWidth,displayHeight)
        final Rect pf = mTmpParentFrame;
        final Rect df = mTmpDisplayFrame;
        final Rect of = mTmpOverscanFrame;
        final Rect vf = mTmpVisibleFrame;
        final Rect dcf = mTmpDecorFrame;
        pf.left = df.left = of.left = vf.left = mDockLeft;
        pf.top = df.top = of.top = vf.top = mDockTop;
        pf.right = df.right = of.right = vf.right = mDockRight;
        pf.bottom = df.bottom = of.bottom = vf.bottom = mDockBottom;
        dcf.setEmpty();  // Decor frame N/A for system bars.

        if (isDefaultDisplay) {
            /// M: add for fullscreen switch feature @{
            if (mSupportFullscreenSwitch) {
                if (mFocusedWindow != null && !mFocusedWindow.isFullscreenOn()) {
                    getSwitchFrame(mFocusedWindow);
                    mLastSystemUiFlags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
            }
            /// @}
            // For purposes of putting out fake window up to steal focus, we will
            // drive nav being hidden only by whether it is requested.
            final int sysui = mLastSystemUiFlags;
            boolean navVisible = (sysui & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
            boolean navTranslucent = (sysui
                    & (View.NAVIGATION_BAR_TRANSLUCENT | View.NAVIGATION_BAR_TRANSPARENT)) != 0;
            boolean immersive = (sysui & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0;
            boolean immersiveSticky = (sysui & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
            boolean navAllowedHidden = immersive || immersiveSticky;
            navTranslucent &= !immersiveSticky;  // transient trumps translucent
            boolean isKeyguardShowing = isStatusBarKeyguard() && !mHideLockScreen;
            if (!isKeyguardShowing) {
                navTranslucent &= areTranslucentBarsAllowed();
            }
            boolean statusBarExpandedNotKeyguard = !isKeyguardShowing && mStatusBar != null
                    && mStatusBar.getAttrs().height == MATCH_PARENT
                    && mStatusBar.getAttrs().width == MATCH_PARENT;

            // When the navigation bar isn't visible, we put up a fake
            // input window to catch all touch events.  This way we can
            // detect when the user presses anywhere to bring back the nav
            // bar and ensure the application doesn't see the event.
            if (navVisible || navAllowedHidden) {
                if (mInputConsumer != null) {
                    mHandler.sendMessage(
                            mHandler.obtainMessage(MSG_DISPOSE_INPUT_CONSUMER, mInputConsumer));
                    mInputConsumer = null;
                }
            } else if (mInputConsumer == null) {
                mInputConsumer = mWindowManagerFuncs.addInputConsumer(mHandler.getLooper(),
                        mHideNavInputEventReceiverFactory);
            }

            // BEGIN: archie_huang@asus.com
            // For feature: Navigation visibility control
            if (Build.FEATURES.ENABLE_NAV_VIS_CTRL) {
                boolean isUsrRequest = (sysui & View.SYSTEM_UI_FLAG_USER_REQUESTED) != 0;
                if (navVisible) {
                    if (mGestureInputConsumer != null) {
                        mHandler.sendMessage(
                                mHandler.obtainMessage(MSG_DISPOSE_INPUT_CONSUMER, mGestureInputConsumer));
                        mGestureInputConsumer = null;
                    }
                } else if (navAllowedHidden && isUsrRequest && (mGestureInputConsumer == null)) {
                    mGestureInputConsumer = mWindowManagerFuncs.addGestureInputConsumer(mHandler.getLooper(),
                            mGestureInputEventReceiverFactory);
                }
            }
            // END: archie_huang@asus.com

            // For purposes of positioning and showing the nav bar, if we have
            // decided that it can't be hidden (because of the screen aspect ratio),
            // then take that into account.
            navVisible |= !canHideNavigationBar();

            boolean updateSysUiVisibility = layoutNavigationBar(displayWidth, displayHeight,
                    displayRotation, uiMode, overscanLeft, overscanRight, overscanBottom, dcf, navVisible, navTranslucent,
                    navAllowedHidden, statusBarExpandedNotKeyguard);
            if (DEBUG_LAYOUT) Slog.i(TAG, String.format("mDock rect: (%d,%d - %d,%d)",
                    mDockLeft, mDockTop, mDockRight, mDockBottom));
            updateSysUiVisibility |= layoutStatusBar(pf, df, of, vf, dcf, sysui, isKeyguardShowing);
            if (updateSysUiVisibility) {
                updateSystemUiVisibilityLw();
            }
        }
    }

    private boolean layoutStatusBar(Rect pf, Rect df, Rect of, Rect vf, Rect dcf, int sysui,
            boolean isKeyguardShowing) {
        // decide where the status bar goes ahead of time
        if (mStatusBar != null) {
            // apply any navigation bar insets
            pf.left = df.left = of.left = mUnrestrictedScreenLeft;
            pf.top = df.top = of.top = mUnrestrictedScreenTop;
            pf.right = df.right = of.right = mUnrestrictedScreenWidth + mUnrestrictedScreenLeft;
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenHeight
                    + mUnrestrictedScreenTop;
            vf.left = mStableLeft;
            vf.top = mStableTop;
            vf.right = mStableRight;
            vf.bottom = mStableBottom;

            mStatusBarLayer = mStatusBar.getSurfaceLayer();

            // Let the status bar determine its size.
            mStatusBar.computeFrameLw(pf /* parentFrame */, df /* displayFrame */,
                    vf /* overlayFrame */, vf /* contentFrame */, vf /* visibleFrame */,
                    dcf /* decorFrame */, vf /* stableFrame */, vf /* outsetFrame */);

            // For layout, the status bar is always at the top with our fixed height.
            mStableTop = mUnrestrictedScreenTop + mStatusBarHeight;

            boolean statusBarTransient = (sysui & View.STATUS_BAR_TRANSIENT) != 0;
            boolean statusBarTranslucent = (sysui
                    & (View.STATUS_BAR_TRANSLUCENT | View.STATUS_BAR_TRANSPARENT)) != 0;
            if (!isKeyguardShowing) {
                statusBarTranslucent &= areTranslucentBarsAllowed();
            }

            // If the status bar is hidden, we don't want to cause
            // windows behind it to scroll.
            if (mStatusBar.isVisibleLw() && !statusBarTransient) {
                // Status bar may go away, so the screen area it occupies
                // is available to apps but just covering them when the
                // status bar is visible.
                mDockTop = mUnrestrictedScreenTop + mStatusBarHeight;

                mContentTop = mVoiceContentTop = mCurTop = mDockTop;
                mContentBottom = mVoiceContentBottom = mCurBottom = mDockBottom;
                mContentLeft = mVoiceContentLeft = mCurLeft = mDockLeft;
                mContentRight = mVoiceContentRight = mCurRight = mDockRight;

                if (DEBUG_LAYOUT) Slog.v(TAG, "Status bar: " +
                        String.format(
                                "dock=[%d,%d][%d,%d] content=[%d,%d][%d,%d] cur=[%d,%d][%d,%d]",
                                mDockLeft, mDockTop, mDockRight, mDockBottom,
                                mContentLeft, mContentTop, mContentRight, mContentBottom,
                                mCurLeft, mCurTop, mCurRight, mCurBottom));
            }
            if (mStatusBar.isVisibleLw() && !mStatusBar.isAnimatingLw()
                    && !statusBarTransient && !statusBarTranslucent
                    && !mStatusBarController.wasRecentlyTranslucent()) {
                // If the opaque status bar is currently requested to be visible,
                // and not in the process of animating on or off, then
                // we can tell the app that it is covered by it.
                mSystemTop = mUnrestrictedScreenTop + mStatusBarHeight;
            }
            if (mStatusBarController.checkHiddenLw()) {
                return true;
            }
        }
        return false;
    }

    private boolean layoutNavigationBar(int displayWidth, int displayHeight, int displayRotation,
            int uiMode, int overscanLeft, int overscanRight, int overscanBottom, Rect dcf,
            boolean navVisible, boolean navTranslucent, boolean navAllowedHidden,
            boolean statusBarExpandedNotKeyguard) {
        if (mNavigationBar != null) {
            boolean transientNavBarShowing = mNavigationBarController.isTransientShowing();
            // Force the navigation bar to its appropriate place and
            // size.  We need to do this directly, instead of relying on
            // it to bubble up from the nav bar, because this needs to
            // change atomically with screen rotations.
            // BEGIN: archie_huang@asus.com
            // For feature: Navigation visibility control - Hide forever
            boolean forceHideNavBar = Build.FEATURES.ENABLE_NAV_VIS_CTRL && !canShowNavigationBar();
            // END: archie_huang@asus.com

            mNavigationBarPosition = navigationBarPosition(displayWidth, displayHeight,
                    displayRotation);
            if (mNavigationBarPosition == NAV_BAR_BOTTOM) {
                // It's a system nav bar or a portrait screen; nav bar goes on bottom.
                int top = displayHeight - overscanBottom
                        - getNavigationBarHeight(displayRotation, uiMode);
                mTmpNavigationFrame.set(0, top, displayWidth, displayHeight - overscanBottom);
                mStableBottom = mStableFullscreenBottom = mTmpNavigationFrame.top;
                // BEGIN: archie_huang@asus.com
                // For feature: Navigation visibility control - Hide forever
                if (forceHideNavBar) {
                    mNavigationBarController.setBarShowingLw(false);
                    // END: archie_huang@asus.com
                } else if (transientNavBarShowing) {
                    mNavigationBarController.setBarShowingLw(true);
                } else if (navVisible) {
                    /// M: Add condition.
                    if (!mIsAlarmBoot && !mIsShutDown) {
                        mNavigationBarController.setBarShowingLw(true);
                        mDockBottom = mTmpNavigationFrame.top;
                        mRestrictedScreenHeight = mDockBottom - mRestrictedScreenTop;
                        mRestrictedOverscanScreenHeight
                                = mDockBottom - mRestrictedOverscanScreenTop;
                    }
                } else {
                    // We currently want to hide the navigation UI - unless we expanded the status
                    // bar.
                    mNavigationBarController.setBarShowingLw(statusBarExpandedNotKeyguard);
                }
                if (navVisible && !navTranslucent && !navAllowedHidden
                        && !mNavigationBar.isAnimatingLw()
                        && !mNavigationBarController.wasRecentlyTranslucent()) {
                    // If the opaque nav bar is currently requested to be visible,
                    // and not in the process of animating on or off, then
                    // we can tell the app that it is covered by it.
                    mSystemBottom = mTmpNavigationFrame.top;
                }
            } else if (mNavigationBarPosition == NAV_BAR_RIGHT) {
                // Landscape screen; nav bar goes to the right.
                int left = displayWidth - overscanRight
                        - getNavigationBarWidth(displayRotation, uiMode);
                mTmpNavigationFrame.set(left, 0, displayWidth - overscanRight, displayHeight);
                mStableRight = mStableFullscreenRight = mTmpNavigationFrame.left;
                // BEGIN: archie_huang@asus.com
                // For feature: Navigation visibility control - Hide forever
                if (forceHideNavBar) {
                    mNavigationBarController.setBarShowingLw(false);
                    // END: archie_huang@asus.com
                } else if (transientNavBarShowing) {
                    mNavigationBarController.setBarShowingLw(true);
                } else if (navVisible) {
                    /// M: Add condition.
                    if (!mIsAlarmBoot && !mIsShutDown) {
                        mNavigationBarController.setBarShowingLw(true);
                        mDockRight = mTmpNavigationFrame.left;
                        mRestrictedScreenWidth = mDockRight - mRestrictedScreenLeft;
                        mRestrictedOverscanScreenWidth
                                = mDockRight - mRestrictedOverscanScreenLeft;
                    }
                } else {
                    // We currently want to hide the navigation UI - unless we expanded the status
                    // bar.
                    mNavigationBarController.setBarShowingLw(statusBarExpandedNotKeyguard);
                }
                if (navVisible && !navTranslucent && !navAllowedHidden
                        && !mNavigationBar.isAnimatingLw()
                        && !mNavigationBarController.wasRecentlyTranslucent()) {
                    // If the nav bar is currently requested to be visible,
                    // and not in the process of animating on or off, then
                    // we can tell the app that it is covered by it.
                    mSystemRight = mTmpNavigationFrame.left;
                }
            } else if (mNavigationBarPosition == NAV_BAR_LEFT) {
                // Seascape screen; nav bar goes to the left.
                int right = overscanLeft + getNavigationBarWidth(displayRotation, uiMode);
                mTmpNavigationFrame.set(overscanLeft, 0, right, displayHeight);
                mStableLeft = mStableFullscreenLeft = mTmpNavigationFrame.right;
                // BEGIN: archie_huang@asus.com
                // For feature: Navigation visibility control - Hide forever
                if (forceHideNavBar) {
                    mNavigationBarController.setBarShowingLw(false);
                    // END: archie_huang@asus.com
                } else if (transientNavBarShowing) {
                    mNavigationBarController.setBarShowingLw(true);
                } else if (navVisible) {
                    mNavigationBarController.setBarShowingLw(true);
                    mDockLeft = mTmpNavigationFrame.right;
                    // TODO: not so sure about those:
                    mRestrictedScreenLeft = mRestrictedOverscanScreenLeft = mDockLeft;
                    mRestrictedScreenWidth = mDockRight - mRestrictedScreenLeft;
                    mRestrictedOverscanScreenWidth = mDockRight - mRestrictedOverscanScreenLeft;
                } else {
                    // We currently want to hide the navigation UI - unless we expanded the status
                    // bar.
                    mNavigationBarController.setBarShowingLw(statusBarExpandedNotKeyguard);
                }
                if (navVisible && !navTranslucent && !navAllowedHidden
                        && !mNavigationBar.isAnimatingLw()
                        && !mNavigationBarController.wasRecentlyTranslucent()) {
                    // If the nav bar is currently requested to be visible,
                    // and not in the process of animating on or off, then
                    // we can tell the app that it is covered by it.
                    mSystemLeft = mTmpNavigationFrame.right;
                }
            }
            // Make sure the content and current rectangles are updated to
            // account for the restrictions from the navigation bar.
            mContentTop = mVoiceContentTop = mCurTop = mDockTop;
            mContentBottom = mVoiceContentBottom = mCurBottom = mDockBottom;
            mContentLeft = mVoiceContentLeft = mCurLeft = mDockLeft;
            mContentRight = mVoiceContentRight = mCurRight = mDockRight;
            mStatusBarLayer = mNavigationBar.getSurfaceLayer();
            // And compute the final frame.
            mNavigationBar.computeFrameLw(mTmpNavigationFrame, mTmpNavigationFrame,
                    mTmpNavigationFrame, mTmpNavigationFrame, mTmpNavigationFrame, dcf,
                    mTmpNavigationFrame, mTmpNavigationFrame);
            if (DEBUG_LAYOUT) Slog.i(TAG, "mNavigationBar frame: " + mTmpNavigationFrame);
            if (mNavigationBarController.checkHiddenLw()) {
                return true;
            }
        }
        return false;
    }

    private int navigationBarPosition(int displayWidth, int displayHeight, int displayRotation) {
        if (mNavigationBarCanMove && displayWidth > displayHeight) {
            if (displayRotation == Surface.ROTATION_270) {
                return NAV_BAR_LEFT;
            } else {
                return NAV_BAR_RIGHT;
            }
        }
        return NAV_BAR_BOTTOM;
    }

    /** {@inheritDoc} */
    @Override
    public int getSystemDecorLayerLw() {
        if (mStatusBar != null && mStatusBar.isVisibleLw()) {
            return mStatusBar.getSurfaceLayer();
        }

        if (mNavigationBar != null && mNavigationBar.isVisibleLw()) {
            return mNavigationBar.getSurfaceLayer();
        }

        return 0;
    }

    @Override
    public void getContentRectLw(Rect r) {
        r.set(mContentLeft, mContentTop, mContentRight, mContentBottom);
    }

    void setAttachedWindowFrames(WindowState win, int fl, int adjust, WindowState attached,
            boolean insetDecors, Rect pf, Rect df, Rect of, Rect cf, Rect vf) {
        if (win.getSurfaceLayer() > mDockLayer && attached.getSurfaceLayer() < mDockLayer) {
            // Here's a special case: if this attached window is a panel that is
            // above the dock window, and the window it is attached to is below
            // the dock window, then the frames we computed for the window it is
            // attached to can not be used because the dock is effectively part
            // of the underlying window and the attached window is floating on top
            // of the whole thing.  So, we ignore the attached window and explicitly
            // compute the frames that would be appropriate without the dock.
            df.left = of.left = cf.left = vf.left = mDockLeft;
            df.top = of.top = cf.top = vf.top = mDockTop;
            df.right = of.right = cf.right = vf.right = mDockRight;
            df.bottom = of.bottom = cf.bottom = vf.bottom = mDockBottom;
        } else {
            // The effective display frame of the attached window depends on
            // whether it is taking care of insetting its content.  If not,
            // we need to use the parent's content frame so that the entire
            // window is positioned within that content.  Otherwise we can use
            // the overscan frame and let the attached window take care of
            // positioning its content appropriately.
            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                // Set the content frame of the attached window to the parent's decor frame
                // (same as content frame when IME isn't present) if specifically requested by
                // setting {@link WindowManager.LayoutParams#FLAG_LAYOUT_ATTACHED_IN_DECOR} flag.
                // Otherwise, use the overscan frame.
                cf.set((fl & FLAG_LAYOUT_ATTACHED_IN_DECOR) != 0
                        ? attached.getContentFrameLw() : attached.getOverscanFrameLw());
            } else {
                // If the window is resizing, then we want to base the content
                // frame on our attached content frame to resize...  however,
                // things can be tricky if the attached window is NOT in resize
                // mode, in which case its content frame will be larger.
                // Ungh.  So to deal with that, make sure the content frame
                // we end up using is not covering the IM dock.
                cf.set(attached.getContentFrameLw());
                if (attached.isVoiceInteraction()) {
                    if (cf.left < mVoiceContentLeft) cf.left = mVoiceContentLeft;
                    if (cf.top < mVoiceContentTop) cf.top = mVoiceContentTop;
                    if (cf.right > mVoiceContentRight) cf.right = mVoiceContentRight;
                    if (cf.bottom > mVoiceContentBottom) cf.bottom = mVoiceContentBottom;
                } else if (attached.getSurfaceLayer() < mDockLayer) {
                    if (cf.left < mContentLeft) cf.left = mContentLeft;
                    if (cf.top < mContentTop) cf.top = mContentTop;
                    if (cf.right > mContentRight) cf.right = mContentRight;
                    if (cf.bottom > mContentBottom) cf.bottom = mContentBottom;
                }
            }
            df.set(insetDecors ? attached.getDisplayFrameLw() : cf);
            of.set(insetDecors ? attached.getOverscanFrameLw() : cf);
            vf.set(attached.getVisibleFrameLw());
        }
        // The LAYOUT_IN_SCREEN flag is used to determine whether the attached
        // window should be positioned relative to its parent or the entire
        // screen.
        pf.set((fl & FLAG_LAYOUT_IN_SCREEN) == 0
                ? attached.getFrameLw() : df);
    }

    private void applyStableConstraints(int sysui, int fl, Rect r) {
        if ((sysui & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
            // If app is requesting a stable layout, don't let the
            // content insets go below the stable values.
            if ((fl & FLAG_FULLSCREEN) != 0) {
                if (r.left < mStableFullscreenLeft) r.left = mStableFullscreenLeft;
                if (r.top < mStableFullscreenTop) r.top = mStableFullscreenTop;
                if (r.right > mStableFullscreenRight) r.right = mStableFullscreenRight;
                if (r.bottom > mStableFullscreenBottom) r.bottom = mStableFullscreenBottom;
            } else {
                if (r.left < mStableLeft) r.left = mStableLeft;
                if (r.top < mStableTop) r.top = mStableTop;
                if (r.right > mStableRight) r.right = mStableRight;
                if (r.bottom > mStableBottom) r.bottom = mStableBottom;
            }
        }
    }

    private boolean canReceiveInput(WindowState win) {
        boolean notFocusable =
                (win.getAttrs().flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0;
        boolean altFocusableIm =
                (win.getAttrs().flags & WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM) != 0;
        boolean notFocusableForIm = notFocusable ^ altFocusableIm;
        return !notFocusableForIm;
    }

    /** {@inheritDoc} */
    @Override
    public void layoutWindowLw(WindowState win, WindowState attached) {
        // We've already done the navigation bar and status bar. If the status bar can receive
        // input, we need to layout it again to accomodate for the IME window.
        if ((win == mStatusBar && !canReceiveInput(win)) || win == mNavigationBar) {
            return;
        }
        final WindowManager.LayoutParams attrs = win.getAttrs();
        final boolean isDefaultDisplay = win.isDefaultDisplay();
        final boolean needsToOffsetInputMethodTarget = isDefaultDisplay &&
                (win == mLastInputMethodTargetWindow && mLastInputMethodWindow != null);
        if (needsToOffsetInputMethodTarget) {
            if (DEBUG_LAYOUT) Slog.i(TAG, "Offset ime target window by the last ime window state");
            offsetInputMethodWindowLw(mLastInputMethodWindow);
        }

        final int fl = PolicyControl.getWindowFlags(win, attrs);
        final int pfl = attrs.privateFlags;
        final int sim = attrs.softInputMode;
        final int sysUiFl = PolicyControl.getSystemUiVisibility(win, null);

        final Rect pf = mTmpParentFrame;
        final Rect df = mTmpDisplayFrame;
        final Rect of = mTmpOverscanFrame;
        final Rect cf = mTmpContentFrame;
        final Rect vf = mTmpVisibleFrame;
        final Rect dcf = mTmpDecorFrame;
        final Rect sf = mTmpStableFrame;
        Rect osf = null;
        dcf.setEmpty();

        /// M: add for fullscreen switch feature @{
        if (mSupportFullscreenSwitch) {
            applyFullScreenSwitch(win);
        }
        /// @}
        final boolean hasNavBar = (isDefaultDisplay && mHasNavigationBar
                && mNavigationBar != null && mNavigationBar.isVisibleLw());

        final int adjust = sim & SOFT_INPUT_MASK_ADJUST;

        if (isDefaultDisplay) {
            sf.set(mStableLeft, mStableTop, mStableRight, mStableBottom);
        } else {
            sf.set(mOverscanLeft, mOverscanTop, mOverscanRight, mOverscanBottom);
        }

        if (!isDefaultDisplay) {
            if (attached != null) {
                // If this window is attached to another, our display
                // frame is the same as the one we are attached to.
                setAttachedWindowFrames(win, fl, adjust, attached, true, pf, df, of, cf, vf);
            } else {
                // Give the window full screen.
                pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                pf.right = df.right = of.right = cf.right
                        = mOverscanScreenLeft + mOverscanScreenWidth;
                pf.bottom = df.bottom = of.bottom = cf.bottom
                        = mOverscanScreenTop + mOverscanScreenHeight;
            }
        } else if (attrs.type == TYPE_INPUT_METHOD) {
            pf.left = df.left = of.left = cf.left = vf.left = mDockLeft;
            pf.top = df.top = of.top = cf.top = vf.top = mDockTop;
            pf.right = df.right = of.right = cf.right = vf.right = mDockRight;
            // IM dock windows layout below the nav bar...
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            // ...with content insets above the nav bar
            cf.bottom = vf.bottom = mStableBottom;
            if (mStatusBar != null && mFocusedWindow == mStatusBar && canReceiveInput(mStatusBar)) {
                // The status bar forces the navigation bar while it's visible. Make sure the IME
                // avoids the navigation bar in that case.
                if (mNavigationBarPosition == NAV_BAR_RIGHT) {
                    pf.right = df.right = of.right = cf.right = vf.right = mStableRight;
                } else if (mNavigationBarPosition == NAV_BAR_LEFT) {
                    pf.left = df.left = of.left = cf.left = vf.left = mStableLeft;
                }
            }
            // IM dock windows always go to the bottom of the screen.
            attrs.gravity = Gravity.BOTTOM;
            mDockLayer = win.getSurfaceLayer();
        } else if (attrs.type == TYPE_VOICE_INTERACTION) {
            pf.left = df.left = of.left = mUnrestrictedScreenLeft;
            pf.top = df.top = of.top = mUnrestrictedScreenTop;
            pf.right = df.right = of.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                cf.left = mDockLeft;
                cf.top = mDockTop;
                cf.right = mDockRight;
                cf.bottom = mDockBottom;
            } else {
                cf.left = mContentLeft;
                cf.top = mContentTop;
                cf.right = mContentRight;
                cf.bottom = mContentBottom;
            }
            if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                vf.left = mCurLeft;
                vf.top = mCurTop;
                vf.right = mCurRight;
                vf.bottom = mCurBottom;
            } else {
                vf.set(cf);
            }
        } else if (win == mStatusBar) {
            pf.left = df.left = of.left = mUnrestrictedScreenLeft;
            pf.top = df.top = of.top = mUnrestrictedScreenTop;
            pf.right = df.right = of.right = mUnrestrictedScreenWidth + mUnrestrictedScreenLeft;
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenHeight + mUnrestrictedScreenTop;
            cf.left = vf.left = mStableLeft;
            cf.top = vf.top = mStableTop;
            cf.right = vf.right = mStableRight;
            vf.bottom = mStableBottom;

            if (adjust == SOFT_INPUT_ADJUST_RESIZE) {
                cf.bottom = mContentBottom;
            } else {
                cf.bottom = mDockBottom;
                vf.bottom = mContentBottom;
            }
        } else {

            // Default policy decor for the default display
            dcf.left = mSystemLeft;
            dcf.top = mSystemTop;
            dcf.right = mSystemRight;
            dcf.bottom = mSystemBottom;
            final boolean inheritTranslucentDecor = (attrs.privateFlags
                    & WindowManager.LayoutParams.PRIVATE_FLAG_INHERIT_TRANSLUCENT_DECOR) != 0;
            final boolean isAppWindow =
                    attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW &&
                    attrs.type <= WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
            final boolean topAtRest =
                    win == mTopFullscreenOpaqueWindowState && !win.isAnimatingLw();
            if (isAppWindow && !inheritTranslucentDecor && !topAtRest) {
                if ((sysUiFl & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0
                        && (fl & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0
                        && (fl & WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) == 0
                        && (fl & WindowManager.LayoutParams.
                                FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == 0
                        && (pfl & PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND) == 0) {
                    // Ensure policy decor includes status bar
                    dcf.top = mStableTop;
                }
                if ((fl & WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION) == 0
                        && (sysUiFl & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
                        && (fl & WindowManager.LayoutParams.
                                FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == 0) {
                    // Ensure policy decor includes navigation bar
                    dcf.bottom = mStableBottom;
                    dcf.right = mStableRight;
                }
            }

            if ((fl & (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR))
                    == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)) {
                /// M: Add more log at WMS
                if (DEBUG_LAYOUT) Slog.v(TAG, "layoutWindowLw(" + attrs.getTitle()
                            + "): IN_SCREEN, INSET_DECOR, sim = #" + Integer.toHexString(adjust));
                // This is the case for a normal activity window: we want it
                // to cover all of the screen space, and it can take care of
                // moving its contents to account for screen decorations that
                // intrude into that space.
                if (attached != null) {
                    // If this window is attached to another, our display
                    // frame is the same as the one we are attached to.
                    setAttachedWindowFrames(win, fl, adjust, attached, true, pf, df, of, cf, vf);
                } else {
                    if (attrs.type == TYPE_STATUS_BAR_PANEL
                            || attrs.type == TYPE_STATUS_BAR_SUB_PANEL) {
                        // Status bar panels are the only windows who can go on top of
                        // the status bar.  They are protected by the STATUS_BAR_SERVICE
                        // permission, so they have the same privileges as the status
                        // bar itself.
                        //
                        // However, they should still dodge the navigation bar if it exists.

                        pf.left = df.left = of.left = hasNavBar
                                ? mDockLeft : mUnrestrictedScreenLeft;
                        pf.top = df.top = of.top = mUnrestrictedScreenTop;
                        pf.right = df.right = of.right = hasNavBar
                                ? mRestrictedScreenLeft+mRestrictedScreenWidth
                                : mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                        pf.bottom = df.bottom = of.bottom = hasNavBar
                                ? mRestrictedScreenTop+mRestrictedScreenHeight
                                : mUnrestrictedScreenTop + mUnrestrictedScreenHeight;

                        if (DEBUG_LAYOUT) Slog.v(TAG, String.format(
                                        "Laying out status bar window: (%d,%d - %d,%d)",
                                        pf.left, pf.top, pf.right, pf.bottom));
                    } else if ((fl & FLAG_LAYOUT_IN_OVERSCAN) != 0
                            && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                        // Asking to layout into the overscan region, so give it that pure
                        // unrestricted area.
                        pf.left = df.left = of.left = mOverscanScreenLeft;
                        pf.top = df.top = of.top = mOverscanScreenTop;
                        pf.right = df.right = of.right = mOverscanScreenLeft + mOverscanScreenWidth;
                        pf.bottom = df.bottom = of.bottom = mOverscanScreenTop
                                + mOverscanScreenHeight;
                    } else if (canHideNavigationBar()
                            && (sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0
                            /// M:[ALPS01186390]Fix IPO flash issue
                            && (attrs.type == TYPE_TOP_MOST || (
                               attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW))) {
                        // Asking for layout as if the nav bar is hidden, lets the
                        // application extend into the unrestricted overscan screen area.  We
                        // only do this for application windows to ensure no window that
                        // can be above the nav bar can do this.
                        pf.left = df.left = mOverscanScreenLeft;
                        pf.top = df.top = mOverscanScreenTop;
                        pf.right = df.right = mOverscanScreenLeft + mOverscanScreenWidth;
                        pf.bottom = df.bottom = mOverscanScreenTop + mOverscanScreenHeight;
                        // We need to tell the app about where the frame inside the overscan
                        // is, so it can inset its content by that amount -- it didn't ask
                        // to actually extend itself into the overscan region.
                        of.left = mUnrestrictedScreenLeft;
                        of.top = mUnrestrictedScreenTop;
                        of.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                        of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    } else {
                        pf.left = df.left = mRestrictedOverscanScreenLeft;
                        pf.top = df.top = mRestrictedOverscanScreenTop;
                        pf.right = df.right = mRestrictedOverscanScreenLeft
                                + mRestrictedOverscanScreenWidth;
                        pf.bottom = df.bottom = mRestrictedOverscanScreenTop
                                + mRestrictedOverscanScreenHeight;
                        // We need to tell the app about where the frame inside the overscan
                        // is, so it can inset its content by that amount -- it didn't ask
                        // to actually extend itself into the overscan region.
                        of.left = mUnrestrictedScreenLeft;
                        of.top = mUnrestrictedScreenTop;
                        of.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                        of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    }

                    if ((fl & FLAG_FULLSCREEN) == 0) {
                        if (win.isVoiceInteraction()) {
                            cf.left = mVoiceContentLeft;
                            cf.top = mVoiceContentTop;
                            cf.right = mVoiceContentRight;
                            cf.bottom = mVoiceContentBottom;
                        } else {
                            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                                cf.left = mDockLeft;
                                cf.top = mDockTop;
                                cf.right = mDockRight;
                                cf.bottom = mDockBottom;
                            } else {
                                cf.left = mContentLeft;
                                cf.top = mContentTop;
                                cf.right = mContentRight;
                                cf.bottom = mContentBottom;
                            }
                        }
                    } else {
                        // Full screen windows are always given a layout that is as if the
                        // status bar and other transient decors are gone.  This is to avoid
                        // bad states when moving from a window that is not hding the
                        // status bar to one that is.
                        cf.left = mRestrictedScreenLeft;
                        cf.top = mRestrictedScreenTop;
                        cf.right = mRestrictedScreenLeft + mRestrictedScreenWidth;
                        cf.bottom = mRestrictedScreenTop + mRestrictedScreenHeight;
                    }
                    applyStableConstraints(sysUiFl, fl, cf);
                    if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                        vf.left = mCurLeft;
                        vf.top = mCurTop;
                        vf.right = mCurRight;
                        vf.bottom = mCurBottom;
                    } else {
                        vf.set(cf);
                    }
                }
            } else if ((fl & FLAG_LAYOUT_IN_SCREEN) != 0 || (sysUiFl
                    & (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)) != 0) {
                if (DEBUG_LAYOUT) Slog.v(TAG, "layoutWindowLw(" + attrs.getTitle() +
                        "): IN_SCREEN");
                // A window that has requested to fill the entire screen just
                // gets everything, period.
                if (attrs.type == TYPE_STATUS_BAR_PANEL
                        || attrs.type == TYPE_STATUS_BAR_SUB_PANEL
                        || attrs.type == TYPE_VOLUME_OVERLAY) {
                    pf.left = df.left = of.left = cf.left = hasNavBar
                            ? mDockLeft : mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = hasNavBar
                                        ? mRestrictedScreenLeft+mRestrictedScreenWidth
                                        : mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = hasNavBar
                                          ? mRestrictedScreenTop+mRestrictedScreenHeight
                                          : mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    if (DEBUG_LAYOUT) Slog.v(TAG, String.format(
                                    "Laying out IN_SCREEN status bar window: (%d,%d - %d,%d)",
                                    pf.left, pf.top, pf.right, pf.bottom));
                } else if (attrs.type == TYPE_NAVIGATION_BAR
                        || attrs.type == TYPE_NAVIGATION_BAR_PANEL) {
                    // The navigation bar has Real Ultimate Power.
                    pf.left = df.left = of.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = mUnrestrictedScreenLeft
                            + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenTop
                            + mUnrestrictedScreenHeight;
                    if (DEBUG_LAYOUT) Slog.v(TAG, String.format(
                                    "Laying out navigation bar window: (%d,%d - %d,%d)",
                                    pf.left, pf.top, pf.right, pf.bottom));
                } else if ((attrs.type == TYPE_SECURE_SYSTEM_OVERLAY
                                || attrs.type == TYPE_BOOT_PROGRESS
                                || attrs.type == TYPE_SCREENSHOT)
                        && ((fl & FLAG_FULLSCREEN) != 0)) {
                    // Fullscreen secure system overlays get what they ask for. Screenshot region
                    // selection overlay should also expand to full screen.
                    pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                    pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                    pf.right = df.right = of.right = cf.right = mOverscanScreenLeft
                            + mOverscanScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mOverscanScreenTop
                            + mOverscanScreenHeight;
                } else if (attrs.type == TYPE_BOOT_PROGRESS) {
                    // Boot progress screen always covers entire display.
                    pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                    pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                    pf.right = df.right = of.right = cf.right = mOverscanScreenLeft
                            + mOverscanScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mOverscanScreenTop
                            + mOverscanScreenHeight;
                } else if (attrs.type == TYPE_WALLPAPER) {
                    // The wallpaper also has Real Ultimate Power, but we want to tell
                    // it about the overscan area.
                    pf.left = df.left = mOverscanScreenLeft;
                    pf.top = df.top = mOverscanScreenTop;
                    pf.right = df.right = mOverscanScreenLeft + mOverscanScreenWidth;
                    pf.bottom = df.bottom = mOverscanScreenTop + mOverscanScreenHeight;
                    of.left = cf.left = mUnrestrictedScreenLeft;
                    of.top = cf.top = mUnrestrictedScreenTop;
                    of.right = cf.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                    of.bottom = cf.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                } else if ((fl & FLAG_LAYOUT_IN_OVERSCAN) != 0
                        && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                        && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                    // Asking to layout into the overscan region, so give it that pure
                    // unrestricted area.
                    pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                    pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                    pf.right = df.right = of.right = cf.right
                            = mOverscanScreenLeft + mOverscanScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom
                            = mOverscanScreenTop + mOverscanScreenHeight;
                } else if (canHideNavigationBar()
                        && (sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0
                        && (attrs.type == TYPE_STATUS_BAR
                            || attrs.type == TYPE_TOAST
                            || attrs.type == TYPE_DOCK_DIVIDER
                            || attrs.type == TYPE_VOICE_INTERACTION_STARTING
                            || (attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW))) {
                    // Asking for layout as if the nav bar is hidden, lets the
                    // application extend into the unrestricted screen area.  We
                    // only do this for application windows (or toasts) to ensure no window that
                    // can be above the nav bar can do this.
                    // XXX This assumes that an app asking for this will also
                    // ask for layout in only content.  We can't currently figure out
                    // what the screen would be if only laying out to hide the nav bar.
                    pf.left = df.left = of.left = cf.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mUnrestrictedScreenLeft
                            + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mUnrestrictedScreenTop
                            + mUnrestrictedScreenHeight;
                } else if ((sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0) {
                    pf.left = df.left = of.left = mRestrictedScreenLeft;
                    pf.top = df.top = of.top  = mRestrictedScreenTop;
                    pf.right = df.right = of.right = mRestrictedScreenLeft + mRestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = mRestrictedScreenTop
                            + mRestrictedScreenHeight;
                    if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                        cf.left = mDockLeft;
                        cf.top = mDockTop;
                        cf.right = mDockRight;
                        cf.bottom = mDockBottom;
                    } else {
                        cf.left = mContentLeft;
                        cf.top = mContentTop;
                        cf.right = mContentRight;
                        cf.bottom = mContentBottom;
                    }
                }
                // BEGIN leo_liao@asus.com, One-hand control
                else if (attrs.type == WindowManager.LayoutParams.TYPE_MAGNIFICATION_OVERLAY
                        && (fl & FLAG_LAYOUT_NO_LIMITS) != 0) {
                    pf.left = df.left = of.left = cf.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mUnrestrictedScreenLeft
                            + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mUnrestrictedScreenTop
                            + mUnrestrictedScreenHeight;
                }
                // END leo_liao@asus.com
                else {
                    pf.left = df.left = of.left = cf.left = mRestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mRestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mRestrictedScreenLeft
                            + mRestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mRestrictedScreenTop
                            + mRestrictedScreenHeight;
                }

                applyStableConstraints(sysUiFl, fl, cf);

                if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                    vf.left = mCurLeft;
                    vf.top = mCurTop;
                    vf.right = mCurRight;
                    vf.bottom = mCurBottom;
                } else {
                    vf.set(cf);
                }
            } else if (attached != null) {
                if (DEBUG_LAYOUT) Slog.v(TAG, "layoutWindowLw(" + attrs.getTitle() +
                        "): attached to " + attached);
                // A child window should be placed inside of the same visible
                // frame that its parent had.
                setAttachedWindowFrames(win, fl, adjust, attached, false, pf, df, of, cf, vf);
            } else {
                if (DEBUG_LAYOUT) Slog.v(TAG, "layoutWindowLw(" + attrs.getTitle() +
                        "): normal window");
                // Otherwise, a normal window must be placed inside the content
                // of all screen decorations.
                if (attrs.type == TYPE_STATUS_BAR_PANEL || attrs.type == TYPE_VOLUME_OVERLAY) {
                    // Status bar panels and the volume dialog are the only windows who can go on
                    // top of the status bar.  They are protected by the STATUS_BAR_SERVICE
                    // permission, so they have the same privileges as the status
                    // bar itself.
                    pf.left = df.left = of.left = cf.left = mRestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mRestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mRestrictedScreenLeft
                            + mRestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mRestrictedScreenTop
                            + mRestrictedScreenHeight;
                } else if (attrs.type == TYPE_TOAST || attrs.type == TYPE_SYSTEM_ALERT) {
                    // These dialogs are stable to interim decor changes.
                    pf.left = df.left = of.left = cf.left = mStableLeft;
                    pf.top = df.top = of.top = cf.top = mStableTop;
                    pf.right = df.right = of.right = cf.right = mStableRight;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mStableBottom;
                } else {
                    pf.left = mContentLeft;
                    pf.top = mContentTop;
                    pf.right = mContentRight;
                    pf.bottom = mContentBottom;
                    if (win.isVoiceInteraction()) {
                        df.left = of.left = cf.left = mVoiceContentLeft;
                        df.top = of.top = cf.top = mVoiceContentTop;
                        df.right = of.right = cf.right = mVoiceContentRight;
                        df.bottom = of.bottom = cf.bottom = mVoiceContentBottom;
                    } else if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                        df.left = of.left = cf.left = mDockLeft;
                        df.top = of.top = cf.top = mDockTop;
                        df.right = of.right = cf.right = mDockRight;
                        df.bottom = of.bottom = cf.bottom = mDockBottom;
                    } else {
                        df.left = of.left = cf.left = mContentLeft;
                        df.top = of.top = cf.top = mContentTop;
                        df.right = of.right = cf.right = mContentRight;
                        df.bottom = of.bottom = cf.bottom = mContentBottom;
                    }
                    if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                        vf.left = mCurLeft;
                        vf.top = mCurTop;
                        vf.right = mCurRight;
                        vf.bottom = mCurBottom;
                    } else {
                        vf.set(cf);
                    }
                }
            }
        }

        // TYPE_SYSTEM_ERROR is above the NavigationBar so it can't be allowed to extend over it.
        // Also, we don't allow windows in multi-window mode to extend out of the screen.
        if ((fl & FLAG_LAYOUT_NO_LIMITS) != 0 && attrs.type != TYPE_SYSTEM_ERROR
                && !win.isInMultiWindowMode()) {
            df.left = df.top = -10000;
            df.right = df.bottom = 10000;
            if (attrs.type != TYPE_WALLPAPER) {
                of.left = of.top = cf.left = cf.top = vf.left = vf.top = -10000;
                of.right = of.bottom = cf.right = cf.bottom = vf.right = vf.bottom = 10000;
            }
        }

        // If the device has a chin (e.g. some watches), a dead area at the bottom of the screen we
        // need to provide information to the clients that want to pretend that you can draw there.
        // We only want to apply outsets to certain types of windows. For example, we never want to
        // apply the outsets to floating dialogs, because they wouldn't make sense there.
        final boolean useOutsets = shouldUseOutsets(attrs, fl);
        if (isDefaultDisplay && useOutsets) {
            osf = mTmpOutsetFrame;
            osf.set(cf.left, cf.top, cf.right, cf.bottom);
            int outset = ScreenShapeHelper.getWindowOutsetBottomPx(mContext.getResources());
            if (outset > 0) {
                int rotation = mDisplayRotation;
                if (rotation == Surface.ROTATION_0) {
                    osf.bottom += outset;
                } else if (rotation == Surface.ROTATION_90) {
                    osf.right += outset;
                } else if (rotation == Surface.ROTATION_180) {
                    osf.top -= outset;
                } else if (rotation == Surface.ROTATION_270) {
                    osf.left -= outset;
                }
                if (DEBUG_LAYOUT) Slog.v(TAG, "applying bottom outset of " + outset
                        + " with rotation " + rotation + ", result: " + osf);
            }
        }

        if (DEBUG_LAYOUT) Slog.v(TAG, "Compute frame " + attrs.getTitle()
                + ": sim=#" + Integer.toHexString(sim)
                + " attach=" + attached + " type=" + attrs.type
                + String.format(" flags=0x%08x", fl)
                + " pf=" + pf.toShortString() + " df=" + df.toShortString()
                + " of=" + of.toShortString()
                + " cf=" + cf.toShortString() + " vf=" + vf.toShortString()
                + " dcf=" + dcf.toShortString()
                + " sf=" + sf.toShortString()
                + " osf=" + (osf == null ? "null" : osf.toShortString()));

        /// M: add for fullscreen switch feature @{
        if (mSupportFullscreenSwitch) {
            resetFullScreenSwitch(win, of);
        }
        /// @}

        win.computeFrameLw(pf, df, of, cf, vf, dcf, sf, osf);

        // Dock windows carve out the bottom of the screen, so normal windows
        // can't appear underneath them.
        if (attrs.type == TYPE_INPUT_METHOD && win.isVisibleOrBehindKeyguardLw()
                && win.isDisplayedLw() && !win.getGivenInsetsPendingLw()) {
            setLastInputMethodWindowLw(null, null);
            offsetInputMethodWindowLw(win);
        }
        if (attrs.type == TYPE_VOICE_INTERACTION && win.isVisibleOrBehindKeyguardLw()
                && !win.getGivenInsetsPendingLw()) {
            offsetVoiceInputWindowLw(win);
        }

        // BEGIN: archie_huang@asus.com
        // For feature: Colorful Navigation Bar
        updateNavigationBarColorLw(win);
        // END: archie_haung@asus.com
    }

    private void offsetInputMethodWindowLw(WindowState win) {
        int top = Math.max(win.getDisplayFrameLw().top, win.getContentFrameLw().top);
        top += win.getGivenContentInsetsLw().top;
        if (mContentBottom > top) {
            mContentBottom = top;
        }
        if (mVoiceContentBottom > top) {
            mVoiceContentBottom = top;
        }
        top = win.getVisibleFrameLw().top;
        top += win.getGivenVisibleInsetsLw().top;
        if (mCurBottom > top) {
            mCurBottom = top;
        }
        if (DEBUG_LAYOUT) Slog.v(TAG, "Input method: mDockBottom="
                + mDockBottom + " mContentBottom="
                + mContentBottom + " mCurBottom=" + mCurBottom);
    }

    private void offsetVoiceInputWindowLw(WindowState win) {
        int top = Math.max(win.getDisplayFrameLw().top, win.getContentFrameLw().top);
        top += win.getGivenContentInsetsLw().top;
        if (mVoiceContentBottom > top) {
            mVoiceContentBottom = top;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void finishLayoutLw() {
        return;
    }

    private void addToHideLockScreenAttrs(WindowManager.LayoutParams attrs) {
        boolean add = true;
        int size = mHideLockScreenAttrs.size();
        for (int i = 0; i < size; i++) {
            if (mHideLockScreenAttrs.get(i) == attrs) {
                // already has this attr, don't add again
                add = false;
                break;
            }
        }
        if (add) {
            mHideLockScreenAttrs.add(attrs);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void beginPostLayoutPolicyLw(int displayWidth, int displayHeight) {
        mTopFullscreenOpaqueWindowState = null;
        mTopFullscreenOpaqueOrDimmingWindowState = null;
        mTopDockedOpaqueWindowState = null;
        mTopDockedOpaqueOrDimmingWindowState = null;
        mAppsToBeHidden.clear();
        mAppsThatDismissKeyguard.clear();
        mForceStatusBar = false;
        mForceStatusBarFromKeyguard = false;
        mForceStatusBarTransparent = false;
        mForcingShowNavBar = false;
        mForcingShowNavBarLayer = -1;

        mHideLockScreen = false;
        mAllowLockscreenWhenOn = false;
        mDismissKeyguard = DISMISS_KEYGUARD_NONE;
        mShowingLockscreen = false;
        mShowingDream = false;
        mWinShowWhenLocked = null;
        mKeyguardSecure = isKeyguardSecure(mCurrentUserId);
        mKeyguardSecureIncludingHidden = mKeyguardSecure
                && (mKeyguardDelegate != null && mKeyguardDelegate.isShowing());
        //begin:hungjie_tseng@asus.com
        mHasAlwaysonWindow = false;
        //end:hungjie_tseng@asus.com

        mHideLockScreenAttrs.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void applyPostLayoutPolicyLw(WindowState win, WindowManager.LayoutParams attrs,
            WindowState attached) {
        if (DEBUG_LAYOUT) Slog.i(TAG, "Win " + win + ": isVisibleOrBehindKeyguardLw="
                + win.isVisibleOrBehindKeyguardLw());
        final int fl = PolicyControl.getWindowFlags(win, attrs);
        if (mTopFullscreenOpaqueWindowState == null
                && win.isVisibleLw() && attrs.type == TYPE_INPUT_METHOD) {
            mForcingShowNavBar = true;
            mForcingShowNavBarLayer = win.getSurfaceLayer();
        }
        if (attrs.type == TYPE_STATUS_BAR) {
            if ((attrs.privateFlags & PRIVATE_FLAG_KEYGUARD) != 0) {
                mForceStatusBarFromKeyguard = true;
                mShowingLockscreen = true;
            }
            if ((attrs.privateFlags & PRIVATE_FLAG_FORCE_STATUS_BAR_VISIBLE_TRANSPARENT) != 0) {
                mForceStatusBarTransparent = true;
            }
        }

        boolean appWindow = attrs.type >= FIRST_APPLICATION_WINDOW
                && attrs.type < FIRST_SYSTEM_WINDOW;
        final boolean showWhenLocked = (fl & FLAG_SHOW_WHEN_LOCKED) != 0;
        final boolean dismissKeyguard = (fl & FLAG_DISMISS_KEYGUARD) != 0;
        final int stackId = win.getStackId();

// BEGIN: oliver_hu@asus.com
        //begin:hungjie_tseng@asus.com
        //begin:hungjie_tseng@asus.com, add for alwayons		
        boolean isAlwaysOnFullWindow = false;        
        if (Build.FEATURES.ENABLE_ALWAYS_ON) {
            isAlwaysOnFullWindow = isAlwaysOnWindow(win);
            if(isAlwaysOnFullWindow) {
                mHasAlwaysonWindow = true;
            }       
        }
        //end:hungjie_tseng@asus.com, add for alwayons
// END: oliver_hu@asus.com
        //Begin:HJ@asus.com.Adding for inadvertentTouch
        if(Build.FEATURES.ENABLE_INADVERTENTTOUCH) {
            if(attrs.type == TYPE_SYSTEM_ERROR && attrs.format == INADVERTENTTOUCH_WINDOW_FORMAT
                    && win.getOwningUid() == Process.SYSTEM_UID) {
                attrs.userActivityTimeout = 0;
            }
        }
        //End:HJ@asus.com.Adding for inadvertentTouch

        if (mTopFullscreenOpaqueWindowState == null &&
                win.isVisibleOrBehindKeyguardLw() && !win.isGoneForLayoutLw()) {
            if ((fl & FLAG_FORCE_NOT_FULLSCREEN) != 0) {
                if ((attrs.privateFlags & PRIVATE_FLAG_KEYGUARD) != 0) {
                    mForceStatusBarFromKeyguard = true;
                } else {
                    mForceStatusBar = true;
                }
            }
            if (attrs.type == TYPE_DREAM) {
                // If the lockscreen was showing when the dream started then wait
                // for the dream to draw before hiding the lockscreen.
                if (!mDreamingLockscreen
                        || (win.isVisibleLw() && win.hasDrawnLw())) {
                    mShowingDream = true;
                    appWindow = true;
                }
            }

            final IApplicationToken appToken = win.getAppToken();

            // For app windows that are not attached, we decide if all windows in the app they
            // represent should be hidden or if we should hide the lockscreen. For attached app
            // windows we defer the decision to the window it is attached to.
            if (appWindow && attached == null) {
                if (showWhenLocked) {
                    // Remove any previous windows with the same appToken.
                    mAppsToBeHidden.remove(appToken);
                    mAppsThatDismissKeyguard.remove(appToken);
                    if (mAppsToBeHidden.isEmpty()) {
                        if (dismissKeyguard && !mKeyguardSecure) {
                            mAppsThatDismissKeyguard.add(appToken);
                        } else if ((win.isDrawnLw() || win.hasAppShownWindows()) && !isAlwaysOnFullWindow) {
                            mWinShowWhenLocked = win;
                            mHideLockScreen = true;
                            mForceStatusBarFromKeyguard = false;

                            // save this window's attributes that may be modified later
                            addToHideLockScreenAttrs(attrs);
                        }
                    }
                } else if (dismissKeyguard) {
                    if (mKeyguardSecure) {
                        mAppsToBeHidden.add(appToken);
                    } else {
                        mAppsToBeHidden.remove(appToken);
                    }
                    mAppsThatDismissKeyguard.add(appToken);
                } else {
                    mAppsToBeHidden.add(appToken);
                }
                if (isFullscreen(attrs) && StackId.normallyFullscreenWindows(stackId) && !isAlwaysOnFullWindow) {
                    if (DEBUG_LAYOUT) Slog.v(TAG, "Fullscreen window: " + win);
                    mTopFullscreenOpaqueWindowState = win;
                    if (mTopFullscreenOpaqueOrDimmingWindowState == null) {
                        mTopFullscreenOpaqueOrDimmingWindowState = win;
                    }
                    if (!mAppsThatDismissKeyguard.isEmpty() &&
                            mDismissKeyguard == DISMISS_KEYGUARD_NONE) {
                        if (DEBUG_LAYOUT) Slog.v(TAG,
                                "Setting mDismissKeyguard true by win " + win);
                        mDismissKeyguard = (mWinDismissingKeyguard == win
                                && mSecureDismissingKeyguard == mKeyguardSecure)
                                ? DISMISS_KEYGUARD_CONTINUE : DISMISS_KEYGUARD_START;
                        mWinDismissingKeyguard = win;
                        mSecureDismissingKeyguard = mKeyguardSecure;
                        mForceStatusBarFromKeyguard = mShowingLockscreen && mKeyguardSecure;
                    } else if (mAppsToBeHidden.isEmpty() && showWhenLocked
                            && (win.isDrawnLw() || win.hasAppShownWindows())) {
                        if (DEBUG_LAYOUT) Slog.v(TAG,
                                "Setting mHideLockScreen to true by win " + win);
                        mHideLockScreen = true;
                        mForceStatusBarFromKeyguard = false;

                        // save this window's attributes that may be modified later
                        addToHideLockScreenAttrs(attrs);
                    }
                    if ((fl & FLAG_ALLOW_LOCK_WHILE_SCREEN_ON) != 0) {
                        mAllowLockscreenWhenOn = true;
                    }
                }

                if (!mKeyguardHidden && mWinShowWhenLocked != null &&
                        mWinShowWhenLocked.getAppToken() != win.getAppToken() &&
                        (attrs.flags & FLAG_SHOW_WHEN_LOCKED) == 0) {
                    win.hideLw(false);
                }
            }
        } else if (mTopFullscreenOpaqueWindowState == null && mWinShowWhenLocked == null) {
            // No TopFullscreenOpaqueWindow is showing, but we found a SHOW_WHEN_LOCKED window
            // that is being hidden in an animation - keep the
            // keyguard hidden until the new window shows up and
            // we know whether to show the keyguard or not.
            if (win.isAnimatingLw() && appWindow && showWhenLocked && mKeyguardHidden && !isAlwaysOnFullWindow) {
                mHideLockScreen = true;
                mWinShowWhenLocked = win;
            }
        }

        // Keep track of the window if it's dimming but not necessarily fullscreen.
        final boolean reallyVisible = win.isVisibleOrBehindKeyguardLw() && !win.isGoneForLayoutLw();
        if (mTopFullscreenOpaqueOrDimmingWindowState == null &&  reallyVisible
                && win.isDimming() && StackId.normallyFullscreenWindows(stackId) && !isAlwaysOnFullWindow) {
            mTopFullscreenOpaqueOrDimmingWindowState = win;
        }

        // We need to keep track of the top "fullscreen" opaque window for the docked stack
        // separately, because both the "real fullscreen" opaque window and the one for the docked
        // stack can control View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.
        if (mTopDockedOpaqueWindowState == null && reallyVisible && appWindow && attached == null
                && isFullscreen(attrs) && stackId == DOCKED_STACK_ID) {
            mTopDockedOpaqueWindowState = win;
            if (mTopDockedOpaqueOrDimmingWindowState == null) {
                mTopDockedOpaqueOrDimmingWindowState = win;
            }
        }

        // Also keep track of any windows that are dimming but not necessarily fullscreen in the
        // docked stack.
        if (mTopDockedOpaqueOrDimmingWindowState == null && reallyVisible && win.isDimming()
                && stackId == DOCKED_STACK_ID) {
            mTopDockedOpaqueOrDimmingWindowState = win;
        }
    }

    private boolean isFullscreen(WindowManager.LayoutParams attrs) {
        return attrs.x == 0 && attrs.y == 0
                && attrs.width == WindowManager.LayoutParams.MATCH_PARENT
                && attrs.height == WindowManager.LayoutParams.MATCH_PARENT;
    }

    /** {@inheritDoc} */
    @Override
    public int finishPostLayoutPolicyLw() {
        if (mWinShowWhenLocked != null && mTopFullscreenOpaqueWindowState != null &&
                mWinShowWhenLocked.getAppToken() != mTopFullscreenOpaqueWindowState.getAppToken()
                && isKeyguardLocked()) {
            // A dialog is dismissing the keyguard. Put the wallpaper behind it and hide the
            // fullscreen window.
            // TODO: Make sure FLAG_SHOW_WALLPAPER is restored when dialog is dismissed. Or not.
            mWinShowWhenLocked.getAttrs().flags |= FLAG_SHOW_WALLPAPER;
            /// M: Check null object. {@
            if (mTopFullscreenOpaqueWindowState != null) {
                mTopFullscreenOpaqueWindowState.hideLw(false);
            }
            /// @}
            mTopFullscreenOpaqueWindowState = mWinShowWhenLocked;
        }

        int changes = 0;
        boolean topIsFullscreen = false;

        final WindowManager.LayoutParams lp = (mTopFullscreenOpaqueWindowState != null)
                ? mTopFullscreenOpaqueWindowState.getAttrs()
                : null;

        // If we are not currently showing a dream then remember the current
        // lockscreen state.  We will use this to determine whether the dream
        // started while the lockscreen was showing and remember this state
        // while the dream is showing.
        if (!mShowingDream) {
            mDreamingLockscreen = mShowingLockscreen;
            if (mDreamingSleepTokenNeeded) {
                mDreamingSleepTokenNeeded = false;
                mHandler.obtainMessage(MSG_UPDATE_DREAMING_SLEEP_TOKEN, 0, 1).sendToTarget();
            }
        } else {
            if (!mDreamingSleepTokenNeeded) {
                mDreamingSleepTokenNeeded = true;
                mHandler.obtainMessage(MSG_UPDATE_DREAMING_SLEEP_TOKEN, 1, 1).sendToTarget();
            }
        }

        if (mStatusBar != null) {
            if (DEBUG_LAYOUT) Slog.i(TAG, "force=" + mForceStatusBar
                    + " forcefkg=" + mForceStatusBarFromKeyguard
                    + " top=" + mTopFullscreenOpaqueWindowState);
            boolean shouldBeTransparent = mForceStatusBarTransparent
                    && !mForceStatusBar
                    && !mForceStatusBarFromKeyguard;
            if (!shouldBeTransparent) {
                mStatusBarController.setShowTransparent(false /* transparent */);
            } else if (!mStatusBar.isVisibleLw()) {
                mStatusBarController.setShowTransparent(true /* transparent */);
            }

            WindowManager.LayoutParams statusBarAttrs = mStatusBar.getAttrs();
            boolean statusBarExpanded = statusBarAttrs.height == MATCH_PARENT
                    && statusBarAttrs.width == MATCH_PARENT
                    /// M: [ALPS02861530] show statusbar only when the top isn't the dream type
                    && (mTopFullscreenOpaqueWindowState != null ?
                        mTopFullscreenOpaqueWindowState.getAttrs().type != TYPE_DREAM : true);
            if ((mForceStatusBar || mForceStatusBarFromKeyguard || mForceStatusBarTransparent
                    || statusBarExpanded) && !mHasAlwaysonWindow) {
                if (DEBUG_LAYOUT) Slog.v(TAG, "Showing status bar: forced");
                if (mStatusBarController.setBarShowingLw(true)) {
                    if(Build.FEATURES.ENABLE_ALWAYS_ON) {
                        Slog.d(TAG,"FINISH_LAYOUT_REDO_LAYOUT && FINISH_LAYOUT_REDO_WALLPAPER");
                        changes |= FINISH_LAYOUT_REDO_LAYOUT
                                | FINISH_LAYOUT_REDO_WALLPAPER;
                    } else {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                }
                // Maintain fullscreen layout until incoming animation is complete.
                topIsFullscreen = mTopIsFullscreen && mStatusBar.isAnimatingLw();
                // Transient status bar on the lockscreen is not allowed
                if (mForceStatusBarFromKeyguard && mStatusBarController.isTransientShowing()) {
                    mStatusBarController.updateVisibilityLw(false /*transientAllowed*/,
                            mLastSystemUiFlags, mLastSystemUiFlags);
                }
                // BEGIN: archie_huang@asus.com
                // For feature: Navigation visibility control - Hide forever
                if (Build.FEATURES.ENABLE_NAV_VIS_CTRL
                         && statusBarExpanded && mNavigationBar != null && canShowNavigationBar()) {
                    if (mNavigationBarController.setBarShowingLw(true)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                }
                // END: archie_huang@asus.com
            } else if (mTopFullscreenOpaqueWindowState != null) {
                final int fl = PolicyControl.getWindowFlags(null, lp);
                if (localLOGV) {
                    Slog.d(TAG, "frame: " + mTopFullscreenOpaqueWindowState.getFrameLw()
                            + " shown position: "
                            + mTopFullscreenOpaqueWindowState.getShownPositionLw());
                    Slog.d(TAG, "attr: " + mTopFullscreenOpaqueWindowState.getAttrs()
                            + " lp.flags=0x" + Integer.toHexString(fl));
                }
                topIsFullscreen = (fl & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
                        || (mLastSystemUiFlags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
                // The subtle difference between the window for mTopFullscreenOpaqueWindowState
                // and mTopIsFullscreen is that mTopIsFullscreen is set only if the window
                // has the FLAG_FULLSCREEN set.  Not sure if there is another way that to be the
                // case though.
                if (mStatusBarController.isTransientShowing()) {
                    if (mStatusBarController.setBarShowingLw(true)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                } else if (topIsFullscreen
                        && !mWindowManagerInternal.isStackVisible(FREEFORM_WORKSPACE_STACK_ID)
                        && !mWindowManagerInternal.isStackVisible(DOCKED_STACK_ID)) {
                    if (DEBUG_LAYOUT) Slog.v(TAG, "** HIDING status bar");
                    if (mStatusBarController.setBarShowingLw(false)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    } else {
                        if (DEBUG_LAYOUT) Slog.v(TAG, "Status bar already hiding");
                    }
                }
                //Begin:hungjie_tseng@asus.com
                else if(Build.FEATURES.ENABLE_ALWAYS_ON && mHasAlwaysonWindow) {
                    if (DEBUG_LAYOUT)
                        Slog.v(TAG, "** HIDING status bar, because alwaysonwindow show");
                    if (mStatusBarController.setBarShowingLw(false)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    } else {
                        if (DEBUG_LAYOUT) Slog.v(TAG, "Status bar already hiding");
                    }
                }
                //End:hungjie_tseng@asus.com
                else {
                    if (DEBUG_LAYOUT) Slog.v(TAG, "** SHOWING status bar: top is not fullscreen");
                    if (mStatusBarController.setBarShowingLw(true)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                }
            }
        }

        if (mTopIsFullscreen != topIsFullscreen) {
            if (!topIsFullscreen) {
                // Force another layout when status bar becomes fully shown.
                changes |= FINISH_LAYOUT_REDO_LAYOUT;
            }
            mTopIsFullscreen = topIsFullscreen;
        }

        // Hide the key guard if a visible window explicitly specifies that it wants to be
        // displayed when the screen is locked.
        if (mKeyguardDelegate != null && mStatusBar != null) {
            if (localLOGV) Slog.v(TAG, "finishPostLayoutPolicyLw: mHideKeyguard="
                    + mHideLockScreen
                    /// M: Add more log at WMS @{
                    + " mDismissKeyguard=" + mDismissKeyguard
                    + " mKeyguardDelegate.isSecure()= "
                    + mKeyguardDelegate.isSecure(mCurrentUserId));
                    /// @}
            if (mDismissKeyguard != DISMISS_KEYGUARD_NONE && !mKeyguardSecure) {
                mKeyguardHidden = true;
                handleKeyguardScreenStatusMessage(MSG_UPDATE_KEYGUARD_SCREEN_STATUS,"hide");
                if (setKeyguardOccludedLw(true)) {
                    changes |= FINISH_LAYOUT_REDO_LAYOUT
                            | FINISH_LAYOUT_REDO_CONFIG
                            | FINISH_LAYOUT_REDO_WALLPAPER;
                }
                if (mKeyguardDelegate.isShowing()) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mKeyguardDelegate.keyguardDone(false, false);
                        }
                    });
                }
            } else if (mHideLockScreen) {

                if (mKeyguardDelegate.isShowing()) {
                    // set userActivityTimeout attribute for the windows (eg. Line
                    // notification window) to 0 so the minimum support display timeout is
                    // used.
                    int size = mHideLockScreenAttrs.size();
                    for (int i = 0; i < size; i++) {
                        mHideLockScreenAttrs.get(i).userActivityTimeout = 0;
                    }
                }

                mKeyguardHidden = true;
                handleKeyguardScreenStatusMessage(MSG_UPDATE_KEYGUARD_SCREEN_STATUS,"hide");
                mWinDismissingKeyguard = null;
                if (setKeyguardOccludedLw(true)) {
                    changes |= FINISH_LAYOUT_REDO_LAYOUT
                            | FINISH_LAYOUT_REDO_CONFIG
                            | FINISH_LAYOUT_REDO_WALLPAPER;
                }
            } else if (mDismissKeyguard != DISMISS_KEYGUARD_NONE) {
                mKeyguardHidden = false;
                handleKeyguardScreenStatusMessage(MSG_UPDATE_KEYGUARD_SCREEN_STATUS,"show");
                if (setKeyguardOccludedLw(false)) {
                    changes |= FINISH_LAYOUT_REDO_LAYOUT
                            | FINISH_LAYOUT_REDO_CONFIG
                            | FINISH_LAYOUT_REDO_WALLPAPER;
                }
                if (mDismissKeyguard == DISMISS_KEYGUARD_START) {
                    // Only launch the next keyguard unlock window once per window.
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mKeyguardDelegate.dismiss();
                        }
                    });
                }
            } else {
                mWinDismissingKeyguard = null;
                mSecureDismissingKeyguard = false;
                mKeyguardHidden = false;
                handleKeyguardScreenStatusMessage(MSG_UPDATE_KEYGUARD_SCREEN_STATUS,"show");
                if (setKeyguardOccludedLw(false)) {
                    changes |= FINISH_LAYOUT_REDO_LAYOUT
                            | FINISH_LAYOUT_REDO_CONFIG
                            | FINISH_LAYOUT_REDO_WALLPAPER;
                }
            }
        }

        if ((updateSystemUiVisibilityLw()&SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            // If the navigation bar has been hidden or shown, we need to do another
            // layout pass to update that window.
            changes |= FINISH_LAYOUT_REDO_LAYOUT;
        }

        // update since mAllowLockscreenWhenOn might have changed
        updateLockScreenTimeout();
        return changes;
    }

    /**
     * Updates the occluded state of the Keyguard.
     *
     * @return Whether the flags have changed and we have to redo the layout.
     */
    private boolean setKeyguardOccludedLw(boolean isOccluded) {
        boolean wasOccluded = mKeyguardOccluded;
        boolean showing = mKeyguardDelegate.isShowing();
        if (wasOccluded && !isOccluded && showing) {
            mKeyguardOccluded = false;
            mKeyguardDelegate.setOccluded(false);
            mStatusBar.getAttrs().privateFlags |= PRIVATE_FLAG_KEYGUARD;
            return true;
        } else if (!wasOccluded && isOccluded && showing) {
            mKeyguardOccluded = true;
            mKeyguardDelegate.setOccluded(true);
            mStatusBar.getAttrs().privateFlags &= ~PRIVATE_FLAG_KEYGUARD;
            mStatusBar.getAttrs().flags &= ~FLAG_SHOW_WALLPAPER;
            return true;
        } else {
            return false;
        }
    }

    private boolean isStatusBarKeyguard() {
        return mStatusBar != null
                && (mStatusBar.getAttrs().privateFlags & PRIVATE_FLAG_KEYGUARD) != 0;
    }

    @Override
    public boolean allowAppAnimationsLw() {
        if (isStatusBarKeyguard() || mShowingDream) {
            // If keyguard or dreams is currently visible, no reason to animate behind it.
            return false;
        }
        return true;
    }

    @Override
    public int focusChangedLw(WindowState lastFocus, WindowState newFocus) {
        mFocusedWindow = newFocus;

        // BEGIN: archie_huang@asus.com
        // For feature: Colorful Navigation Bar
        if (newFocus != null) {
            updateNavigationBarColorLw(newFocus);
        }
        // END: archie_huang@asus.com

        if ((updateSystemUiVisibilityLw()&SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            // If the navigation bar has been hidden or shown, we need to do another
            // layout pass to update that window.
            return FINISH_LAYOUT_REDO_LAYOUT;
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void notifyLidSwitchChanged(long whenNanos, boolean lidOpen) {
    //modified by lizusheng@wind-mobi.com 20161124 start		
        if ( Settings.System.getInt(mContext.getContentResolver(),
                  "HallSensorTest",0) == 1) {
                  Log.d(TAG, "return for HallSensorTest in Emode ");
            return ;
        } 
        //modified by lizusheng@wind-mobi.com 20161124 end		
        //+++Yuchen_Chang
        boolean absent = adjustLidStateByHardwareFeature();
        Log.d(TAG, "notifyLidSwitchChanged, lidOpen:" + lidOpen +
                   " mHasDockFeature:" + mHasDockFeature +
                   " mHasKeyboardFeature:" + mHasKeyboardFeature +
                   " mHasHallSensorFeature:" + mHasHallSensorFeature +
                   " mLidState:"+ mLidState);
        if (absent) return;

        // lid changed state
        final int newLidState = lidOpen ? LID_OPEN : LID_CLOSED;
        if (newLidState == mLidState) {
            return;
        }
        
        mUnlockScreenOnWakingUp=false;//add by jeson_li for cover,TT-722917
        mLidState = newLidState;

        //Begin:HJ@asus.com.Adding for inadvertentTouch
        if (Build.FEATURES.ENABLE_INADVERTENTTOUCH && Build.FEATURES.HAS_TRANSCOVER) {
            Slog.d(TAG, "<notifyLidSwitchChanged>notifyLid switch");
            if (mInadvertentTouchController == null) {
                mInadvertentTouchController = LocalServices.getService(InadvertentTouchControllerInternal.class);
                Slog.d(TAG, "<notifyLidSwitchChanged> GetLocalService mInadvertentTouchController: " + mInadvertentTouchController);
            }
            if (mInadvertentTouchController != null) {
                mInadvertentTouchController.notifyLidSwitchState(mLidState);
            }
        }
        //End:HJ@asus.com.Adding for inadvertentTouch
        /**
         * ASUS Proximity sensor framework  +++ "[Sensors_framework] Add for Phone call check flip state"
         */
        mPowerManager.notifyLidSwitchState(mLidState);
        /* ASUS Proximity sensor framework --- */

        applyLidSwitchState();
        updateRotation(true);
        
        boolean isPrivateUser=(android.os.Build.FEATURES.HAS_TRANSCOVER?isPrivateUser():false);//add by jeson_li for cover,TT-693817
        if (lidOpen) {
			// +++ add automatic unlock control by jeson_li@20141130
            if(android.os.Build.FEATURES.HAS_TRANSCOVER){
                if(isPrivateUser){
                    mUnlockScreenOnWakingUp=true;
                }else{
                    if(mHasTranscoverInfoFeature){//cover with hole
                        mUnlockScreenOnWakingUp=mTranscoverAutomaticUnlock;
                    }else{//cover without hole
                        mUnlockScreenOnWakingUp=(mIsTranscoverEnabled&&mTranscoverAutomaticUnlock);
                    }
                }
            }else{
                //original code to unlock
                mUnlockScreenOnWakingUp=true;
            }
            if(mUnlockScreenOnWakingUp&&mKeyguardDelegate!=null&&mHandler!=null){
                Log.i(TAG, "unlock non-secure lock screen for notifyLidSwitchChanged");
                mHandler.removeMessages(MSG_FLIPCOVER_UNLOCK_LOCKSCREEN);
                mHandler.sendEmptyMessage(MSG_FLIPCOVER_UNLOCK_LOCKSCREEN);
            }
            // --- jeson_li
            //+++ jeson_li@20150106 fix TT-526826
            if(android.os.Build.FEATURES.HAS_TRANSCOVER){
                boolean toWakeup=(isPrivateUser||(mHasTranscoverInfoFeature?(mIsTranscoverEnabled||mTranscoverAutomaticUnlock):mIsTranscoverEnabled));
                if(toWakeup){
                    Log.i(TAG, "wakeup screen for opening cover");
                    wakeUp(SystemClock.uptimeMillis(), mAllowTheaterModeWakeFromLidSwitch,
                        "android.policy:LID");
                }
            //-- jeson_li
            }else{//aosp
                wakeUp(SystemClock.uptimeMillis(), mAllowTheaterModeWakeFromLidSwitch,
                    "android.policy:LID");
            }
            //+++add for private user account by jeson_li
        } else if(android.os.Build.FEATURES.HAS_TRANSCOVER&&mSystemBooted&&((mHasTranscoverInfoFeature&&!mIsTranscoverEnabled&&mTranscoverAutomaticUnlock)||isPrivateUser)){
            //always suspend screen for snapview user when closing cover, and sleep for cover with hole that "enable cover" was disabled and automatic unlock was enabled
            setFlipCoverSysPropety();
            Log.d(TAG, "sleep for private user or cover with hole that \"enable cover\" was disabled and \"automatic unlock\" was enabled");
            mPowerManager.goToSleep(SystemClock.uptimeMillis(),
                            PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH,
                            PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
            return;
            //--- jeson_li
        } else if (!mLidControlsSleep) {
            mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
        }
        
        // +++ jeson_li: ViewFlipCover
        if(android.os.Build.FEATURES.HAS_TRANSCOVER){
            validateFlipCoverForLidState(true,isPrivateUser);
        }
        // --- jeson_li: ViewFlipCover
    }
    
    //+++ jeson_li: ViewFlipCover
    void validateFlipCoverForLidState(boolean forNotifyLidSwitchChanged,boolean isPrivateUser){
        if(mSystemBooted){
            setFlipCoverSysPropety();
            if(mIsTranscoverEnabled&&mFlipCover2ServiceDelegate!=null){
                if(mFlipCover2ServiceDelegate.isCoverServiceConnected()){
                    if(mIsTranscoverEnabled&&mHasTranscoverFeature&&mHasTranscoverInfoFeature&&mLidState == LID_CLOSED&&!isPrivateUser){
                        mFlipCover2ServiceDelegate.showFlipCover(forNotifyLidSwitchChanged);
                        if(mPowerManager!=null){
                            mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
                        }
                        return;
                    }else if(mHasTranscoverInfoFeature){
                        Log.d(TAG, "validateFlipCoverForLidState, mAwake:"+mAwake+", mScreenOnEarly:"+mScreenOnEarly+", mScreenOnFully:"+mScreenOnFully+", forNotifyLidSwitchChanged:"+forNotifyLidSwitchChanged);
                        mFlipCover2ServiceDelegate.hideFlipCover(forNotifyLidSwitchChanged);
                    }
                }else if(forNotifyLidSwitchChanged&&mHasTranscoverInfoFeature&&mLidState == LID_CLOSED&&!isPrivateUser){
                    mFlipCover2ServiceDelegate.sendCoverServiceRebindMsg();
                }
            }
        }
    }
    //--- jeson_li: ViewFlipCover

    @Override
    public void notifyCameraLensCoverSwitchChanged(long whenNanos, boolean lensCovered) {
        int lensCoverState = lensCovered ? CAMERA_LENS_COVERED : CAMERA_LENS_UNCOVERED;
        if (mCameraLensCoverState == lensCoverState) {
            return;
        }
        if (mCameraLensCoverState == CAMERA_LENS_COVERED &&
                lensCoverState == CAMERA_LENS_UNCOVERED) {
            Intent intent;
            final boolean keyguardActive = mKeyguardDelegate == null ? false :
                    mKeyguardDelegate.isShowing();
            if (keyguardActive) {
                intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
            } else {
                intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            }
            wakeUp(whenNanos / 1000000, mAllowTheaterModeWakeFromCameraLens,
                    "android.policy:CAMERA_COVER");
            startActivityAsUser(intent, UserHandle.CURRENT_OR_SELF);
        }
        mCameraLensCoverState = lensCoverState;
    }

    void setHdmiPlugged(boolean plugged) {
        if (mHdmiPlugged != plugged) {
            mHdmiPlugged = plugged;
            updateRotation(true, true);
            Intent intent = new Intent(ACTION_HDMI_PLUGGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(EXTRA_HDMI_PLUGGED_STATE, plugged);
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    void initializeHdmiState() {
        boolean plugged = false;
        // watch for HDMI plug messages if the hdmi switch exists
        if (new File("/sys/devices/virtual/switch/hdmi/state").exists()) {
            mHDMIObserver.startObserving("DEVPATH=/devices/virtual/switch/hdmi");

            final String filename = "/sys/class/switch/hdmi/state";
            FileReader reader = null;
            try {
                reader = new FileReader(filename);
                char[] buf = new char[15];
                int n = reader.read(buf);
                if (n > 1) {
                    plugged = 0 != Integer.parseInt(new String(buf, 0, n-1));
                }
            } catch (IOException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } catch (NumberFormatException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                    }
                }
            }
        }
        // This dance forces the code in setHdmiPlugged to run.
        // Always do this so the sticky intent is stuck (to false) if there is no hdmi.
        mHdmiPlugged = !plugged;
        setHdmiPlugged(!mHdmiPlugged);
    }

   //+++Mist Liao, read the dock state
   int readDockState() {
        final String filename = "/sys/class/switch/dock/state";
        FileReader reader = null;
        try {
            reader = new FileReader(filename);
            char[] buf = new char[15];
            int n = reader.read(buf);
            if (n > 1) {
                return Integer.parseInt(new String(buf, 0, n-1));
            } else {
                return 0;
            }
        } catch (IOException ex) {
            Slog.d(TAG, "couldn't read dock state from " + filename + ": " + ex);
            return 0;
        } catch (NumberFormatException ex) {
            Slog.d(TAG, "couldn't read dock state from " + filename + ": " + ex);
            return 0;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                }
            }
        }
    }
    //---

    final Object mScreenshotLock = new Object();
    ServiceConnection mScreenshotConnection = null;

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                    notifyScreenshotError();
                }
            }
        }
    };

    // Assume this is called from the Handler thread.
    private void takeScreenshot(final int screenshotType) {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            final ComponentName serviceComponent = new ComponentName(SYSUI_PACKAGE,
                    SYSUI_SCREENSHOT_SERVICE);
            final Intent serviceIntent = new Intent();
            serviceIntent.setComponent(serviceComponent);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, screenshotType);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        mHandler.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
                        if (mStatusBar != null && mStatusBar.isVisibleLw())
                            msg.arg1 = 1;
                        if (mNavigationBar != null && mNavigationBar.isVisibleLw())
                            msg.arg2 = 1;
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    notifyScreenshotError();
                }
            };
            if (mContext.bindServiceAsUser(serviceIntent, conn,
                    Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE_WHILE_AWAKE,
                    UserHandle.CURRENT)) {
                mScreenshotConnection = conn;
                mHandler.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }

    /**
     * Notifies the screenshot service to show an error.
     */
    private void notifyScreenshotError() {
        // If the service process is killed, then ask it to clean up after itself
        final ComponentName errorComponent = new ComponentName(SYSUI_PACKAGE,
                SYSUI_SCREENSHOT_ERROR_RECEIVER);
        Intent errorIntent = new Intent(Intent.ACTION_USER_PRESENT);
        errorIntent.setComponent(errorComponent);
        errorIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT |
                Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcastAsUser(errorIntent, UserHandle.CURRENT);
    }

    void handleFunctionKey(KeyEvent event) {
        mBroadcastWakeLock.acquire();
        mHandler.post(new PassFunctionKey(new KeyEvent(event)));
    }

    /** {@inheritDoc} */
    @Override
    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags) {
        if (!mSystemBooted) {
            // If we have not yet booted, don't let key events do anything.
            return 0;
        }

        /// M: If USP service freeze display, disable power key
        if (interceptKeyBeforeHandling(event)) {
            return 0;
        }

        /// M: power-off alarm, disable power_key @{
        if (KeyEvent.KEYCODE_POWER == event.getKeyCode() && mIsAlarmBoot) {
            return 0;
        }
        /// @}

        /// M: IPO migration @{
        synchronized (mKeyDispatchLock) {
            if (KEY_DISPATCH_MODE_ALL_DISABLE == mKeyDispatcMode) {
                return 0;
            }
        }

        //lishunbo@wind-mobi.com modify 20180109 for Chile SAE start
        if ("CL".equals(COUNTRY_CODE)) {
            boolean isWindSAEDisableKey = Settings.System.getInt(mContext.getContentResolver(), "WIND_SAE_DIALOG", 0) == 1;
            if (isWindSAEDisableKey) {
                return 0;
            }
        }
        //lishunbo@wind-mobi.com modify 20180109 for Chile SAE end

        /// @}
        final boolean interactive = (policyFlags & FLAG_INTERACTIVE) != 0;
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();
        final int keyCode = event.getKeyCode();

        final boolean isInjected = (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0;

        // If screen is off then we treat the case where the keyguard is open but hidden
        // the same as if it were open and in front.
        // This will prevent any keys other than the power button from waking the screen
        // when the keyguard is hidden by another activity.
        boolean keyguardActive = (mKeyguardDelegate == null ? false :
                                            (interactive ?
                                                isKeyguardShowingAndNotOccluded() :
                                                mKeyguardDelegate.isShowing()));
        // +++
        final boolean keyguardWasActive = keyguardActive;
        final boolean isLidCloseOnDock = isLidClosedOnDock();
        // ---

        if (DEBUG_INPUT) {
            Log.d(TAG, "interceptKeyTq keycode=" + keyCode
                    + " interactive=" + interactive + " keyguardActive=" + keyguardActive
                    + " policyFlags=" + Integer.toHexString(policyFlags));
        }

        if (android.os.Build.IS_DEBUGGABLE && (keyCode == KeyEvent.KEYCODE_POWER ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            Log.d(TAG, "interceptKeyTq keyCode=" + keyCode + " down=" + down  + " canceled=" + canceled);
        }

        // Basic policy based on interactive state.
        int result;
        boolean isWakeKey = (policyFlags & WindowManagerPolicy.FLAG_WAKE) != 0
                || event.isWakeKey();
        if (interactive || (isInjected && !isWakeKey)) {
            // When the device is interactive or the key is injected pass the
            // key to the application.
            result = ACTION_PASS_TO_USER;
            isWakeKey = false;
        } else if (!interactive && shouldDispatchInputWhenNonInteractive()) {
            // If we're currently dozing with the screen on and the keyguard showing, pass the key
            // to the application but preserve its wake key status to make sure we still move
            // from dozing to fully interactive if we would normally go from off to fully
            // interactive.
            result = ACTION_PASS_TO_USER;
        } else {
            // When the screen is off and the key is not injected, determine whether
            // to wake the device but don't pass the key to the application.
            result = 0;
            if (isWakeKey && (!down || !isWakeKeyWhenScreenOff(keyCode))) {
                isWakeKey = false;
            }
            if (isWakeKey && isLidCloseOnDock) { //+++ isLidCloseOnDock
                isWakeKey = false;
            }
        }

        // If the key would be handled globally, just return the result, don't worry about special
        // key processing.
        if (isValidGlobalKey(keyCode)
                && mGlobalKeyManager.shouldHandleGlobalKey(keyCode, event)) {
            if (isWakeKey) {
                wakeUp(event.getEventTime(), mAllowTheaterModeWakeFromKey, "android.policy:KEY");
            }
            return result;
        }

        boolean useHapticFeedback=false;
        if (down && (policyFlags & WindowManagerPolicy.FLAG_VIRTUAL) != 0
            && event.getRepeatCount() == 0) {
            // +++ Willie_Huang, disable virtual key if cover is closed.
            if (!mHasTranscoverFeature || mLidState != LID_CLOSED) {
                useHapticFeedback = true;
            }
            // ---
        }

        /// M: Add more log at WMS
        //modify mohongwu@wind-mobi.com 2016/12/14 start
        if (true == IS_USER_BUILD) {
        //modify mohongwu@wind-mobi.com 2016/12/14 end
            Log.d(TAG, "interceptKeyTq keycode=" + keyCode
                + " interactive=" + interactive + " keyguardActive=" + keyguardActive
                + " policyFlags=" + Integer.toHexString(policyFlags)
                + " down =" + down + " canceled = " + canceled
                + " isWakeKey=" + isWakeKey
                + " mVolumeDownKeyTriggered =" + mScreenshotChordVolumeDownKeyTriggered
                + " mVolumeUpKeyTriggered =" + mScreenshotChordVolumeUpKeyTriggered
                + " result = " + result
                + " useHapticFeedback = " + useHapticFeedback
                + " isInjected = " + isInjected);
        }

        //+++ Mist Liao, handle the hardware keyboard keys
        if ((policyFlags & WindowManagerPolicy.FLAG_UNLOCK) != 0) {
            // Skip all hardware keyboard keys when close the lid
            if (isLidCloseOnDock) {
                Log.d(TAG, "Lid is not open, skip the key:" + keyCode);
                return 0;
            }

            // Handle the keys when keyguard active
            if (keyguardActive) {
                Log.d(TAG, "Keyguard active key:" + keyCode);

                if (!isKeyguardSecure(mCurrentUserId)) {
                    // Consume the key, so don't need pass to user
                    result &= ~ACTION_PASS_TO_USER;

                    if (down) {
                        Log.d(TAG, "Unlock keyguard by key:" + keyCode);
                        keyguardActive = !mKeyguardDelegate.doKeyguardBypass(mCurrentUserId);

                        // wake the device
                        isWakeKey = true;
                        //mPowerManager.wakeUp(SystemClock.uptimeMillis());
                        //result |= ACTION_WAKE_UP;
                    }
                }
            }
       }
        //---

        if ((mCombineKeyDetector.onKeyEvent(event,interactive) & CombineKeyDetector.NEED_RETURN_EVENT) != 0){
            Log.i(TAG, "CombineKeyDetector return keycode = "+event.getKeyCode()+", down = "+down);
            mHandler.removeCallbacks(mRecentLongPressRunnable);
            return 0;
        }

        if (mSystemMonitorInternal == null) {
            Log.i(TAG,"init SystemMonitorInternal service");
            mSystemMonitorInternal = LocalServices
                    .getService(SystemMonitorInternal.class);
        }

        String openStatus  = SystemProperties.get("persist.asus.camera.open");
        boolean isAllowUseCamera = ("".equals(openStatus) || "1".equals(openStatus) || openStatus == null);

        // Handle special keys.
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                //+++Chilin_Wang@asus.com, Porting of lock physical key when the screen pinning request is show.
                synchronized (mLockPhysicalKey) {
                    if (mIsLockPhysicalKey) {
                        Log.i(TAG,"Lock physical key = "+keyCode+", down = "+down);
                        //Begin:HJ@asus.com
                        if(Build.FEATURES.ENABLE_INADVERTENTTOUCH && down) {
                            notifyHardwareKeyPressed();
                        }
                        //End:HJ@asus.com
                        return 0;
                    }
                }
                Log.i(TAG,"beforeQueueing keycode is KEYCODE_BACK");
                //---Chilin_Wang@asus.com
                if (down) {
                    mBackKeyHandled = false;
                    if (hasLongPressOnBackBehavior()) {
                        Message msg = mHandler.obtainMessage(MSG_BACK_LONG_PRESS);
                        msg.setAsynchronous(true);
                        mHandler.sendMessageDelayed(msg,
                                ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
                    }
                } else {
                    boolean handled = mBackKeyHandled;

                    // Reset back key state
                    cancelPendingBackKeyAction();

                    // Don't pass back press to app if we've already handled it
                    if (handled) {
                        result &= ~ACTION_PASS_TO_USER;
                    }
                }
                break;
            }
            //+++Chilin_Wang@asus.com, Porting of lock physical key when the screen pinning request is show.
            case KeyEvent.KEYCODE_APP_SWITCH: {
                synchronized (mLockPhysicalKey) {
                    if (mIsLockPhysicalKey) {
                        Log.i(TAG,"Lock physical key = "+keyCode+", down = "+down);
                        //Begin:HJ@asus.com
                        if(Build.FEATURES.ENABLE_INADVERTENTTOUCH && down) {
                            notifyHardwareKeyPressed();
                        }
                        //End:HJ@asus.com
                        return 0;
                    }
                }
                break;
            }
            case KeyEvent.KEYCODE_HOME: {
                synchronized (mLockPhysicalKey) {
                    if (mIsLockPhysicalKey) {
                        Log.i(TAG, "Lock physical key = " + keyCode + ", down = " + down);
                        //Begin:HJ@asus.com
                        if(Build.FEATURES.ENABLE_INADVERTENTTOUCH && down) {
                            notifyHardwareKeyPressed();
                        }
                        //End:HJ@asus.com
                        return 0;
                    }
                }
				 // Add by gaohui@wind-mobi.com 20161107 start to disable home key when enrolling
                if (down)
                    mFingerprintOn = fingerprintOn();
                // Add by gaohui@wind-mobi.com 20161107 end to disable home key when enrolling
                break;
            }
            case KeyEvent.KEYCODE_CAMERA_RECORD:
            case KeyEvent.KEYCODE_CAMERA: {
                if ((mIsInstantCameraEnabled && !interactive && mLidState != LID_CLOSED)
                        || (mIsLongPressInstantCameraEnabled && interactive && isAllowLaunchCamera() && isAllowUseCamera)) {
                    if (down) {
                        final int repeatCount = event.getRepeatCount();
                        //Long press to launch camera
                        if (repeatCount == 0) {
                            if (keyCode == KeyEvent.KEYCODE_CAMERA_RECORD) {
                                handleCameraMessage("hardware:camerarecordkey", MSG_LONG_PRESS_EVENT, interactive, KeyEvent.KEYCODE_CAMERA_RECORD);
                            }
                            if (keyCode == KeyEvent.KEYCODE_CAMERA) {
                                handleCameraMessage("hardware:camerakey", MSG_LONG_PRESS_EVENT, interactive, KeyEvent.KEYCODE_CAMERA);
                            }
                        }
                    } else {
                        if (keyCode == KeyEvent.KEYCODE_CAMERA_RECORD) {
                            handleCameraMessage("hardware:camerarecordkey_up", MSG_KEY_UP_EVENT, interactive, KeyEvent.KEYCODE_CAMERA_RECORD);
                        }
                        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
                            handleCameraMessage("hardware:camerakey_up", MSG_KEY_UP_EVENT, interactive, KeyEvent.KEYCODE_CAMERA);
                        }
                    }
                }
                break;
            }
            //---Chilin_Wang@asus.com

            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE: {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (down) {
                        if (interactive && !mScreenshotChordVolumeDownKeyTriggered
                                && (event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
                            mScreenshotChordVolumeDownKeyTriggered = true;
                            mScreenshotChordVolumeDownKeyTime = event.getDownTime();
                            mScreenshotChordVolumeDownKeyConsumed = false;
                            cancelPendingPowerKeyAction();
                            interceptScreenshotChord();
                        }
                    } else {
                        mScreenshotChordVolumeDownKeyTriggered = false;
                        cancelPendingScreenshotChordAction();
                    }
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    /// M: Key remapping
                    if ((false == IS_USER_BUILD)
                                && SystemProperties.get("persist.sys.anr_sys_key").equals("1")) {
                        mHandler.postDelayed(mKeyRemappingVolumeDownLongPress_Test, 0);
                    }
                    if (down) {
                        if (interactive && !mScreenshotChordVolumeUpKeyTriggered
                                && (event.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) {
                            mScreenshotChordVolumeUpKeyTriggered = true;
                            cancelPendingPowerKeyAction();
                            cancelPendingScreenshotChordAction();
                        }
                    } else {
                        mScreenshotChordVolumeUpKeyTriggered = false;
                        cancelPendingScreenshotChordAction();
                    }
                }
                if (down) {
                    TelecomManager telecomManager = getTelecommService();
                    if (telecomManager != null) {
                        if (telecomManager.isRinging()) {
                            // If an incoming call is ringing, either VOLUME key means
                            // "silence ringer".  We handle these keys here, rather than
                            // in the InCallScreen, to make sure we'll respond to them
                            // even if the InCallScreen hasn't come to the foreground yet.
                            // Look for the DOWN event here, to agree with the "fallback"
                            // behavior in the InCallScreen.
                            Log.i(TAG, "interceptKeyBeforeQueueing:"
                                  + " VOLUME key-down while ringing: Silence ringer!");

                            // Silence the ringer.  (It's safe to call this
                            // even if the ringer has already been silenced.)
                            //+++ cenxingcan@wind-mobi.com [ALPS03101432] add BUG#152739 end 2017/02/12 +++
                            // telecomManager.silenceRinger();  //origin code
                            if (WIND_DEF_MUTERINGER_FEATURE) {
                                telecomManager.muteRinger();
                            } else {
                                telecomManager.silenceRinger();  //origin code
                            }
                            //+++ cenxingcan@wind-mobi.com [ALPS03101432] add BUG#152739 end 22017/02/12 +++

                            // And *don't* pass this key thru to the current activity
                            // (which is probably the InCallScreen.)
                            result &= ~ACTION_PASS_TO_USER;
                            //Begin:HJ@asus.com
                            if(Build.FEATURES.ENABLE_INADVERTENTTOUCH) {
                                synchronized (mLockPhysicalKey) {
                                    if (mIsLockPhysicalKey) {
                                        Slog.d(TAG,"interceptKeyBeforeQueueing: pass volume key to " +
                                        "inadvertenttouch window");
                                        result |= ACTION_PASS_TO_USER;
                                    }
                                }
                            }
                            //End:HJ@asus.com
                            break;
                        }
                        if (telecomManager.isInCall()
                                && (result & ACTION_PASS_TO_USER) == 0) {
                            // If we are in call but we decided not to pass the key to
                            // the application, just pass it to the session service.

                            MediaSessionLegacyHelper.getHelper(mContext)
                                    .sendVolumeKeyEvent(event, false);
                            break;
                        }
                    }
                }
                if (mUseTvRouting) {
                    // On TVs, defer special key handlings to
                    // {@link interceptKeyBeforeDispatching()}.
                    result |= ACTION_PASS_TO_USER;
                } else if ((result & ACTION_PASS_TO_USER) == 0) {
                    // If we aren't passing to the user and no one else
                    // handled it send it to the session manager to
                    // figure out.
                    MediaSessionLegacyHelper.getHelper(mContext)
                            .sendVolumeKeyEvent(event, true);
                }

                //+++Chilin_Wang@asus.com, Instant camera porting
                if (mIsInstantCameraEnabled && !interactive && mLidState != LID_CLOSED && !isMusicActive() && !mSupportShutterOrRecordKeyDevice) {
                    if (keyCode != KeyEvent.KEYCODE_VOLUME_MUTE) {
                        if (!down) {
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                                handleCameraMessage("hardware:volumedownkey_up",MSG_KEY_UP_EVENT,interactive,KeyEvent.KEYCODE_VOLUME_DOWN);
                                break;
                            }
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                handleCameraMessage("hardware:volumeupkey_up",MSG_KEY_UP_EVENT,interactive,KeyEvent.KEYCODE_VOLUME_UP);
                                break;
                            }
                            break;
                        } else {
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                                handleCameraMessage("hardware:volumekey_down",MSG_DOUBLE_CLICK_EVENT,interactive,KeyEvent.KEYCODE_VOLUME_DOWN);
                                break;
                            }

                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                handleCameraMessage("hardware:volumekey_up",MSG_DOUBLE_CLICK_EVENT,interactive,KeyEvent.KEYCODE_VOLUME_UP);
                                break;
                            }
                        }
                    }
                }
                //---Instant camera porting
                break;
            }

            case KeyEvent.KEYCODE_ENDCALL: {
                result &= ~ACTION_PASS_TO_USER;
                if (down) {
                    TelecomManager telecomManager = getTelecommService();
                    boolean hungUp = false;
                    if (telecomManager != null) {
                        hungUp = telecomManager.endCall();
                    }
                    if (interactive && !hungUp) {
                        mEndCallKeyHandled = false;
                        mHandler.postDelayed(mEndCallLongPress,
                                ViewConfiguration.get(mContext).getDeviceGlobalActionKeyTimeout());
                    } else {
                        mEndCallKeyHandled = true;
                    }
                } else {
                    if (!mEndCallKeyHandled) {
                        mHandler.removeCallbacks(mEndCallLongPress);
                        if (!canceled) {
                            if ((mEndcallBehavior
                                    & Settings.System.END_BUTTON_BEHAVIOR_HOME) != 0) {
                                if (goHome()) {
                                    break;
                                }
                            }
                            if ((mEndcallBehavior
                                    & Settings.System.END_BUTTON_BEHAVIOR_SLEEP) != 0) {
                                mPowerManager.goToSleep(event.getEventTime(),
                                        PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
                                isWakeKey = false;
                            }
                        }
                    }
                }
                break;
            }

            case KeyEvent.KEYCODE_POWER: {
                if (mHasFeatureVR) {
                    synchronized (mLockPhysicalKey) {
                    IVrManager vrManager = IVrManager.Stub.asInterface(
                    ServiceManager.getService(VrManagerService.VR_MANAGER_BINDER_SERVICE));
                    if (vrManager != null) {
                      try {
                          //vrManager.registerListener(mVrStateCallbacks);
                          mVrModeEnabled = vrManager.getVrModeState();
                          Slog.d(TAG, "mVrModeEnabled = " + mVrModeEnabled);
                      } catch (RemoteException e) {
                      Slog.e(TAG, "Failed to get VR mode state: " + e);
                      }
                    }
                     if (mVrModeEnabled) {
                          Log.i(TAG,"Disable power button in VR mode ,  keyCode = "+keyCode);
                          return 0;
                      }
                    }
                }
                result &= ~ACTION_PASS_TO_USER;
                isWakeKey = false; // wake-up will be handled separately
                //modified by lizusheng@wind-mobi.com 20161108 start
               /* if (down) {
                    interceptPowerKeyDown(event, interactive);
                } else {
                    interceptPowerKeyUp(event, interactive, canceled);
                }*/

                String mCurApClassNamae = "";
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningTaskInfo> runningTaskInfos = am.getRunningTasks(1);
                    if (runningTaskInfos != null && runningTaskInfos.size() > 0) {
                        mCurApClassNamae = runningTaskInfos.get(0).topActivity.getClassName();
                    }
                }

                if (mCurApClassNamae.equals("com.wind.emode.testcase.KeyTest") && isScreenOn()) {
                    Log.d(TAG, "Disable PowerKey for KeyTest in Emode ");
                    result |= ACTION_PASS_TO_USER;
                } else {
                    if (down) {
                        //lifeifei@wind-mobi.com add 2018/01/09 begin
                        if(mPerfService == null){
                            mPerfService = new PerfServiceWrapper(null);
                        }
                        if(mPerfService != null && !isScreenOn()){
                            Log.d(TAG,"full CPU 1000ms begin");
                            mPerfServiceHandle_base_cpu = mPerfService.userReg(8,0);
                            mPerfService.userEnableTimeout(mPerfServiceHandle_base_cpu,1000);
                        }
                        //lifeifei@wind-mobi.com add 2018/01/09 end
                        interceptPowerKeyDown(event, interactive);
                    } else {
                        interceptPowerKeyUp(event, interactive, canceled);
                    }
                    injectSpecialPowerKey(down);
                }
                //modified by lizusheng@wind-mobi.com 20161108 end
                break;
            }

            /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 begin
            case KeyEvent.KEYCODE_F11: {
                if (WIND_DEF_OTG_REVERSE) {
                     result &= ~ACTION_PASS_TO_USER;
                     if (down && (!getPopOtgDialogEnabled())) {
                         otgState = true;  //M: add by cenxingcan@wind-mobi.com 2017/01/12
                         Message plugInOtgMsg = Message.obtain();
                         plugInOtgMsg.what = MSG_OTG_PLUG_IN_OPERATION;
                         mHandler.sendMessage(plugInOtgMsg);
                         setPopOtgDialogValue(true);
                     }
                 }
            }
            break;
            case KeyEvent.KEYCODE_F12: {
                if (WIND_DEF_OTG_REVERSE) {
                    result &= ~ACTION_PASS_TO_USER;
                    if (down && getPopOtgDialogEnabled()) {
                        otgState = false;  //M: add by cenxingcan@wind-mobi.com 2017/01/12
                        Message plugOutOtgMsg = Message.obtain();
                        plugOutOtgMsg.what = MSG_OTG_PLUG_OUT_OPERATION;
                        mHandler.sendMessage(plugOutOtgMsg);
                    }
                }
            }
            break;
            /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 end

            case KeyEvent.KEYCODE_SLEEP: {
                result &= ~ACTION_PASS_TO_USER;
                isWakeKey = false;
                if (!mPowerManager.isInteractive()) {
                    useHapticFeedback = false; // suppress feedback if already non-interactive
                }
                if (down) {
                    sleepPress(event.getEventTime());
                } else {
                    sleepRelease(event.getEventTime());
                }
                break;
            }

            // +++
//            case KeyEvent.KEYCODE_SLEEP: {//+++Mist Liao
                // We would let device go to sleep when no keyguard or
                // has keyguard but is secure (eg. Pattern Lock)
//                if (isScreenOn && down && (!keyguardWasActive || isKeyguardSecure())) {
//                    result &= ~ACTION_WAKE_UP;
//                    result |= ACTION_GO_TO_SLEEP;
//                }

//                result &= ~ACTION_PASS_TO_USER;
//                break;
//            }
            // ---
            case KeyEvent.KEYCODE_SOFT_SLEEP: {
                result &= ~ACTION_PASS_TO_USER;
                isWakeKey = false;
                if (!down) {
                    mPowerManagerInternal.setUserInactiveOverrideFromWindowManager();
                }
                break;
            }

            case KeyEvent.KEYCODE_WAKEUP: {
                result &= ~ACTION_PASS_TO_USER;
                isWakeKey = true;
                break;
            }

            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_RECORD:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK: {
                // Disable function key of media in SetupWizard.
                if (!isDeviceProvisioned()) {
                    result &= ~ACTION_PASS_TO_USER;
                    break;
                }

                if (MediaSessionLegacyHelper.getHelper(mContext).isGlobalPriorityActive()) {
                    // If the global session is active pass all media keys to it
                    // instead of the active window.
                    result &= ~ACTION_PASS_TO_USER;
                }
                if ((result & ACTION_PASS_TO_USER) == 0) {
                    // Only do this if we would otherwise not pass it to the user. In that
                    // case, the PhoneWindow class will do the same thing, except it will
                    // only do it if the showing app doesn't process the key on its own.
                    // Note that we need to make a copy of the key event here because the
                    // original key event will be recycled when we return.
                    mBroadcastWakeLock.acquire();
                    Message msg = mHandler.obtainMessage(MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK,
                            new KeyEvent(event));
                    msg.setAsynchronous(true);
                    msg.sendToTarget();
                }
                break;
            }

            case KeyEvent.KEYCODE_CALL: {
                if (down) {
                    TelecomManager telecomManager = getTelecommService();
                    if (telecomManager != null) {
                        if (telecomManager.isRinging()) {
                            Log.i(TAG, "interceptKeyBeforeQueueing:"
                                  + " CALL key-down while ringing: Answer the call!");
                            telecomManager.acceptRingingCall();

                            // And *don't* pass this key thru to the current activity
                            // (which is presumably the InCallScreen.)
                            result &= ~ACTION_PASS_TO_USER;
                        }
                    }
                }
                break;
            }
            case KeyEvent.KEYCODE_VOICE_ASSIST: {
                // Only do this if we would otherwise not pass it to the user. In that case,
                // interceptKeyBeforeDispatching would apply a similar but different policy in
                // order to invoke voice assist actions. Note that we need to make a copy of the
                // key event here because the original key event will be recycled when we return.
                if ((result & ACTION_PASS_TO_USER) == 0 && !down) {
                    mBroadcastWakeLock.acquire();
                    Message msg = mHandler.obtainMessage(MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK,
                            keyguardActive ? 1 : 0, 0);
                    msg.setAsynchronous(true);
                    msg.sendToTarget();
                }
                break;
            }
            case KeyEvent.KEYCODE_WINDOW: {
                if (mShortPressWindowBehavior == SHORT_PRESS_WINDOW_PICTURE_IN_PICTURE) {
                    if (mTvPictureInPictureVisible) {
                        // Consumes the key only if picture-in-picture is visible
                        // to show picture-in-picture control menu.
                        // This gives a chance to the foreground activity
                        // to customize PIP key behavior.
                        if (!down) {
                            showTvPictureInPictureMenu(event);
                        }
                        result &= ~ACTION_PASS_TO_USER;
                    }
                }
                break;
            }
            case KeyEvent.KEYCODE_CAPS_LOCK:
            case KeyEvent.KEYCODE_EISU: {
                if (down && !keyguardActive) {
                    handleFunctionKey(event);
                }
                break;
            }
            case KeyEvent.KEYCODE_WIRELESS:
            case KeyEvent.KEYCODE_BLUETOOTH:
            case KeyEvent.KEYCODE_TOUCHPAD:
            case KeyEvent.KEYCODE_EXPLORER:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_SPLENDID:
            case KeyEvent.KEYCODE_LAUNCH_CAMERA:
            case KeyEvent.KEYCODE_FLIGHT_MODE_SWITCH:
            case KeyEvent.KEYCODE_READMODE:
            case KeyEvent.KEYCODE_MODE_SWITCH:
                // Disable function key of Wifi, BT, touchpad, browser, and settings in SetupWizard.
                if (!isDeviceProvisioned()) {
                    result &= ~ACTION_PASS_TO_USER;
                    break;
                }
            case KeyEvent.KEYCODE_BRIGHTNESS_AUTO:
            case KeyEvent.KEYCODE_NUM_LOCK: {
                if (down && !keyguardActive) {
                    handleFunctionKey(event);
                    result &= ~ACTION_PASS_TO_USER;
                }
                break;
            }

            case KeyEvent.KEYCODE_CAPTURE: {
                if (down) {
                    mHandler.post(mScreenshotRunnable);
                    result &= ~ACTION_PASS_TO_USER;
                }
                break;
            }

	        case KeyEvent.KEYCODE_GESTURE_WAKE:
            case KeyEvent.KEYCODE_GESTURE_DOUBLE_CLICK: {
                if (!isDeviceProvisioned()) {
                    result &= ~ACTION_PASS_TO_USER;
                    break;
                }
                if (down) {
                    int mFlipTap = Settings.System.getInt(
                            mContext.getContentResolver(),
                            "asus_cover_taptap_for_call", 0);
                    readLidState();
                    Log.i(TAG, "DOUBLE CLICK mLidState:" + mLidState + " mFlipTap:"
                            + mFlipTap);
                    if (mFlipTap == 0 && mLidState == LID_CLOSED) {
                        isWakeKey = false;
                        Log.d(TAG, "Cover mode: Double Tap disable " + keyCode);
                    } else {
                        TelephonyManager tm = (TelephonyManager) mContext
                                .getSystemService(Context.TELEPHONY_SERVICE);
                        AudioManager audioManager = (AudioManager) mContext
                                .getSystemService(Context.AUDIO_SERVICE);
                        // int mFlipTap =
                        // Settings.System.getInt(mContext.getContentResolver(),
                        // "asus_cover_taptap_for_call", 0);
                        // readLidState();
                        Log.i(TAG, "DOUBLE CLICK mLidState:" + mLidState
                                + " mFlipTap:" + mFlipTap);
                        if (TelephonyManager.CALL_STATE_IDLE == tm.getCallState()) {
                            isWakeKey = true;
                            Log.d(TAG, "CALL STATE_IDLE " + keyCode);
                        } else if (TelephonyManager.CALL_STATE_RINGING == tm
                                .getCallState()) {
                            isWakeKey = true;
                            Log.d(TAG, "CALL STATE RINGING " + keyCode);
                        } else if (TelephonyManager.CALL_STATE_OFFHOOK == tm
                                .getCallState()) {
                            if ((mFlipTap == 1 && mLidState == LID_CLOSED)
                                    || audioManager.isSpeakerphoneOn() == true
                                    || audioManager.isBluetoothScoOn() == true
                                    || audioManager.isWiredHeadsetOn() == true
                                    || audioManager.isBluetoothA2dpOn() == true) {
                                isWakeKey = true;
                            } else {
                                isWakeKey = false;
                                useHapticFeedback = false;
                            }
                            Log.d(TAG, "CALL STATE OFFHOOK " + keyCode);
                            Log.d(TAG,
                                    "isSpeakerphoneOn: "
                                            + audioManager.isSpeakerphoneOn());
                            Log.d(TAG,
                                    "isBluetoothScoOn: "
                                            + audioManager.isBluetoothScoOn());
                            Log.d(TAG,
                                    "isWiredHeadsetOn: "
                                            + audioManager.isWiredHeadsetOn());
                            Log.d(TAG,
                                    "isBluetoothA2dpOn: "
                                            + audioManager.isBluetoothA2dpOn());
                        }
                    }
                    // dongjiangpeng@wind-mobi.com add 2016/12/19 start
                    if (isWakeKey) {
                        // mVibrator.vibrate(50);
                        mSensorManager.registerListener(mDistanceSensorListener,
                                mProximityMotion,
                                SensorManager.SENSOR_DELAY_FASTEST);
                        mHandler.removeMessages(MSG_GESTURE_LISTEN);
                        Message msg = mHandler.obtainMessage(MSG_GESTURE_LISTEN,
                                keyCode, (int) 0,
                                Long.valueOf(event.getEventTime()));
                        msg.setAsynchronous(true);
                        mHandler.sendMessageDelayed(msg, 100);
                        isWakeKey = false;
                    }
                    // dongjiangpeng@wind-mobi.com add 2016/12/19 end
                }
                result &= ~ACTION_PASS_TO_USER;
                break;
            }
            case KeyEvent.KEYCODE_GESTURE_C:
            case KeyEvent.KEYCODE_GESTURE_E:
            case KeyEvent.KEYCODE_GESTURE_S:
            case KeyEvent.KEYCODE_GESTURE_V:
            case KeyEvent.KEYCODE_GESTURE_W:
            case KeyEvent.KEYCODE_GESTURE_Z: {
                if (!isDeviceProvisioned()) {
                    result &= ~ACTION_PASS_TO_USER;
                    break;
                }
                if (down) {
                    String componentName = "";
                    String packageName = "";
                    boolean isOpenCamera = false;
                    if (keyCode == KeyEvent.KEYCODE_GESTURE_W) {
                        componentName = Settings.System.getString(mContext.getContentResolver(), GESTURE_TYPE1_APP);
                    } else if (keyCode == KeyEvent.KEYCODE_GESTURE_S) {
                        componentName = Settings.System.getString(mContext.getContentResolver(), GESTURE_TYPE2_APP);
                    } else if (keyCode == KeyEvent.KEYCODE_GESTURE_E) {
                        componentName = Settings.System.getString(mContext.getContentResolver(), GESTURE_TYPE3_APP);
                    } else if (keyCode == KeyEvent.KEYCODE_GESTURE_C) {
                        componentName = Settings.System.getString(mContext.getContentResolver(), GESTURE_TYPE4_APP);
                    } else if (keyCode == KeyEvent.KEYCODE_GESTURE_Z) {
                        componentName = Settings.System.getString(mContext.getContentResolver(), GESTURE_TYPE5_APP);
                    } else if (keyCode == KeyEvent.KEYCODE_GESTURE_V) {
                        componentName = Settings.System.getString(mContext.getContentResolver(), GESTURE_TYPE6_APP);
                    }
                    packageName = componentName.split("/")[0];
                    Log.d(TAG,"componentName:"+componentName+" packageName:"+packageName);
                    if ((!packageName.equals("com.asus.camera")) && (!packageName.equals("frontCamera"))) {
                        mGestureKeyWakeTime = event.getEventTime();
                        Log.d(TAG,"send MSG_TOUCHGESTURE_DELAY_WAKEUP_SCREEN,200ms");
                        mHandler.sendEmptyMessageDelayed(MSG_TOUCHGESTURE_DELAY_WAKEUP_SCREEN, 200L);
                        isOpenCamera = true;
                    }
                    mSensorManager.registerListener(mDistanceSensorListener,
                            mProximityMotion, SensorManager.SENSOR_DELAY_FASTEST);
                    mHandler.removeMessages(MSG_GESTURE_LISTEN);
                    Message msg = mHandler.obtainMessage(MSG_GESTURE_LISTEN,
                            keyCode, (int) (isOpenCamera ? 1 : 0),
                            Long.valueOf(event.getEventTime()));
                    msg.setAsynchronous(true);
                    mHandler.sendMessageDelayed(msg, 100);
                    isWakeKey = false;
                }
                result &= ~ACTION_PASS_TO_USER;
                break;
            }

            // BEGIN: Jeffrey_Chiang@asus.com
            case KeyEvent.KEYCODE_FINGERPRINT_SWIPE_UP: {
                Log.i(TAG, "KEYCODE_FINGERPRINT_SWIPE_UP");
            } break;
            case KeyEvent.KEYCODE_FINGERPRINT_SWIPE_DOWN: {
                Log.i(TAG, "KEYCODE_FINGERPRINT_SWIPE_DOWN");
            } break;
            case KeyEvent.KEYCODE_FINGERPRINT_SWIPE_LEFT: {
                Log.i(TAG, "KEYCODE_FINGERPRINT_SWIPE_LEFT");
            } break;
            case KeyEvent.KEYCODE_FINGERPRINT_SWIPE_RIGHT: {
                Log.i(TAG, "KEYCODE_FINGERPRINT_SWIPE_RIGHT");
            } break;
            case KeyEvent.KEYCODE_FINGERPRINT_TAP: {
                Log.i(TAG, "KEYCODE_FINGERPRINT_TAP");
                //add mohongwu@wind-mobi.com for TT1108979 2017/11/14 start
                isWakeKey = false;
                //add mohongwu@wind-mobi.com for TT1108979 2017/11/14 end
                
                // BEGIN : roy_huang@asus.com
                //modify mohongwu@wind-mobi.com for TT1006426 2017/5/18 start
                //remove mohongwu@wind-mobi.com for back fingerprint 2017/8/3 start
                /*if (mIsCNSku && mIsFpNavigationKeysEnabled && interactive && !down && isAllowedHandleKey(event)) {
                //modify mohongwu@wind-mobi.com for TT1006426 2017/5/18 end
                    if (mShortPressOnHome) {
                        mHandler.removeCallbacks(mShortPressHomeTimeoutRunnable);
                        mShortPressOnHome = false;
                    } else {
                        performHapticFeedbackLw(null, HapticFeedbackConstants.CONTEXT_CLICK, false);
                        sendKeyEvent(KeyEvent.KEYCODE_BACK);
                    }
                }
                //add mohongwu@wind-mobi.com for TT1035076 2017/6/20 start
                result &= ~ACTION_PASS_TO_USER;*/
                //remove mohongwu@wind-mobi.com for back fingerprint 2017/8/3 end
                //add mohongwu@wind-mobi.com for TT1035076 2017/6/20 end
                // END : roy_huang@asus.com
                // M: Remove comfilict fuction by cenxingcan@wind-mobi.com 2017/05/04 {@
                /*if (Build.FEATURES.ENABLE_FINGERPRINT_AS_HOME && interactive && event.getRepeatCount() == 0 && isAllowedHandleFingerprintKey(event)) {
                    if (down) {
                        sendKeyEvent(KeyEvent.KEYCODE_HOME);
                    }
                    result &= ~ACTION_PASS_TO_USER;
                }*/
                // @}
            } break;
            case KeyEvent.KEYCODE_FINGERPRINT_DTAP: {
                Log.i(TAG, "KEYCODE_FINGERPRINT_DTAP");
                Slog.d(TAG, "interactive=" + interactive + ". keyguardActive=" + keyguardActive + ". mIsLaunchCameraFromFpEnabled=" + mIsLaunchCameraFromFpEnabled);
                //add mohongwu@wind-mobi.com for TT1108979 2017/11/14 start
                isWakeKey = false;
                //add mohongwu@wind-mobi.com for TT1108979 2017/11/14 end
                //modify mohongwu@wind-mobi.com for back fingerprint 2017/8/3 start
                /*if (Build.FEATURES.ENABLE_FINGERPRINT_AS_HOME && interactive && !keyguardActive && isAllowedHandleKey(event)) {
                    if (down) {
                        TelecomManager tm = getTelecommService();
                        if (tm != null && !tm.isRinging()) {
                            handleDoubleTapOnHome();
                        }
                    }
                    result &= ~ACTION_PASS_TO_USER;
                    break;
                }*/

                if (interactive && !keyguardActive && mIsLaunchCameraFromFpEnabled) {
                    mHandler.removeMessages(MSG_FP_LAUNCH_CAMERA_EVENT);
                    Message msg = mHandler.obtainMessage(MSG_FP_LAUNCH_CAMERA_EVENT,
                            0, // non-suspend
                            KeyEvent.KEYCODE_FINGERPRINT_DTAP, // keycode
                            "hardware:fingerprint" // hardware source
                            );
                    mHandler.sendMessage(msg);
                    result &= ~ACTION_PASS_TO_USER;
                }
                //modify mohongwu@wind-mobi.com for back fingerprint 2017/8/3 end
            } break;
            case KeyEvent.KEYCODE_FINGERPRINT_LONGPRESS: {
                Log.i(TAG, "KEYCODE_FINGERPRINT_LONGPRESS");
                //add mohongwu@wind-mobi.com for TT1108979 2017/11/14 start
                isWakeKey = false;
                //add mohongwu@wind-mobi.com for TT1108979 2017/11/14 end
                // BEGIN : roy_huang@asus.com
                //modify mohongwu@wind-mobi.com for TT1006426 2017/5/18 start
                //if (mIsCNSku && mIsFpNavigationKeysEnabled && interactive && !keyguardActive && !down && isAllowTakeScreenshotFromFPLongPress()) {
                /*if (mIsCNSku && mIsFpNavigationKeysEnabled && interactive && !keyguardActive && !down && isAllowTakeScreenshotFromFPLongPress() && isAllowedHandleKey(event)) {
                //modify mohongwu@wind-mobi.com for TT1006426 2017/5/18 end
                    if (mLongPressOnHome) {
                        mHandler.removeCallbacks(mLongPressHomeTimeoutRunnable);
                        mLongPressOnHome = false;
                    } else if (mShortPressOnHome) {
                        mHandler.removeCallbacks(mShortPressHomeTimeoutRunnable);
                        mShortPressOnHome = false;
                    } else if (mDoublePressOnHome) {
                        mHandler.removeCallbacks(mDoublePressHomeTimeoutRunnable);
                        mDoublePressOnHome = false;
                    } else {
                        if (mVibrator != null && mVibrator.hasVibrator()) {
                            mVibrator.vibrate(mContextClickVibePattern, -1);
                        }
                        takeScreenshot(TAKE_SCREENSHOT_FULLSCREEN);
                    }
                }*/
                // END : roy_huang@asus.com
                // M: Remove comfilict fuction by cenxingcan@wind-mobi.com 2017/05/04 {@
                /*if (Build.FEATURES.ENABLE_FINGERPRINT_AS_HOME && interactive && !keyguardActive && event.getRepeatCount() == 0 && isAllowedHandleFingerprintKey(event)) {
                    if (down) {
                        TelecomManager tm = getTelecommService();
                        if (tm != null && !tm.isRinging()) {
                            if (mVirtualKeyBackLight == null) {
                                mVirtualKeyBackLight = LocalServices.getService(VirtualKeyBackLightService.class);
                            }
                            mVirtualKeyBackLight.flashVirtualKeybyKeyEvent();
                            handleLongPressOnHome(event.getDeviceId());
                            result &= ~ACTION_PASS_TO_USER;
                        }
                    } else {
                        if (mHomeConsumed) {
                            mHomeConsumed = false;
                            result &= ~ACTION_PASS_TO_USER;
                        }
                    }
                 }*/
                 // @}
                
            } break;
			case KeyEvent.KEYCODE_FINGERPRINT_POWERKEY: {
				Log.i(TAG, "KEYCODE_FINGERPRINT_POWERKEY");
			} break;
            // END: Jeffrey_Chiang@asus.com
            //BEGIN: Chilin_Wang@asus.com, For game genie lock mode ,blocking home/recent key
            case KeyEvent.KEYCODE_GAMEGENIE_LOCK: {
                mIsGameGenieLock = true;
                result &= ~ACTION_PASS_TO_USER;
            } break;
            case KeyEvent.KEYCODE_GAMEGENIE_UNLOCK: {
                mIsGameGenieLock = false;
                result &= ~ACTION_PASS_TO_USER;
            } break;
            //END: Chilin_Wang@asus.com
        }

        if (useHapticFeedback) {
            performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_KEY, false);
        }

        if (isWakeKey) {
            wakeUp(event.getEventTime(), mAllowTheaterModeWakeFromKey, "android.policy:KEY");
        }

        return result;
    }

    /**
     * Returns true if the key can have global actions attached to it.
     * We reserve all power management keys for the system since they require
     * very careful handling.
     */
    private static boolean isValidGlobalKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_SLEEP:
                return false;
            default:
                return true;
        }
    }

    /**
     * When the screen is off we ignore some keys that might otherwise typically
     * be considered wake keys.  We filter them out here.
     *
     * {@link KeyEvent#KEYCODE_POWER} is notably absent from this list because it
     * is always considered a wake key.
     */
    private boolean isWakeKeyWhenScreenOff(int keyCode) {
        switch (keyCode) {
            // ignore volume keys unless docked
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return mDockMode != Intent.EXTRA_DOCK_STATE_UNDOCKED;

            // ignore media and camera keys
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_RECORD:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
            case KeyEvent.KEYCODE_CAMERA:
                return false;
        }
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public int interceptMotionBeforeQueueingNonInteractive(long whenNanos, int policyFlags) {
        if ((policyFlags & FLAG_WAKE) != 0) {
	    if (!isLidClosedOnDock()) { //+++ !isLidClosedOnDock
             if (wakeUp(whenNanos / 1000000, mAllowTheaterModeWakeFromMotion,
                    "android.policy:MOTION")) {
                return 0;
             }
	    }
        }

        if (shouldDispatchInputWhenNonInteractive()) {
            return ACTION_PASS_TO_USER;
        }

        // If we have not passed the action up and we are in theater mode without dreaming,
        // there will be no dream to intercept the touch and wake into ambient.  The device should
        // wake up in this case.
        if (isTheaterModeEnabled() && (policyFlags & FLAG_WAKE) != 0) {
            wakeUp(whenNanos / 1000000, mAllowTheaterModeWakeFromMotionWhenNotDreaming,
                    "android.policy:MOTION");
        }

        return 0;
    }

    private boolean shouldDispatchInputWhenNonInteractive() {
        final boolean displayOff = (mDisplay == null || mDisplay.getState() == Display.STATE_OFF);

        if (displayOff && !mHasFeatureWatch) {
            return false;
        }

// BEGIN: oliver_hu@asus.com
        if (Build.FEATURES.ENABLE_ALWAYS_ON) {
            if (mDisplay.getState() == Display.STATE_DOZE_SUSPEND
                    || mDisplay.getState() == Display.STATE_DOZE) {
                return false;
            }
        }
// END: oliver_hu@asus.com

        // Send events to keyguard while the screen is on and it's showing.
        if (isKeyguardShowingAndNotOccluded() && !displayOff) {
            return true;
        }

        // Send events to a dozing dream even if the screen is off since the dream
        // is in control of the state of the screen.
        IDreamManager dreamManager = getDreamManager();

        try {
            if (dreamManager != null && dreamManager.isDreaming()) {
                return true;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "RemoteException when checking if dreaming", e);
        }

        // Otherwise, consume events since the user can't see what is being
        // interacted with.
        return false;
    }

    private void dispatchDirectAudioEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }
        int keyCode = event.getKeyCode();
        int flags = AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND
                | AudioManager.FLAG_FROM_KEY;
        String pkgName = mContext.getOpPackageName();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                try {
                    getAudioService().adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE,
                            AudioManager.USE_DEFAULT_STREAM_TYPE, flags, pkgName, TAG);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error dispatching volume up in dispatchTvAudioEvent.", e);
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                try {
                    getAudioService().adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER,
                            AudioManager.USE_DEFAULT_STREAM_TYPE, flags, pkgName, TAG);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error dispatching volume down in dispatchTvAudioEvent.", e);
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                try {
                    if (event.getRepeatCount() == 0) {
                        getAudioService().adjustSuggestedStreamVolume(
                                AudioManager.ADJUST_TOGGLE_MUTE,
                                AudioManager.USE_DEFAULT_STREAM_TYPE, flags, pkgName, TAG);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Error dispatching mute in dispatchTvAudioEvent.", e);
                }
                break;
        }
    }

    void dispatchMediaKeyWithWakeLock(KeyEvent event) {
        if (DEBUG_INPUT) {
            Slog.d(TAG, "dispatchMediaKeyWithWakeLock: " + event);
        }

        if (mHavePendingMediaKeyRepeatWithWakeLock) {
            if (DEBUG_INPUT) {
                Slog.d(TAG, "dispatchMediaKeyWithWakeLock: canceled repeat");
            }

            mHandler.removeMessages(MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK);
            mHavePendingMediaKeyRepeatWithWakeLock = false;
            mBroadcastWakeLock.release(); // pending repeat was holding onto the wake lock
        }

        dispatchMediaKeyWithWakeLockToAudioService(event);

        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            mHavePendingMediaKeyRepeatWithWakeLock = true;

            Message msg = mHandler.obtainMessage(
                    MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK, event);
            msg.setAsynchronous(true);
            mHandler.sendMessageDelayed(msg, ViewConfiguration.getKeyRepeatTimeout());
        } else {
            mBroadcastWakeLock.release();
        }
    }

    void dispatchMediaKeyRepeatWithWakeLock(KeyEvent event) {
        mHavePendingMediaKeyRepeatWithWakeLock = false;

        KeyEvent repeatEvent = KeyEvent.changeTimeRepeat(event,
                SystemClock.uptimeMillis(), 1, event.getFlags() | KeyEvent.FLAG_LONG_PRESS);
        if (DEBUG_INPUT) {
            Slog.d(TAG, "dispatchMediaKeyRepeatWithWakeLock: " + repeatEvent);
        }

        dispatchMediaKeyWithWakeLockToAudioService(repeatEvent);
        mBroadcastWakeLock.release();
    }

    void dispatchMediaKeyWithWakeLockToAudioService(KeyEvent event) {
        if (ActivityManagerNative.isSystemReady()) {
            MediaSessionLegacyHelper.getHelper(mContext).sendMediaButtonEvent(event, true);
        }
    }

    void launchVoiceAssistWithWakeLock(boolean keyguardActive) {
        IDeviceIdleController dic = IDeviceIdleController.Stub.asInterface(
                ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER));
        if (dic != null) {
            try {
                dic.exitIdle("voice-search");
            } catch (RemoteException e) {
            }
        }
        Intent voiceIntent =
            new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
        voiceIntent.putExtra(RecognizerIntent.EXTRA_SECURE, keyguardActive);
        startActivityAsUser(voiceIntent, UserHandle.CURRENT_OR_SELF);
        mBroadcastWakeLock.release();
    }

    BroadcastReceiver mDockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_DOCK_EVENT.equals(intent.getAction())) {
		   // +++
                int lastDockMode = mDockMode;
                // ---
                mDockMode = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED);
                //+++ Bypass nonsecure keyguard when put device on dock
                if (lastDockMode == Intent.EXTRA_DOCK_STATE_UNDOCKED &&
                     mDockMode == Intent.EXTRA_DOCK_STATE_KEYBOARD) {
                    Log.d(TAG, "unlock keyguard by dock:" + mDockMode);
                    mKeyguardDelegate.doKeyguardBypass(mCurrentUserId);
                }
                //---
            } else {
                try {
                    IUiModeManager uiModeService = IUiModeManager.Stub.asInterface(
                            ServiceManager.getService(Context.UI_MODE_SERVICE));
                    mUiMode = uiModeService.getCurrentModeType();
                } catch (RemoteException e) {
                }
            }
            updateRotation(true);
            synchronized (mLock) {
                updateOrientationListenerLp();
            }
        }
    };

    BroadcastReceiver mDreamReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_DREAMING_STARTED.equals(intent.getAction())) {
                if (mKeyguardDelegate != null) {
                    mKeyguardDelegate.onDreamingStarted();
                }
            } else if (Intent.ACTION_DREAMING_STOPPED.equals(intent.getAction())) {
                if (mKeyguardDelegate != null) {
                    mKeyguardDelegate.onDreamingStopped();
                }
            }
        }
    };

    BroadcastReceiver mMultiuserReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_SWITCHED.equals(intent.getAction())) {
                // tickle the settings observer: this first ensures that we're
                // observing the relevant settings for the newly-active user,
                // and then updates our own bookkeeping based on the now-
                // current user.
                mSettingsObserver.onChange(false);

                // force a re-application of focused window sysui visibility.
                // the window may never have been shown for this user
                // e.g. the keyguard when going through the new-user setup flow
                synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                    mLastSystemUiFlags = 0;
                    updateSystemUiVisibilityLw();
                }
                
                //+++jeson_li for cover
                if(android.os.Build.FEATURES.HAS_TRANSCOVER){
                    mCurrentUserIdLastForCover=mCurrentUserId;
                    if(mSystemBooted&&mHasTranscoverInfoFeature&&mFlipCover2ServiceDelegate!=null){
                        Log.i(TAG, "ACTION_USER_SWITCHED, unbind or rebind cover service");
                        mFlipCover2ServiceDelegate.sendCoverServiceRebindMsg();//SEND MSG (avoid deadlock and long time without response) to unbind or rebind cover service
                    }
                }
                //---jeson_li for cover
            }
        }
    };

    private final Runnable mHiddenNavPanic = new Runnable() {
        @Override
        public void run() {
            synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                if (!isUserSetupComplete()) {
                    // Swipe-up for navigation bar is disabled during setup
                    return;
                }
                mPendingPanicGestureUptime = SystemClock.uptimeMillis();
                mNavigationBarController.showTransient();
            }
        }
    };

    private void requestTransientBars(WindowState swipeTarget) {
        synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
            if (!isUserSetupComplete()) {
                // Swipe-up for navigation bar is disabled during setup
                return;
            }
            boolean sb = mStatusBarController.checkShowTransientBarLw();
            boolean nb = mNavigationBarController.checkShowTransientBarLw();
            if (sb || nb) {
                // Don't show status bar when swiping on already visible navigation bar
                if (!nb && swipeTarget == mNavigationBar) {
                    if (DEBUG) Slog.d(TAG, "Not showing transient bar, wrong swipe target");
                    return;
                }
                if (sb) mStatusBarController.showTransient();
                if (nb) mNavigationBarController.showTransient();
                mImmersiveModeConfirmation.confirmCurrentPrompt();
                updateSystemUiVisibilityLw();
            }
        }
    }

    // Called on the PowerManager's Notifier thread.
    @Override
    public void startedGoingToSleep(int why) {
        if (DEBUG_WAKEUP) Slog.i(TAG, "Started going to sleep... (why=" + why + ")");
        mUnlockScreenOnWakingUp=false;//add by jeson_li for cover,TT-722917
        mCameraGestureTriggeredDuringGoingToSleep = false;
        mGoingToSleep = true;
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.onStartedGoingToSleep(why);
        }
    }

    // Called on the PowerManager's Notifier thread.
    @Override
    public void finishedGoingToSleep(int why) {
        EventLog.writeEvent(70000, 0);
        if (DEBUG_WAKEUP) Slog.i(TAG, "Finished going to sleep... (why=" + why + ")");
        MetricsLogger.histogram(mContext, "screen_timeout", mLockScreenTimeout / 1000);

        mGoingToSleep = false;

        // We must get this work done here because the power manager will drop
        // the wake lock and let the system suspend once this function returns.
        synchronized (mLock) {
            mAwake = false;
            updateWakeGestureListenerLp();
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.onFinishedGoingToSleep(why,
                    mCameraGestureTriggeredDuringGoingToSleep);
        }
        mCameraGestureTriggeredDuringGoingToSleep = false;
        // +++ jeson_li: ViewFlipCover
        if(android.os.Build.FEATURES.HAS_TRANSCOVER){
            if(isCoverClosed()&&mFlipCover2ServiceDelegate!=null){
                mFlipCover2ServiceDelegate.onScreenTurnedOff(why);
            }
        }
        // --- jeson_li: ViewFlipCover
    }

    // Called on the PowerManager's Notifier thread.
    @Override
    public void startedWakingUp() {
        EventLog.writeEvent(70000, 1);
        if (DEBUG_WAKEUP) Slog.i(TAG, "Started waking up...");

        // Since goToSleep performs these functions synchronously, we must
        // do the same here.  We cannot post this work to a handler because
        // that might cause it to become reordered with respect to what
        // may happen in a future call to goToSleep.
        synchronized (mLock) {
            mAwake = true;

            updateWakeGestureListenerLp();
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }

        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.onStartedWakingUp();
        }
        // +++ jeson_li: ViewFlipCover
        if(android.os.Build.FEATURES.HAS_TRANSCOVER){
            if (isCoverClosed()&&mFlipCover2ServiceDelegate != null) {
                mFlipCover2ServiceDelegate.onScreenTurnedOn();
            }
        }
        // --- jeson_li: ViewFlipCover
    }

    // Called on the PowerManager's Notifier thread.
    @Override
    public void finishedWakingUp() {
        if (DEBUG_WAKEUP) Slog.i(TAG, "Finished waking up...");
        //+++add by jeson_li to fix TT-722917
        if(mUnlockScreenOnWakingUp&&mKeyguardDelegate!=null&&mHandler!=null){
            Log.i(TAG, "unlock non-secure lock screen for finishedWakingUp");
            mHandler.removeMessages(MSG_FLIPCOVER_UNLOCK_LOCKSCREEN);
            mHandler.sendEmptyMessage(MSG_FLIPCOVER_UNLOCK_LOCKSCREEN);
        }
        mUnlockScreenOnWakingUp=false;
        //---add by jeson_li to fix TT-722917
    }

    private void wakeUpFromPowerKey(long eventTime) {
        wakeUp(eventTime, mAllowTheaterModeWakeFromPowerKey, "android.policy:POWER");
    }

    private boolean wakeUp(long wakeTime, boolean wakeInTheaterMode, String reason) {
        final boolean theaterModeEnabled = isTheaterModeEnabled();
        if (!wakeInTheaterMode && theaterModeEnabled) {
            return false;
        }

        if (theaterModeEnabled) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.THEATER_MODE_ON, 0);
        }

        mPowerManager.wakeUp(wakeTime, reason);
        return true;
    }

    private void finishKeyguardDrawn() {
        synchronized (mLock) {
            if (!mScreenOnEarly || mKeyguardDrawComplete) {
                return; // We are not awake yet or we have already informed of this event.
            }

            mKeyguardDrawComplete = true;
            if (mKeyguardDelegate != null) {
                mHandler.removeMessages(MSG_KEYGUARD_DRAWN_TIMEOUT);
            }
            mWindowManagerDrawComplete = false;
        }

        // ... eventually calls finishWindowsDrawn which will finalize our screen turn on
        // as well as enabling the orientation change logic/sensor.
        mWindowManagerInternal.waitForAllWindowsDrawn(mWindowManagerDrawCallback,
                WAITING_FOR_DRAWN_TIMEOUT);
    }

    // Called on the DisplayManager's DisplayPowerController thread.
    @Override
    public void screenTurnedOff() {
        if (DEBUG_WAKEUP) Slog.i(TAG, "Screen turned off...");

        updateScreenOffSleepToken(true);
        synchronized (mLock) {
            mScreenOnEarly = false;
            mScreenOnFully = false;
            mKeyguardDrawComplete = false;
            mWindowManagerDrawComplete = false;
            mScreenOnListener = null;
            updateOrientationListenerLp();

            if (mKeyguardDelegate != null) {
                mKeyguardDelegate.onScreenTurnedOff();
            }
        }
    }

    // Called on the DisplayManager's DisplayPowerController thread.
    @Override
    public void screenTurningOn(final ScreenOnListener screenOnListener) {
        if (DEBUG_WAKEUP) Slog.i(TAG, "Screen turning on...");
        EventLog.writeEvent(70000, 1);

        updateScreenOffSleepToken(false);
        synchronized (mLock) {
            mScreenOnEarly = true;
            mScreenOnFully = false;
            mKeyguardDrawComplete = false;
            mWindowManagerDrawComplete = false;
            mScreenOnListener = screenOnListener;

            if (mKeyguardDelegate != null) {
                mHandler.removeMessages(MSG_KEYGUARD_DRAWN_TIMEOUT);
                mHandler.sendEmptyMessageDelayed(MSG_KEYGUARD_DRAWN_TIMEOUT, 1000);
                mKeyguardDelegate.onScreenTurningOn(mKeyguardDrawnCallback);
            } else {
                if (DEBUG_WAKEUP) Slog.d(TAG,
                        "null mKeyguardDelegate: setting mKeyguardDrawComplete.");
                finishKeyguardDrawn();
            }
        }
        // +++ [TT-346735]
        //if (mKeyguardDelegate != null && mRequireKeyguardDoneWhenScreenOn) {
         //   mRequireKeyguardDoneWhenScreenOn = false;
        //    mKeyguardDelegate.doKeyguardBypass();
        //}
        // ---
    }

    // Called on the DisplayManager's DisplayPowerController thread.
    @Override
    public void screenTurnedOn() {
        synchronized (mLock) {
            if (mKeyguardDelegate != null) {
                mKeyguardDelegate.onScreenTurnedOn();
            }
        }
    }

    private void finishWindowsDrawn() {
        synchronized (mLock) {
            if (!mScreenOnEarly || mWindowManagerDrawComplete) {
                return; // Screen is not turned on or we did already handle this case earlier.
            }

            mWindowManagerDrawComplete = true;
        }

        finishScreenTurningOn();
    }

    private void finishScreenTurningOn() {
        synchronized (mLock) {
            // We have just finished drawing screen content. Since the orientation listener
            // gets only installed when all windows are drawn, we try to install it again.
            updateOrientationListenerLp();
        }
        final ScreenOnListener listener;
        final boolean enableScreen;
        synchronized (mLock) {
            if (DEBUG_WAKEUP) Slog.d(TAG,
                    "finishScreenTurningOn: mAwake=" + mAwake
                            + ", mScreenOnEarly=" + mScreenOnEarly
                            + ", mScreenOnFully=" + mScreenOnFully
                            + ", mKeyguardDrawComplete=" + mKeyguardDrawComplete
                            + ", mWindowManagerDrawComplete=" + mWindowManagerDrawComplete);

            if (mScreenOnFully || !mScreenOnEarly || !mWindowManagerDrawComplete
                    || (mAwake && !mKeyguardDrawComplete)) {
                return; // spurious or not ready yet
            }

            if (DEBUG_WAKEUP) Slog.i(TAG, "Finished screen turning on...");
            listener = mScreenOnListener;
            mScreenOnListener = null;
            mScreenOnFully = true;

            // Remember the first time we draw the keyguard so we know when we're done with
            // the main part of booting and can enable the screen and hide boot messages.
            if (!mKeyguardDrawnOnce && mAwake) {
                mKeyguardDrawnOnce = true;
                enableScreen = true;
                if (mBootMessageNeedsHiding) {
                    mBootMessageNeedsHiding = false;
                    hideBootMessages();
                }
            } else {
                enableScreen = false;
            }
        }

        if (listener != null) {
            listener.onScreenOn();
        }

        if (enableScreen) {
            try {
                mWindowManager.enableScreenIfNeeded();
            } catch (RemoteException unhandled) {
            }
        }
    }

    private void handleHideBootMessage() {
        synchronized (mLock) {
            if (!mKeyguardDrawnOnce) {
                mBootMessageNeedsHiding = true;
                return; // keyguard hasn't drawn the first time yet, not done booting
            }
        }

        if (mBootMsgDialog != null) {
            if (DEBUG_WAKEUP) Slog.d(TAG, "handleHideBootMessage: dismissing");
            mBootMsgDialog.dismiss();
            mBootMsgDialog = null;
        }
    }

    @Override
    public boolean isScreenOn() {
        return mScreenOnFully;
    }

    /** {@inheritDoc} */
    @Override
    public void enableKeyguard(boolean enabled) {
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.setKeyguardEnabled(enabled);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void exitKeyguardSecurely(OnKeyguardExitResult callback) {
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.verifyUnlock(callback);
        }
    }

    private boolean isKeyguardShowingAndNotOccluded() {
        if (mKeyguardDelegate == null) return false;
        return mKeyguardDelegate.isShowing() && !mKeyguardOccluded;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyguardLocked() {
        return keyguardOn();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyguardSecure(int userId) {
        if (mKeyguardDelegate == null) return false;
        return mKeyguardDelegate.isSecure(userId);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyguardShowingOrOccluded() {
        return mKeyguardDelegate == null ? false : mKeyguardDelegate.isShowing();
    }

    /** {@inheritDoc} */
    @Override
    public boolean inKeyguardRestrictedKeyInputMode() {
        if (mKeyguardDelegate == null) return false;
        return mKeyguardDelegate.isInputRestricted();
    }

    @Override
    public void dismissKeyguardLw() {
        if (mKeyguardDelegate != null && mKeyguardDelegate.isShowing()) {
            if (DEBUG_KEYGUARD) Slog.d(TAG, "PWM.dismissKeyguardLw");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //+++Chilin_Wang@asus.com, Instant camera porting
                    if ((mIsLongPressInstantCameraEnabled || mIsInstantCameraEnabled) && !isScreenOn() && !isKeyguardSecure(mCurrentUserId)) {
                        Slog.d(TAG, "InstantCamera: calls keyguardDone in not secure!");
                        mKeyguardDelegate.keyguardDone(false, true);
                    } else {
                        //--- Instant camera porting
                        // ask the keyguard to prompt the user to authenticate if necessary
                        mKeyguardDelegate.dismiss();
                    }
                }
            });
        }
    }

    @Override
    public void notifyActivityDrawnForKeyguardLw() {
        if (mKeyguardDelegate != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mKeyguardDelegate.onActivityDrawn();
                }
            });
        }
    }

    @Override
    public boolean isKeyguardDrawnLw() {
        synchronized (mLock) {
            return mKeyguardDrawnOnce;
        }
    }

    @Override
    public void startKeyguardExitAnimation(long startTime, long fadeoutDuration) {
        if (mKeyguardDelegate != null) {
            if (DEBUG_KEYGUARD) Slog.d(TAG, "PWM.startKeyguardExitAnimation");
            mKeyguardDelegate.startKeyguardExitAnimation(startTime, fadeoutDuration);
        }
    }

    @Override
    public void getStableInsetsLw(int displayRotation, int displayWidth, int displayHeight,
            Rect outInsets) {
        outInsets.setEmpty();

        // Navigation bar and status bar.
        getNonDecorInsetsLw(displayRotation, displayWidth, displayHeight, outInsets);
        if (mStatusBar != null) {
            outInsets.top = mStatusBarHeight;
        }
    }

    @Override
    public void getNonDecorInsetsLw(int displayRotation, int displayWidth, int displayHeight,
            Rect outInsets) {
        outInsets.setEmpty();

        // Only navigation bar
        if (mNavigationBar != null) {
            int position = navigationBarPosition(displayWidth, displayHeight, displayRotation);
            if (position == NAV_BAR_BOTTOM) {
                outInsets.bottom = getNavigationBarHeight(displayRotation, mUiMode);
            } else if (position == NAV_BAR_RIGHT) {
                outInsets.right = getNavigationBarWidth(displayRotation, mUiMode);
            } else if (position == NAV_BAR_LEFT) {
                outInsets.left = getNavigationBarWidth(displayRotation, mUiMode);
            }
        }
    }

    @Override
    public boolean isNavBarForcedShownLw(WindowState windowState) {
        return mForceShowSystemBars;
    }

    @Override
    public boolean isDockSideAllowed(int dockSide) {

        // We do not allow all dock sides at which the navigation bar touches the docked stack.
        if (!mNavigationBarCanMove) {
            return dockSide == DOCKED_TOP || dockSide == DOCKED_LEFT || dockSide == DOCKED_RIGHT;
        } else {
            return dockSide == DOCKED_TOP || dockSide == DOCKED_LEFT;
        }
    }

    void sendCloseSystemWindows() {
        PhoneWindow.sendCloseSystemWindows(mContext, null);
    }

    void sendCloseSystemWindows(String reason) {
        PhoneWindow.sendCloseSystemWindows(mContext, reason);
    }

    @Override
    public int rotationForOrientationLw(int orientation, int lastRotation) {
        if (false) {
            Slog.v(TAG, "rotationForOrientationLw(orient="
                        + orientation + ", last=" + lastRotation
                        + "); user=" + mUserRotation + " "
                        + ((mUserRotationMode == WindowManagerPolicy.USER_ROTATION_LOCKED)
                            ? "USER_ROTATION_LOCKED" : "")
                        );
        }

        // robert_lcc@asus.com
        // shutdown animation
        if("1".equals(SystemProperties.get("sys.shutdown.animation.request", "0"))){
            Log.w(TAG, "sys.shutdown.animation.request is 1 !!!");
            return Surface.ROTATION_0;
        }
        // robert_lcc@asus.com

        if (mForceDefaultOrientation) {
            return Surface.ROTATION_0;
        }

        synchronized (mLock) {
            int sensorRotation = mOrientationListener.getProposedRotation(); // may be -1
            if (sensorRotation < 0) {
                sensorRotation = lastRotation;
            }

            final int preferredRotation;
            if (mLidState == LID_OPEN && mLidOpenRotation >= 0) {
                // Ignore sensor when lid switch is open and rotation is forced.
                preferredRotation = mLidOpenRotation;
            } else if (mDockMode == Intent.EXTRA_DOCK_STATE_CAR
                    && (mCarDockEnablesAccelerometer || mCarDockRotation >= 0)) {
                // Ignore sensor when in car dock unless explicitly enabled.
                // This case can override the behavior of NOSENSOR, and can also
                // enable 180 degree rotation while docked.
                preferredRotation = mCarDockEnablesAccelerometer
                        ? sensorRotation : mCarDockRotation;
            } else if ((mDockMode == Intent.EXTRA_DOCK_STATE_DESK
                    || mDockMode == Intent.EXTRA_DOCK_STATE_LE_DESK
                    || mDockMode == Intent.EXTRA_DOCK_STATE_HE_DESK)
                    && (mDeskDockEnablesAccelerometer || mDeskDockRotation >= 0)) {
                // Ignore sensor when in desk dock unless explicitly enabled.
                // This case can override the behavior of NOSENSOR, and can also
                // enable 180 degree rotation while docked.
                preferredRotation = mDeskDockEnablesAccelerometer
                        ? sensorRotation : mDeskDockRotation;
            } else if (mHdmiPlugged && mDemoHdmiRotationLock) {
                // Ignore sensor when plugged into HDMI when demo HDMI rotation lock enabled.
                // Note that the dock orientation overrides the HDMI orientation.
                preferredRotation = mDemoHdmiRotation;
            } else if (mHdmiPlugged && mDockMode == Intent.EXTRA_DOCK_STATE_UNDOCKED
                    && mUndockedHdmiRotation >= 0) {
                // Ignore sensor when plugged into HDMI and an undocked orientation has
                // been specified in the configuration (only for legacy devices without
                // full multi-display support).
                // Note that the dock orientation overrides the HDMI orientation.
                preferredRotation = mUndockedHdmiRotation;
            } else if (mDemoRotationLock) {
                // Ignore sensor when demo rotation lock is enabled.
                // Note that the dock orientation and HDMI rotation lock override this.
                preferredRotation = mDemoRotation;
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
                // Application just wants to remain locked in the last rotation.
                preferredRotation = lastRotation;
            } else if (!mSupportAutoRotation) {
                // If we don't support auto-rotation then bail out here and ignore
                // the sensor and any rotation lock settings.
                preferredRotation = -1;
            } else if ((mUserRotationMode == WindowManagerPolicy.USER_ROTATION_FREE
                            && (orientation == ActivityInfo.SCREEN_ORIENTATION_USER
                                    || orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    || orientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                                    || orientation == ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                                    || orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_USER))
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) {
                // Otherwise, use sensor only if requested by the application or enabled
                // by default for USER or UNSPECIFIED modes.  Does not apply to NOSENSOR.
                if (mAllowAllRotations < 0) {
                    // Can't read this during init() because the context doesn't
                    // have display metrics at that time so we cannot determine
                    // tablet vs. phone then.
                    mAllowAllRotations = mContext.getResources().getBoolean(
                            com.android.internal.R.bool.config_allowAllRotations) ? 1 : 0;
                }
                if (sensorRotation != Surface.ROTATION_180
                        || mAllowAllRotations == 1
                        || orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                        || orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_USER) {
                    preferredRotation = sensorRotation;
                } else {
                    preferredRotation = lastRotation;
                }
            } else if (mUserRotationMode == WindowManagerPolicy.USER_ROTATION_LOCKED
                    && orientation != ActivityInfo.SCREEN_ORIENTATION_NOSENSOR) {
                // Apply rotation lock.  Does not apply to NOSENSOR.
                // The idea is that the user rotation expresses a weak preference for the direction
                // of gravity and as NOSENSOR is never affected by gravity, then neither should
                // NOSENSOR be affected by rotation lock (although it will be affected by docks).
                preferredRotation = mUserRotation;
            } else if (mDockMode != Intent.EXTRA_DOCK_STATE_UNDOCKED
                    && (mAsusDockEnablesAccelerometer || mAsusDockRotation >= 0)) {
                // Ignore sensor when in keyboard dock unless explicitly enabled.
                // This case can override the behavior of NOSENSOR, and can also
                // enable 180 degree rotation while docked.
                preferredRotation = mAsusDockEnablesAccelerometer
                        ? sensorRotation : mAsusDockRotation;
            } else {
                // No overriding preference.
                // We will do exactly what the application asked us to do.
                preferredRotation = -1;
            }

            /// M:[ALPS00117318] @{
            if (DEBUG_ORIENTATION) {
                Slog.v(TAG, "rotationForOrientationLw(appReqQrientation = "
                            + orientation + ", lastOrientation = " + lastRotation
                            + ", sensorRotation = " + sensorRotation
                            + ", UserRotation = " + mUserRotation
                            + ", LidState = " + mLidState
                            + ", DockMode = " + mDockMode
                            + ", DeskDockEnable = " + mDeskDockEnablesAccelerometer
                            + ", CarDockEnable = " + mCarDockEnablesAccelerometer
                            + ", HdmiPlugged = " + mHdmiPlugged
                            + ", Accelerometer = " + mAccelerometerDefault
                            + ", AllowAllRotations = " + mAllowAllRotations
                            + ")");
            }
            /// @}

            switch (orientation) {
                case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                    // Return portrait unless overridden.
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mPortraitRotation;

                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    // Return landscape unless overridden.
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mLandscapeRotation;

                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                    // Return reverse portrait unless overridden.
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mUpsideDownRotation;

                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    // Return seascape unless overridden.
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    return mSeascapeRotation;

                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE:
                    // Return either landscape rotation.
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    if (isLandscapeOrSeascape(lastRotation)) {
                        return lastRotation;
                    }
                    return mLandscapeRotation;

                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                case ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT:
                    // Return either portrait rotation.
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    if (isAnyPortrait(lastRotation)) {
                        return lastRotation;
                    }
                    return mPortraitRotation;

                default:
                    // For USER, UNSPECIFIED, NOSENSOR, SENSOR and FULL_SENSOR,
                    // just return the preferred orientation we already calculated.
                    if (preferredRotation >= 0) {
                        return preferredRotation;
                    }
                    return Surface.ROTATION_0;
            }
        }
    }

    @Override
    public boolean rotationHasCompatibleMetricsLw(int orientation, int rotation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return isAnyPortrait(rotation);

            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                return isLandscapeOrSeascape(rotation);

            default:
                return true;
        }
    }

    @Override
    public void setRotationLw(int rotation) {
        mOrientationListener.setCurrentRotation(rotation);
    }

    private boolean isLandscapeOrSeascape(int rotation) {
        return rotation == mLandscapeRotation || rotation == mSeascapeRotation;
    }

    private boolean isAnyPortrait(int rotation) {
        return rotation == mPortraitRotation || rotation == mUpsideDownRotation;
    }

    @Override
    public int getUserRotationMode() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0, UserHandle.USER_CURRENT) != 0 ?
                        WindowManagerPolicy.USER_ROTATION_FREE :
                                WindowManagerPolicy.USER_ROTATION_LOCKED;
    }

    // User rotation: to be used when all else fails in assigning an orientation to the device
    @Override
    public void setUserRotationMode(int mode, int rot) {
        ContentResolver res = mContext.getContentResolver();

        // mUserRotationMode and mUserRotation will be assigned by the content observer
        if (mode == WindowManagerPolicy.USER_ROTATION_LOCKED) {
            Settings.System.putIntForUser(res,
                    Settings.System.USER_ROTATION,
                    rot,
                    UserHandle.USER_CURRENT);
            // robert_lcc@asus.com
            // shutdown animation
            if("1".equals(SystemProperties.get("sys.shutdown.animation.request", "0"))){
                Log.w(TAG, "sys.shutdown.animation.request is 1 !!!, DON'T revise Settings.System.ACCELEROMETER_ROTATION");
            } else {
                Log.w(TAG, "sys.shutdown.animation.request is 0 !!!, revise Settings.System.ACCELEROMETER_ROTATION to 0");

                Settings.System.putIntForUser(res,
                        Settings.System.ACCELEROMETER_ROTATION,
                        0,
                        UserHandle.USER_CURRENT);
            }
            // robert_lcc@asus.com
        } else {
            Settings.System.putIntForUser(res,
                    Settings.System.ACCELEROMETER_ROTATION,
                    1,
                    UserHandle.USER_CURRENT);
        }
    }

    @Override
    public void setSafeMode(boolean safeMode) {
        mSafeMode = safeMode;
        performHapticFeedbackLw(null, safeMode
                ? HapticFeedbackConstants.SAFE_MODE_ENABLED
                : HapticFeedbackConstants.SAFE_MODE_DISABLED, true);
    }

    static long[] getLongIntArray(Resources r, int resid) {
        int[] ar = r.getIntArray(resid);
        if (ar == null) {
            return null;
        }
        long[] out = new long[ar.length];
        for (int i=0; i<ar.length; i++) {
            out[i] = ar[i];
        }
        return out;
    }

    /** {@inheritDoc} */
    @Override
    public void systemReady() {
        mKeyguardDelegate = new KeyguardServiceDelegate(mContext);
        mKeyguardDelegate.onSystemReady();

        readCameraLensCoverState();
        updateUiMode();
        boolean bindKeyguardNow;
        synchronized (mLock) {
            updateOrientationListenerLp();
            mSystemReady = true;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateSettings();
                }
            });

            bindKeyguardNow = mDeferBindKeyguard;
            if (bindKeyguardNow) {
                // systemBooted ran but wasn't able to bind to the Keyguard, we'll do it now.
                mDeferBindKeyguard = false;
            }
        }

        if (bindKeyguardNow) {
            mKeyguardDelegate.bindService(mContext);
            mKeyguardDelegate.onBootCompleted();
        }
        mSystemGestures.systemReady();
        mImmersiveModeConfirmation.systemReady();
        // +++ jeson_li: ViewFlipCover
        if(android.os.Build.FEATURES.HAS_TRANSCOVER){
            if(mHasTranscoverInfoFeature){
                if (mFlipCover2ServiceDelegate == null) {
                    mFlipCover2ServiceDelegate = new FlipCover2ServiceDelegate(mContext, this);
                }
                mFlipCover2ServiceDelegate.onSystemReady();
            }
        }
        // --- jeson_li: ViewFlipCover
        //Begin: hungjie_tseng@asus.com, Alwayson
        if(Build.FEATURES.ENABLE_ALWAYS_ON) {
            mAlwaysOnController = LocalServices.getService(AlwaysOnControllerInternal.class);
        }
        //End: hungjie_tseng@asus.com, Alwayson
        //BEGIN : roy_huang@asus.com
        if(Build.FEATURES.ENABLE_INADVERTENTTOUCH) {
            mInadvertentTouchController = LocalServices.getService(InadvertentTouchControllerInternal.class);
        }
        //END : roy_huang@asus.com
    }

    /** {@inheritDoc} */
    @Override
    public void systemBooted() {
        boolean bindKeyguardNow = false;
        synchronized (mLock) {
            // Time to bind Keyguard; take care to only bind it once, either here if ready or
            // in systemReady if not.
            if (mKeyguardDelegate != null) {
                bindKeyguardNow = true;
            } else {
                // Because mKeyguardDelegate is null, we know that the synchronized block in
                // systemReady didn't run yet and setting this will actually have an effect.
                mDeferBindKeyguard = true;
            }
        }
        if (bindKeyguardNow) {
            mKeyguardDelegate.bindService(mContext);
            mKeyguardDelegate.onBootCompleted();
        }
        synchronized (mLock) {
            mSystemBooted = true;
        }
         // +++ jeson_li: ViewFlipCover
        if(android.os.Build.FEATURES.HAS_TRANSCOVER){
            mIsTranscoverEnabledLastForCover=mIsTranscoverEnabled;
            mCurrentUserIdLastForCover=mCurrentUserId;
            mTranscoverAutomaticUnlockLastForCover=mTranscoverAutomaticUnlock;
            if (mHasTranscoverInfoFeature) {
                if (mFlipCover2ServiceDelegate == null) {
                    mFlipCover2ServiceDelegate = new FlipCover2ServiceDelegate(mContext, this);
                }
                mFlipCover2ServiceDelegate.onBootCompleted();
            }
            if (mIsTranscoverEnabled) {
                updateRotation(true);
            }
            readLidState();
            Log.i(TAG, "systemBooted, mLidState:"+mLidState);
            validateFlipCoverForLidState(false,isPrivateUser());
        }
        //--- jeson_li: ViewFlipCover
        startedWakingUp();
        screenTurningOn(null);
        screenTurnedOn();

        // wangyan@wind-mobi.com add Feature #823 2017/08/26 start
        ZenMotionGesturesUtils.initZenmotionSwitch();
        //  wangyan@wind-mobi.com add Feature #823 2017/08/26 end
        	
    }

    ProgressDialog mBootMsgDialog = null;

    /** {@inheritDoc} */
    @Override
    public void showBootMessage(final CharSequence msg, final boolean always) {
        mHandler.post(new Runnable() {
            @Override public void run() {
                if (mBootMsgDialog == null) {
                    int theme;
                    if (mHasFeatureWatch) {
                        theme = com.android.internal.R.style.Theme_Micro_Dialog_Alert;
                    } else if (mContext.getPackageManager().hasSystemFeature(FEATURE_TELEVISION)) {
                        theme = com.android.internal.R.style.Theme_Leanback_Dialog_Alert;
                    } else {
                        theme = 0;
                    }

                    mBootMsgDialog = new ProgressDialog(mContext, theme) {
                        // This dialog will consume all events coming in to
                        // it, to avoid it trying to do things too early in boot.
                        @Override public boolean dispatchKeyEvent(KeyEvent event) {
                            return true;
                        }
                        @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
                            return true;
                        }
                        @Override public boolean dispatchTouchEvent(MotionEvent ev) {
                            return true;
                        }
                        @Override public boolean dispatchTrackballEvent(MotionEvent ev) {
                            return true;
                        }
                        @Override public boolean dispatchGenericMotionEvent(MotionEvent ev) {
                            return true;
                        }
                        @Override public boolean dispatchPopulateAccessibilityEvent(
                                AccessibilityEvent event) {
                            return true;
                        }
                    };
                    if (mContext.getPackageManager().isUpgrade()) {
                        mBootMsgDialog.setTitle(R.string.android_upgrading_title);
                    } else {
                        mBootMsgDialog.setTitle(R.string.android_start_title);
                    }
                    mBootMsgDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    mBootMsgDialog.setIndeterminate(true);
                    mBootMsgDialog.getWindow().setType(
                            WindowManager.LayoutParams.TYPE_BOOT_PROGRESS);
                    mBootMsgDialog.getWindow().addFlags(
                            WindowManager.LayoutParams.FLAG_DIM_BEHIND
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                    mBootMsgDialog.getWindow().setDimAmount(1);
                    WindowManager.LayoutParams lp = mBootMsgDialog.getWindow().getAttributes();
                    lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
                    mBootMsgDialog.getWindow().setAttributes(lp);
                    mBootMsgDialog.setCancelable(false);
                    mBootMsgDialog.show();
                }
                mBootMsgDialog.setMessage(msg);
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public void hideBootMessages() {
        mHandler.sendEmptyMessage(MSG_HIDE_BOOT_MESSAGE);
    }

    /** {@inheritDoc} */
    @Override
    public void userActivity() {
        // ***************************************
        // NOTE NOTE NOTE NOTE NOTE NOTE NOTE NOTE
        // ***************************************
        // THIS IS CALLED FROM DEEP IN THE POWER MANAGER
        // WITH ITS LOCKS HELD.
        //
        // This code must be VERY careful about the locks
        // it acquires.
        // In fact, the current code acquires way too many,
        // and probably has lurking deadlocks.

        /// M:[ALPS00062902] When the user activiy flag is enabled,
        /// it notifies the intent "STK_USERACTIVITY" @{
        synchronized (mStkLock) {
            if (mIsStkUserActivityEnabled) {
                /// M:[ALPS00389865]
                mHandler.post(mNotifyStk);
            }
        }
        /// @}

        synchronized (mScreenLockTimeout) {
            if (mLockScreenTimerActive) {
                // reset the timer
                mHandler.removeCallbacks(mScreenLockTimeout);
                mHandler.postDelayed(mScreenLockTimeout, mLockScreenTimeout);
            }
        }
    }

    /**
     * +++Mist Liao, for checking if lid close,
     * which mean we should prevent turning on screen
     *{@inheritDoc}
     */
    private boolean isLidClosedOnDock() {
        // If do not have hall sensor or dock features, just return false
        if (!mHasHallSensorFeature || !mHasDockFeature)
            return false;

        if (DEBUG) Log.d(TAG, "isLidClosedOnDock, mLidState:" + mLidState);

        // Only read lid state when screen off
        if (!isScreenOn())
            readLidState();

        return (mLidState == LID_CLOSED);
    }

    class ScreenLockTimeout implements Runnable {
        Bundle options;

        @Override
        public void run() {
            synchronized (this) {
                if (localLOGV) Log.v(TAG, "mScreenLockTimeout activating keyguard");
                if (mKeyguardDelegate != null) {
                    mKeyguardDelegate.doKeyguardTimeout(options);
                }
                mLockScreenTimerActive = false;
                options = null;
            }
        }

        public void setLockOptions(Bundle options) {
            this.options = options;
        }
    }

    ScreenLockTimeout mScreenLockTimeout = new ScreenLockTimeout();

    @Override
    public void lockNow(Bundle options) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DEVICE_POWER, null);
        mHandler.removeCallbacks(mScreenLockTimeout);
        if (options != null) {
            // In case multiple calls are made to lockNow, we don't wipe out the options
            // until the runnable actually executes.
            mScreenLockTimeout.setLockOptions(options);
        }
        mHandler.post(mScreenLockTimeout);
    }

    private void updateLockScreenTimeout() {
        synchronized (mScreenLockTimeout) {
            boolean enable = (mAllowLockscreenWhenOn && mAwake &&
                    mKeyguardDelegate != null && mKeyguardDelegate.isSecure(mCurrentUserId));
            if (mLockScreenTimerActive != enable) {
                if (enable) {
                    if (localLOGV) Log.v(TAG, "setting lockscreen timer");
                    mHandler.removeCallbacks(mScreenLockTimeout); // remove any pending requests
                    mHandler.postDelayed(mScreenLockTimeout, mLockScreenTimeout);
                } else {
                    if (localLOGV) Log.v(TAG, "clearing lockscreen timer");
                    mHandler.removeCallbacks(mScreenLockTimeout);
                }
                mLockScreenTimerActive = enable;
            }
        }
    }

    private void updateDreamingSleepToken(boolean acquire) {
        if (acquire) {
            if (mDreamingSleepToken == null) {
                mDreamingSleepToken = mActivityManagerInternal.acquireSleepToken("Dream");
            }
        } else {
            if (mDreamingSleepToken != null) {
                mDreamingSleepToken.release();
                mDreamingSleepToken = null;
            }
        }
    }

    private void updateScreenOffSleepToken(boolean acquire) {
        if (acquire) {
            if (mScreenOffSleepToken == null) {
                mScreenOffSleepToken = mActivityManagerInternal.acquireSleepToken("ScreenOff");
            }
        } else {
            if (mScreenOffSleepToken != null) {
                mScreenOffSleepToken.release();
                mScreenOffSleepToken = null;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void enableScreenAfterBoot() {
        readLidState();
        applyLidSwitchState();
        updateRotation(true);
    }

    private void applyLidSwitchState() {
		//+++jeson_li flipcover
        if(android.os.Build.FEATURES.HAS_TRANSCOVER){
            if (mLidState == LID_CLOSED && mLidControlsSleep) {
                if(mIsTranscoverEnabled&&!mHasTranscoverInfoFeature){
                    Log.d(TAG, "sleep for applyLidSwitchState");
                    mPowerManager.goToSleep(SystemClock.uptimeMillis(),
                            PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH,
                            PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
                }
            }
        //---jeson_li flipcover
        } else if (mLidState == LID_CLOSED && mLidControlsSleep) {
            mPowerManager.goToSleep(SystemClock.uptimeMillis(),
                    PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH,
                    PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
        } else if (mLidState == LID_CLOSED && mLidControlsScreenLock) {
            mWindowManagerFuncs.lockDeviceNow();
        }

        synchronized (mLock) {
            updateWakeGestureListenerLp();
        }
    }

    void updateUiMode() {
        if (mUiModeManager == null) {
            mUiModeManager = IUiModeManager.Stub.asInterface(
                    ServiceManager.getService(Context.UI_MODE_SERVICE));
        }
        try {
            mUiMode = mUiModeManager.getCurrentModeType();
        } catch (RemoteException e) {
        }
    }

    void updateRotation(boolean alwaysSendConfiguration) {
        try {
            //set orientation on WindowManager
            mWindowManager.updateRotation(alwaysSendConfiguration, false);
        } catch (RemoteException e) {
            // Ignore
        }
    }

    void updateRotation(boolean alwaysSendConfiguration, boolean forceRelayout) {
        try {
            //set orientation on WindowManager
            mWindowManager.updateRotation(alwaysSendConfiguration, forceRelayout);
        } catch (RemoteException e) {
            // Ignore
        }
    }

    /**
     * Return an Intent to launch the currently active dock app as home.  Returns
     * null if the standard home should be launched, which is the case if any of the following is
     * true:
     * <ul>
     *  <li>The device is not in either car mode or desk mode
     *  <li>The device is in car mode but mEnableCarDockHomeCapture is false
     *  <li>The device is in desk mode but ENABLE_DESK_DOCK_HOME_CAPTURE is false
     *  <li>The device is in car mode but there's no CAR_DOCK app with METADATA_DOCK_HOME
     *  <li>The device is in desk mode but there's no DESK_DOCK app with METADATA_DOCK_HOME
     * </ul>
     * @return A dock intent.
     */
    Intent createHomeDockIntent() {
        Intent intent = null;

        // What home does is based on the mode, not the dock state.  That
        // is, when in car mode you should be taken to car home regardless
        // of whether we are actually in a car dock.
        if (mUiMode == Configuration.UI_MODE_TYPE_CAR) {
            if (mEnableCarDockHomeCapture) {
                intent = mCarDockIntent;
            }
        } else if (mUiMode == Configuration.UI_MODE_TYPE_DESK) {
            if (ENABLE_DESK_DOCK_HOME_CAPTURE) {
                intent = mDeskDockIntent;
            }
        } else if (mUiMode == Configuration.UI_MODE_TYPE_WATCH
                && (mDockMode == Intent.EXTRA_DOCK_STATE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_HE_DESK
                        || mDockMode == Intent.EXTRA_DOCK_STATE_LE_DESK)) {
            // Always launch dock home from home when watch is docked, if it exists.
            intent = mDeskDockIntent;
        }

        if (intent == null) {
            return null;
        }

        ActivityInfo ai = null;
        ResolveInfo info = mContext.getPackageManager().resolveActivityAsUser(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_META_DATA,
                mCurrentUserId);
        if (info != null) {
            ai = info.activityInfo;
        }
        if (ai != null
                && ai.metaData != null
                && ai.metaData.getBoolean(Intent.METADATA_DOCK_HOME)) {
            intent = new Intent(intent);
            intent.setClassName(ai.packageName, ai.name);
            return intent;
        }

        return null;
    }

    void startDockOrHome(boolean fromHomeKey, boolean awakenFromDreams) {
        if (awakenFromDreams) {
            awakenDreams();
        }

		 // Add by gaohui@wind-mobi.com 20161107 start to disable home key when enrolling
        if (fromHomeKey && mFingerprintOn) {
            Log.d(TAG, "HOME press bypassed for FP being activated.");
            return;
        }
        // Add by gaohui@wind-mobi.com 20161107 end to disable home key when enrolling
		
        Intent dock = createHomeDockIntent();
        if (dock != null) {
            try {
                if (fromHomeKey) {
                    dock.putExtra(WindowManagerPolicy.EXTRA_FROM_HOME_KEY, fromHomeKey);
                }
                startActivityAsUser(dock, UserHandle.CURRENT);
                return;
            } catch (ActivityNotFoundException e) {
            }
        }

        Intent intent;

        if (fromHomeKey) {
            intent = new Intent(mHomeIntent);
            intent.putExtra(WindowManagerPolicy.EXTRA_FROM_HOME_KEY, fromHomeKey);
        } else {
            intent = mHomeIntent;
        }

        startActivityAsUser(intent, UserHandle.CURRENT);
    }

    /**
     * goes to the home screen
     * @return whether it did anything
     */
    boolean goHome() {
        if (!isUserSetupComplete()) {
            Slog.i(TAG, "Not going home because user setup is in progress.");
            return false;
        }
        if (false) {
            // This code always brings home to the front.
            try {
                ActivityManagerNative.getDefault().stopAppSwitches();
            } catch (RemoteException e) {
            }
            sendCloseSystemWindows();
            startDockOrHome(false /*fromHomeKey*/, true /* awakenFromDreams */);
        } else {
            // This code brings home to the front or, if it is already
            // at the front, puts the device to sleep.
            try {
                if (SystemProperties.getInt("persist.sys.uts-test-mode", 0) == 1) {
                    /// Roll back EndcallBehavior as the cupcake design to pass P1 lab entry.
                    Log.d(TAG, "UTS-TEST-MODE");
                } else {
                    ActivityManagerNative.getDefault().stopAppSwitches();
                    sendCloseSystemWindows();
                    Intent dock = createHomeDockIntent();
                    if (dock != null) {
                        int result = ActivityManagerNative.getDefault()
                                .startActivityAsUser(null, null, dock,
                                        dock.resolveTypeIfNeeded(mContext.getContentResolver()),
                                        null, null, 0,
                                        ActivityManager.START_FLAG_ONLY_IF_NEEDED,
                                        null, null, UserHandle.USER_CURRENT);
                        if (result == ActivityManager.START_RETURN_INTENT_TO_CALLER) {
                            return false;
                        }
                    }
                }
                int result = ActivityManagerNative.getDefault()
                        .startActivityAsUser(null, null, mHomeIntent,
                                mHomeIntent.resolveTypeIfNeeded(mContext.getContentResolver()),
                                null, null, 0,
                                ActivityManager.START_FLAG_ONLY_IF_NEEDED,
                                null, null, UserHandle.USER_CURRENT);
                if (result == ActivityManager.START_RETURN_INTENT_TO_CALLER) {
                    return false;
                }
            } catch (RemoteException ex) {
                // bummer, the activity manager, which is in this process, is dead
            }
        }
        return true;
    }

    @Override
    public void setCurrentOrientationLw(int newOrientation) {
        synchronized (mLock) {
            if (newOrientation != mCurrentAppOrientation) {
                mCurrentAppOrientation = newOrientation;
                updateOrientationListenerLp();
            }
        }
    }

    private void performAuditoryFeedbackForAccessibilityIfNeed() {
        if (!isGlobalAccessibilityGestureEnabled()) {
            return;
        }
        AudioManager audioManager = (AudioManager) mContext.getSystemService(
                Context.AUDIO_SERVICE);
        if (audioManager.isSilentMode()) {
            return;
        }
        Ringtone ringTone = RingtoneManager.getRingtone(mContext,
                Settings.System.DEFAULT_NOTIFICATION_URI);
        ringTone.setStreamType(AudioManager.STREAM_MUSIC);
        ringTone.play();
    }

    private boolean isTheaterModeEnabled() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.THEATER_MODE_ON, 0) == 1;
    }

    private boolean isGlobalAccessibilityGestureEnabled() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.ENABLE_ACCESSIBILITY_GLOBAL_GESTURE_ENABLED, 0) == 1;
    }

    @Override
    public boolean performHapticFeedbackLw(WindowState win, int effectId, boolean always) {
        if (!mVibrator.hasVibrator()) {
            return false;
        }
        final boolean hapticsDisabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0, UserHandle.USER_CURRENT) == 0;
        if (hapticsDisabled && !always) {
            return false;
        }
        long[] pattern = null;
        switch (effectId) {
            case HapticFeedbackConstants.LONG_PRESS:
                pattern = mLongPressVibePattern;
                break;
            case HapticFeedbackConstants.VIRTUAL_KEY:
                pattern = mVirtualKeyVibePattern;
                break;
            case HapticFeedbackConstants.KEYBOARD_TAP:
                pattern = mKeyboardTapVibePattern;
                break;
            case HapticFeedbackConstants.CLOCK_TICK:
                pattern = mClockTickVibePattern;
                break;
            case HapticFeedbackConstants.CALENDAR_DATE:
                pattern = mCalendarDateVibePattern;
                break;
            case HapticFeedbackConstants.SAFE_MODE_DISABLED:
                pattern = mSafeModeDisabledVibePattern;
                break;
            case HapticFeedbackConstants.SAFE_MODE_ENABLED:
                pattern = mSafeModeEnabledVibePattern;
                break;
            case HapticFeedbackConstants.CONTEXT_CLICK:
                pattern = mContextClickVibePattern;
                break;
            default:
                return false;
        }
        int owningUid;
        String owningPackage;
        if (win != null) {
            owningUid = win.getOwningUid();
            owningPackage = win.getOwningPackage();
        } else {
            owningUid = android.os.Process.myUid();
            owningPackage = mContext.getOpPackageName();
        }
        if (pattern.length == 1) {
            // One-shot vibration
            mVibrator.vibrate(owningUid, owningPackage, pattern[0], VIBRATION_ATTRIBUTES, AudioAttributes.LEVEL_DEFAULT, AudioAttributes.CATEGORY_TOUCH);
        } else {
            // Pattern vibration
            mVibrator.vibrate(owningUid, owningPackage, pattern, -1, VIBRATION_ATTRIBUTES, AudioAttributes.LEVEL_DEFAULT, AudioAttributes.CATEGORY_TOUCH);
        }
        return true;
    }

    @Override
    public void keepScreenOnStartedLw() {
    }

    @Override
    public void keepScreenOnStoppedLw() {
        if (isKeyguardShowingAndNotOccluded()) {
            mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
        }
    }

    private int updateSystemUiVisibilityLw() {
        // If there is no window focused, there will be nobody to handle the events
        // anyway, so just hang on in whatever state we're in until things settle down.
        final WindowState win = mFocusedWindow != null ? mFocusedWindow
                : mTopFullscreenOpaqueWindowState;
        if (win == null) {
            return 0;
        }
        if ((win.getAttrs().privateFlags & PRIVATE_FLAG_KEYGUARD) != 0 && mHideLockScreen == true) {
            // We are updating at a point where the keyguard has gotten
            // focus, but we were last in a state where the top window is
            // hiding it.  This is probably because the keyguard as been
            // shown while the top window was displayed, so we want to ignore
            // it here because this is just a very transient change and it
            // will quickly lose focus once it correctly gets hidden.
            return 0;
        }

        int tmpVisibility = PolicyControl.getSystemUiVisibility(win, null)
                & ~mResettingSystemUiFlags
                & ~mForceClearedSystemUiFlags;

        // BEGIN: archie_huang@asus.com
        // For feature: Navigation visibility control
        if (Build.FEATURES.ENABLE_NAV_VIS_CTRL
                && mTopFullscreenOpaqueWindowState != null && mStatusBar != null) {
            WindowManager.LayoutParams statusBarAttrs = mStatusBar.getAttrs();
            boolean statusBarExpanded = statusBarAttrs.height == MATCH_PARENT
                    && statusBarAttrs.width == MATCH_PARENT;
            if (!statusBarExpanded) {
                tmpVisibility = PolicyControl.updateNavigationVisibilityCtrl(tmpVisibility, mTopFullscreenOpaqueWindowState)
                    & ~mResettingSystemUiFlags
                    & ~mForceClearedSystemUiFlags;
            }
        }
        // END: archie_huang@asus.com

        if (mForcingShowNavBar && win.getSurfaceLayer() < mForcingShowNavBarLayer) {
            tmpVisibility &= ~PolicyControl.adjustClearableFlags(win, View.SYSTEM_UI_CLEARABLE_FLAGS);
        }

        final int fullscreenVisibility = updateLightStatusBarLw(0 /* vis */,
                mTopFullscreenOpaqueWindowState, mTopFullscreenOpaqueOrDimmingWindowState);
        final int dockedVisibility = updateLightStatusBarLw(0 /* vis */,
                mTopDockedOpaqueWindowState, mTopDockedOpaqueOrDimmingWindowState);
        mWindowManagerFuncs.getStackBounds(HOME_STACK_ID, mNonDockedStackBounds);
        mWindowManagerFuncs.getStackBounds(DOCKED_STACK_ID, mDockedStackBounds);
        final int visibility = updateSystemBarsLw(win, mLastSystemUiFlags, tmpVisibility);
        final int diff = visibility ^ mLastSystemUiFlags;
        final int fullscreenDiff = fullscreenVisibility ^ mLastFullscreenStackSysUiFlags;
        final int dockedDiff = dockedVisibility ^ mLastDockedStackSysUiFlags;
        final boolean needsMenu = win.getNeedsMenuLw(mTopFullscreenOpaqueWindowState);
        if (diff == 0 && fullscreenDiff == 0 && dockedDiff == 0 && mLastFocusNeedsMenu == needsMenu
                && mFocusedApp == win.getAppToken()
                && mLastNonDockedStackBounds.equals(mNonDockedStackBounds)
                && mLastDockedStackBounds.equals(mDockedStackBounds)) {
            return 0;
        }
        mLastSystemUiFlags = visibility;
        mLastFullscreenStackSysUiFlags = fullscreenVisibility;
        mLastDockedStackSysUiFlags = dockedVisibility;
        mLastFocusNeedsMenu = needsMenu;
        mFocusedApp = win.getAppToken();
        final Rect fullscreenStackBounds = new Rect(mNonDockedStackBounds);
        final Rect dockedStackBounds = new Rect(mDockedStackBounds);
        mHandler.post(new Runnable() {
                @Override
                public void run() {
                    StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
                    if (statusbar != null) {
                        statusbar.setSystemUiVisibility(visibility, fullscreenVisibility,
                                dockedVisibility, 0xffffffff, fullscreenStackBounds,
                                dockedStackBounds, win.toString());
                        statusbar.topAppWindowChanged(needsMenu);
                    }
                }
            });
        return diff;
    }

    private int updateLightStatusBarLw(int vis, WindowState opaque, WindowState opaqueOrDimming) {
        WindowState statusColorWin = isStatusBarKeyguard() && !mHideLockScreen
                ? mStatusBar
                : opaqueOrDimming;

        if (statusColorWin != null) {
            if (statusColorWin == opaque) {
                // If the top fullscreen-or-dimming window is also the top fullscreen, respect
                // its light flag.
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                // BEGIN: archie_huang@asus.com
                // For feature: Screen Maximum Aspect Ratio
                //add liangfeng@wind-mobi.com for TT1080328 2017/9/13 start
                //Once SystemUI crash,occur NullPointerException
                if(mStatusBar != null) {
                    if (statusColorWin.isFitScreen()
                            || statusColorWin.getFrameLw().width() == mStatusBar.getFrameLw().width()) {
                        vis |= PolicyControl.getSystemUiVisibility(statusColorWin, null)
                                & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    }
                }
                //add liangfeng@wind-mobi.com for TT1080328 2017/9/13 end
                // END: archie_huang@asus.com
            } else if (statusColorWin != null && statusColorWin.isDimming()) {
                // Otherwise if it's dimming, clear the light flag.
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }
        return vis;
    }

    private boolean drawsSystemBarBackground(WindowState win) {
        return win == null || (win.getAttrs().flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0;
    }

    private boolean forcesDrawStatusBarBackground(WindowState win) {
        return win == null || (win.getAttrs().privateFlags
                & PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND) != 0;
    }

    private int updateSystemBarsLw(WindowState win, int oldVis, int vis) {
        final boolean dockedStackVisible = mWindowManagerInternal.isStackVisible(DOCKED_STACK_ID);
        final boolean freeformStackVisible =
                mWindowManagerInternal.isStackVisible(FREEFORM_WORKSPACE_STACK_ID);
        final boolean resizing = mWindowManagerInternal.isDockedDividerResizing();

        // We need to force system bars when the docked stack is visible, when the freeform stack
        // is visible but also when we are resizing for the transitions when docked stack
        // visibility changes.
        mForceShowSystemBars = dockedStackVisible || freeformStackVisible || resizing;
        final boolean forceOpaqueStatusBar = mForceShowSystemBars && !mForceStatusBarFromKeyguard;

        // apply translucent bar vis flags
        WindowState fullscreenTransWin = isStatusBarKeyguard() && !mHideLockScreen
                ? mStatusBar
                : mTopFullscreenOpaqueWindowState;
        vis = mStatusBarController.applyTranslucentFlagLw(fullscreenTransWin, vis, oldVis);
        vis = mNavigationBarController.applyTranslucentFlagLw(fullscreenTransWin, vis, oldVis);
        final int dockedVis = mStatusBarController.applyTranslucentFlagLw(
                mTopDockedOpaqueWindowState, 0, 0);

        final boolean fullscreenDrawsStatusBarBackground =
                (drawsSystemBarBackground(mTopFullscreenOpaqueWindowState)
                        && (vis & View.STATUS_BAR_TRANSLUCENT) == 0)
                || forcesDrawStatusBarBackground(mTopFullscreenOpaqueWindowState);
        final boolean dockedDrawsStatusBarBackground =
                (drawsSystemBarBackground(mTopDockedOpaqueWindowState)
                        && (dockedVis & View.STATUS_BAR_TRANSLUCENT) == 0)
                || forcesDrawStatusBarBackground(mTopDockedOpaqueWindowState);

        // prevent status bar interaction from clearing certain flags
        int type = win.getAttrs().type;
        boolean statusBarHasFocus = type == TYPE_STATUS_BAR;
        if (statusBarHasFocus && !isStatusBarKeyguard()) {
            int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (mHideLockScreen) {
                flags |= View.STATUS_BAR_TRANSLUCENT | View.NAVIGATION_BAR_TRANSLUCENT;
            }
            vis = (vis & ~flags) | (oldVis & flags);
        }

        if (fullscreenDrawsStatusBarBackground && dockedDrawsStatusBarBackground) {
            vis |= View.STATUS_BAR_TRANSPARENT;
            vis &= ~View.STATUS_BAR_TRANSLUCENT;
        } else if ((!areTranslucentBarsAllowed() && fullscreenTransWin != mStatusBar)
                || forceOpaqueStatusBar) {
            vis &= ~(View.STATUS_BAR_TRANSLUCENT | View.STATUS_BAR_TRANSPARENT);
        }

        vis = configureNavBarOpacity(vis, dockedStackVisible, freeformStackVisible, resizing);

        // update status bar
        boolean immersiveSticky =
                (vis & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
        final boolean hideStatusBarWM =
                mTopFullscreenOpaqueWindowState != null
                && (PolicyControl.getWindowFlags(mTopFullscreenOpaqueWindowState, null)
                        & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
        final boolean hideStatusBarSysui =
                (vis & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
        final boolean hideNavBarSysui =
                (vis & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0;

        final boolean transientStatusBarAllowed = mStatusBar != null
                && (statusBarHasFocus || (!mForceShowSystemBars
                        && (hideStatusBarWM || (hideStatusBarSysui && immersiveSticky))));

        final boolean transientNavBarAllowed = mNavigationBar != null
                && !mForceShowSystemBars && hideNavBarSysui && immersiveSticky;

        final long now = SystemClock.uptimeMillis();
        final boolean pendingPanic = mPendingPanicGestureUptime != 0
                && now - mPendingPanicGestureUptime <= PANIC_GESTURE_EXPIRATION;
        if (pendingPanic && hideNavBarSysui && !isStatusBarKeyguard() && mKeyguardDrawComplete) {
            // The user performed the panic gesture recently, we're about to hide the bars,
            // we're no longer on the Keyguard and the screen is ready. We can now request the bars.
            mPendingPanicGestureUptime = 0;
            mStatusBarController.showTransient();
            mNavigationBarController.showTransient();
        }

        final boolean denyTransientStatus = mStatusBarController.isTransientShowRequested()
                && !transientStatusBarAllowed && hideStatusBarSysui;
        final boolean denyTransientNav = mNavigationBarController.isTransientShowRequested()
                && !transientNavBarAllowed;
        if (denyTransientStatus || denyTransientNav || mForceShowSystemBars) {
            // clear the clearable flags instead
            clearClearableFlagsLw();
            vis &= ~View.SYSTEM_UI_CLEARABLE_FLAGS;
        }

        final boolean immersive = (vis & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0;
        immersiveSticky = (vis & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
        final boolean navAllowedHidden = immersive || immersiveSticky;

        if (hideNavBarSysui && !navAllowedHidden && windowTypeToLayerLw(win.getBaseType())
                > windowTypeToLayerLw(TYPE_INPUT_CONSUMER)) {
            // We can't hide the navbar from this window otherwise the input consumer would not get
            // the input events.
            vis = (vis & ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

        vis = mStatusBarController.updateVisibilityLw(transientStatusBarAllowed, oldVis, vis);

        // update navigation bar
        boolean oldImmersiveMode = isImmersiveMode(oldVis);
        boolean newImmersiveMode = isImmersiveMode(vis);
        if (win != null && oldImmersiveMode != newImmersiveMode
            /// M: When gesture disabled, don't show the immersive mode user guide
            && (win.getSystemUiVisibility()
            & View.SYSTEM_UI_FLAG_IMMERSIVE_GESTURE_ISOLATED) == 0) {
            final String pkg = win.getOwningPackage();
            mImmersiveModeConfirmation.immersiveModeChangedLw(pkg, newImmersiveMode,
                    isUserSetupComplete());
        }

        // BEGIN: archie_huang@asus.com
        // For feature: Navigation visibility control
        if (Build.FEATURES.ENABLE_NAV_VIS_CTRL
                && mNavigationBarController.isTransientShowing()
                && (oldVis & View.NAVIGATION_BAR_TRANSIENT) != 0
                && (oldVis & View.NAVIGATION_BAR_UNHIDE) != 0) {
            vis |= View.NAVIGATION_BAR_UNHIDE; // waiting for sysui's reqponse
        }
        // END: archie_huang@asus.com

        vis = mNavigationBarController.updateVisibilityLw(transientNavBarAllowed, oldVis, vis);

        return vis;
    }

    /**
     * @return the current visibility flags with the nav-bar opacity related flags toggled based
     *         on the nav bar opacity rules chosen by {@link #mNavBarOpacityMode}.
     */
    private int configureNavBarOpacity(int visibility, boolean dockedStackVisible,
            boolean freeformStackVisible, boolean isDockedDividerResizing) {
        if (mNavBarOpacityMode == NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED) {
            if (dockedStackVisible || freeformStackVisible || isDockedDividerResizing) {
                visibility = setNavBarOpaqueFlag(visibility);
            }
        } else if (mNavBarOpacityMode == NAV_BAR_TRANSLUCENT_WHEN_FREEFORM_OPAQUE_OTHERWISE) {
            if (isDockedDividerResizing) {
                visibility = setNavBarOpaqueFlag(visibility);
            } else if (freeformStackVisible) {
                visibility = setNavBarTranslucentFlag(visibility);
            } else {
                visibility = setNavBarOpaqueFlag(visibility);
            }
        }

        if (!areTranslucentBarsAllowed()) {
            visibility &= ~View.NAVIGATION_BAR_TRANSLUCENT;
        }
        return visibility;
    }

    private int setNavBarOpaqueFlag(int visibility) {
        return visibility &= ~(View.NAVIGATION_BAR_TRANSLUCENT | View.NAVIGATION_BAR_TRANSPARENT);
    }

    private int setNavBarTranslucentFlag(int visibility) {
        visibility &= ~View.NAVIGATION_BAR_TRANSPARENT;
        return visibility |= View.NAVIGATION_BAR_TRANSLUCENT;
    }

    private void clearClearableFlagsLw() {
        int newVal = mResettingSystemUiFlags | View.SYSTEM_UI_CLEARABLE_FLAGS;
        if (newVal != mResettingSystemUiFlags) {
            mResettingSystemUiFlags = newVal;
            mWindowManagerFuncs.reevaluateStatusBarVisibility();
        }
    }

    private boolean isImmersiveMode(int vis) {
        final int flags = View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        return mNavigationBar != null
                && (vis & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
                && (vis & flags) != 0
                && canHideNavigationBar();
    }

    /**
     * @return whether the navigation or status bar can be made translucent
     *
     * This should return true unless touch exploration is not enabled or
     * R.boolean.config_enableTranslucentDecor is false.
     */
    private boolean areTranslucentBarsAllowed() {
        return mTranslucentDecorEnabled;
    }

    // Use this instead of checking config_showNavigationBar so that it can be consistently
    // overridden by qemu.hw.mainkeys in the emulator.
    @Override
    public boolean hasNavigationBar() {
        return mHasNavigationBar;
    }

    @Override
    public void setLastInputMethodWindowLw(WindowState ime, WindowState target) {
        mLastInputMethodWindow = ime;
        mLastInputMethodTargetWindow = target;
    }

    @Override
    public int getInputMethodWindowVisibleHeightLw() {
        return mDockBottom - mCurBottom;
    }

    @Override
    public void setCurrentUserLw(int newUserId) {
        mCurrentUserId = newUserId;
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.setCurrentUser(newUserId);
        }
        StatusBarManagerInternal statusBar = getStatusBarManagerInternal();
        if (statusBar != null) {
            statusBar.setCurrentUser(newUserId);
        }
        setLastInputMethodWindowLw(null, null);
    }

    @Override
    public boolean canMagnifyWindow(int windowType) {
        switch (windowType) {
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD:
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG:
            case WindowManager.LayoutParams.TYPE_NAVIGATION_BAR:
            case WindowManager.LayoutParams.TYPE_MAGNIFICATION_OVERLAY: {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTopLevelWindow(int windowType) {
        if (windowType >= WindowManager.LayoutParams.FIRST_SUB_WINDOW
                && windowType <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
            return (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        }
        return true;
    }

    @Override
    public void dump(String prefix, PrintWriter pw, String[] args) {
        /// M: runtime switch debug flags @{
        if (args.length > 0  && args[0].contains("-d")) {
            if ("-d enable 0".equals(args[0])) {
                DEBUG = true;
                localLOGV = true;
            } else if ("-d enable 3".equals(args[0])) {
                DEBUG_LAYOUT = true;
            } else if ("-d enable 6".equals(args[0])) {
                DEBUG_INPUT = true;
            } else if ("-d enable 10".equals(args[0])) {
                DEBUG_ORIENTATION = true;
                //Fix build error: mOrientationListener.setLogEnabled(true);
            } else if ("-d disable 0".equals(args[0])) {
                DEBUG = false;
                localLOGV = false;
            } else if ("-d disable 3".equals(args[0])) {
                DEBUG_LAYOUT = false;
            } else if ("-d disable 6".equals(args[0])) {
                DEBUG_INPUT = false;
            } else if ("-d disable 10".equals(args[0])) {
                DEBUG_ORIENTATION = false;
                //Fix build error: mOrientationListener.setLogEnabled(false);
            } else if ("-d enable a".equals(args[0])) {
                DEBUG = true;
                localLOGV = true;
                DEBUG_LAYOUT = true;
                DEBUG_INPUT = true;
                DEBUG_ORIENTATION = true;
                DEBUG_KEYGUARD = true;
                DEBUG_STARTING_WINDOW = true;
                DEBUG_WAKEUP = true;
            } else if ("-d disable a".equals(args[0])) {
                DEBUG = false;
                localLOGV = false;
                DEBUG_LAYOUT = false;
                DEBUG_INPUT = false;
                DEBUG_ORIENTATION = false;
                DEBUG_KEYGUARD = false;
                DEBUG_STARTING_WINDOW = false;
                DEBUG_WAKEUP = false;
            }
            return;
        }
        /// @}
        pw.print(prefix); pw.print("mSafeMode="); pw.print(mSafeMode);
                pw.print(" mSystemReady="); pw.print(mSystemReady);
                pw.print(" mSystemBooted="); pw.println(mSystemBooted);
        pw.print(prefix); pw.print("mLidState="); pw.print(mLidState);
                pw.print(" mLidOpenRotation="); pw.print(mLidOpenRotation);
                pw.print(" mCameraLensCoverState="); pw.print(mCameraLensCoverState);
                pw.print(" mHdmiPlugged="); pw.println(mHdmiPlugged);
        if (mLastSystemUiFlags != 0 || mResettingSystemUiFlags != 0
                || mForceClearedSystemUiFlags != 0) {
            pw.print(prefix); pw.print("mLastSystemUiFlags=0x");
                    pw.print(Integer.toHexString(mLastSystemUiFlags));
                    pw.print(" mResettingSystemUiFlags=0x");
                    pw.print(Integer.toHexString(mResettingSystemUiFlags));
                    pw.print(" mForceClearedSystemUiFlags=0x");
                    pw.println(Integer.toHexString(mForceClearedSystemUiFlags));
        }
        if (mLastFocusNeedsMenu) {
            pw.print(prefix); pw.print("mLastFocusNeedsMenu=");
                    pw.println(mLastFocusNeedsMenu);
        }
        pw.print(prefix); pw.print("mWakeGestureEnabledSetting=");
                pw.println(mWakeGestureEnabledSetting);

        pw.print(prefix); pw.print("mSupportAutoRotation="); pw.println(mSupportAutoRotation);
        pw.print(prefix); pw.print("mUiMode="); pw.print(mUiMode);
                pw.print(" mDockMode="); pw.print(mDockMode);
                pw.print(" mEnableCarDockHomeCapture="); pw.print(mEnableCarDockHomeCapture);
                pw.print(" mCarDockRotation="); pw.print(mCarDockRotation);
                pw.print(" mDeskDockRotation="); pw.println(mDeskDockRotation);
        pw.print(prefix); pw.print("mUserRotationMode="); pw.print(mUserRotationMode);
                pw.print(" mUserRotation="); pw.print(mUserRotation);
                pw.print(" mAllowAllRotations="); pw.println(mAllowAllRotations);
        pw.print(prefix); pw.print("mCurrentAppOrientation="); pw.println(mCurrentAppOrientation);
        pw.print(prefix); pw.print("mCarDockEnablesAccelerometer=");
                pw.print(mCarDockEnablesAccelerometer);
                pw.print(" mDeskDockEnablesAccelerometer=");
                pw.println(mDeskDockEnablesAccelerometer);
        pw.print(prefix); pw.print("mLidKeyboardAccessibility=");
                pw.print(mLidKeyboardAccessibility);
                pw.print(" mLidNavigationAccessibility="); pw.print(mLidNavigationAccessibility);
                pw.print(" mLidControlsScreenLock="); pw.println(mLidControlsScreenLock);
                pw.print(" mLidControlsSleep="); pw.println(mLidControlsSleep);
        pw.print(prefix);
                pw.print(" mLongPressOnBackBehavior="); pw.println(mLongPressOnBackBehavior);
        pw.print(prefix);
                pw.print("mShortPressOnPowerBehavior="); pw.print(mShortPressOnPowerBehavior);
                pw.print(" mLongPressOnPowerBehavior="); pw.println(mLongPressOnPowerBehavior);
        pw.print(prefix);
                pw.print("mDoublePressOnPowerBehavior="); pw.print(mDoublePressOnPowerBehavior);
                pw.print(" mTriplePressOnPowerBehavior="); pw.println(mTriplePressOnPowerBehavior);
        pw.print(prefix); pw.print("mHasSoftInput="); pw.println(mHasSoftInput);
        pw.print(prefix); pw.print("mAwake="); pw.println(mAwake);
        pw.print(prefix); pw.print("mScreenOnEarly="); pw.print(mScreenOnEarly);
                pw.print(" mScreenOnFully="); pw.println(mScreenOnFully);
        pw.print(prefix); pw.print("mKeyguardDrawComplete="); pw.print(mKeyguardDrawComplete);
                pw.print(" mWindowManagerDrawComplete="); pw.println(mWindowManagerDrawComplete);
        pw.print(prefix); pw.print("mOrientationSensorEnabled=");
                pw.println(mOrientationSensorEnabled);
        pw.print(prefix); pw.print("mOverscanScreen=("); pw.print(mOverscanScreenLeft);
                pw.print(","); pw.print(mOverscanScreenTop);
                pw.print(") "); pw.print(mOverscanScreenWidth);
                pw.print("x"); pw.println(mOverscanScreenHeight);
        if (mOverscanLeft != 0 || mOverscanTop != 0
                || mOverscanRight != 0 || mOverscanBottom != 0) {
            pw.print(prefix); pw.print("mOverscan left="); pw.print(mOverscanLeft);
                    pw.print(" top="); pw.print(mOverscanTop);
                    pw.print(" right="); pw.print(mOverscanRight);
                    pw.print(" bottom="); pw.println(mOverscanBottom);
        }
        pw.print(prefix); pw.print("mRestrictedOverscanScreen=(");
                pw.print(mRestrictedOverscanScreenLeft);
                pw.print(","); pw.print(mRestrictedOverscanScreenTop);
                pw.print(") "); pw.print(mRestrictedOverscanScreenWidth);
                pw.print("x"); pw.println(mRestrictedOverscanScreenHeight);
        pw.print(prefix); pw.print("mUnrestrictedScreen=("); pw.print(mUnrestrictedScreenLeft);
                pw.print(","); pw.print(mUnrestrictedScreenTop);
                pw.print(") "); pw.print(mUnrestrictedScreenWidth);
                pw.print("x"); pw.println(mUnrestrictedScreenHeight);
        pw.print(prefix); pw.print("mRestrictedScreen=("); pw.print(mRestrictedScreenLeft);
                pw.print(","); pw.print(mRestrictedScreenTop);
                pw.print(") "); pw.print(mRestrictedScreenWidth);
                pw.print("x"); pw.println(mRestrictedScreenHeight);
        pw.print(prefix); pw.print("mStableFullscreen=("); pw.print(mStableFullscreenLeft);
                pw.print(","); pw.print(mStableFullscreenTop);
                pw.print(")-("); pw.print(mStableFullscreenRight);
                pw.print(","); pw.print(mStableFullscreenBottom); pw.println(")");
        pw.print(prefix); pw.print("mStable=("); pw.print(mStableLeft);
                pw.print(","); pw.print(mStableTop);
                pw.print(")-("); pw.print(mStableRight);
                pw.print(","); pw.print(mStableBottom); pw.println(")");
        pw.print(prefix); pw.print("mSystem=("); pw.print(mSystemLeft);
                pw.print(","); pw.print(mSystemTop);
                pw.print(")-("); pw.print(mSystemRight);
                pw.print(","); pw.print(mSystemBottom); pw.println(")");
        pw.print(prefix); pw.print("mCur=("); pw.print(mCurLeft);
                pw.print(","); pw.print(mCurTop);
                pw.print(")-("); pw.print(mCurRight);
                pw.print(","); pw.print(mCurBottom); pw.println(")");
        pw.print(prefix); pw.print("mContent=("); pw.print(mContentLeft);
                pw.print(","); pw.print(mContentTop);
                pw.print(")-("); pw.print(mContentRight);
                pw.print(","); pw.print(mContentBottom); pw.println(")");
        pw.print(prefix); pw.print("mVoiceContent=("); pw.print(mVoiceContentLeft);
                pw.print(","); pw.print(mVoiceContentTop);
                pw.print(")-("); pw.print(mVoiceContentRight);
                pw.print(","); pw.print(mVoiceContentBottom); pw.println(")");
        pw.print(prefix); pw.print("mDock=("); pw.print(mDockLeft);
                pw.print(","); pw.print(mDockTop);
                pw.print(")-("); pw.print(mDockRight);
                pw.print(","); pw.print(mDockBottom); pw.println(")");
        pw.print(prefix); pw.print("mDockLayer="); pw.print(mDockLayer);
                pw.print(" mStatusBarLayer="); pw.println(mStatusBarLayer);
        pw.print(prefix); pw.print("mShowingLockscreen="); pw.print(mShowingLockscreen);
                pw.print(" mShowingDream="); pw.print(mShowingDream);
                pw.print(" mDreamingLockscreen="); pw.print(mDreamingLockscreen);
                pw.print(" mDreamingSleepToken="); pw.println(mDreamingSleepToken);
        if (mLastInputMethodWindow != null) {
            pw.print(prefix); pw.print("mLastInputMethodWindow=");
                    pw.println(mLastInputMethodWindow);
        }
        if (mLastInputMethodTargetWindow != null) {
            pw.print(prefix); pw.print("mLastInputMethodTargetWindow=");
                    pw.println(mLastInputMethodTargetWindow);
        }
        if (mStatusBar != null) {
            pw.print(prefix); pw.print("mStatusBar=");
                    pw.print(mStatusBar); pw.print(" isStatusBarKeyguard=");
                    pw.println(isStatusBarKeyguard());
        }
        if (mNavigationBar != null) {
            pw.print(prefix); pw.print("mNavigationBar=");
                    pw.println(mNavigationBar);
        }
        if (mFocusedWindow != null) {
            pw.print(prefix); pw.print("mFocusedWindow=");
                    pw.println(mFocusedWindow);
        }
        if (mFocusedApp != null) {
            pw.print(prefix); pw.print("mFocusedApp=");
                    pw.println(mFocusedApp);
        }
        if (mWinDismissingKeyguard != null) {
            pw.print(prefix); pw.print("mWinDismissingKeyguard=");
                    pw.println(mWinDismissingKeyguard);
        }
        if (mTopFullscreenOpaqueWindowState != null) {
            pw.print(prefix); pw.print("mTopFullscreenOpaqueWindowState=");
                    pw.println(mTopFullscreenOpaqueWindowState);
        }
        if (mTopFullscreenOpaqueOrDimmingWindowState != null) {
            pw.print(prefix); pw.print("mTopFullscreenOpaqueOrDimmingWindowState=");
                    pw.println(mTopFullscreenOpaqueOrDimmingWindowState);
        }
        if (mForcingShowNavBar) {
            pw.print(prefix); pw.print("mForcingShowNavBar=");
                    pw.println(mForcingShowNavBar); pw.print( "mForcingShowNavBarLayer=");
                    pw.println(mForcingShowNavBarLayer);
        }
        pw.print(prefix); pw.print("mTopIsFullscreen="); pw.print(mTopIsFullscreen);
                pw.print(" mHideLockScreen="); pw.println(mHideLockScreen);
        pw.print(prefix); pw.print("mForceStatusBar="); pw.print(mForceStatusBar);
                pw.print(" mForceStatusBarFromKeyguard=");
                pw.println(mForceStatusBarFromKeyguard);
        pw.print(prefix); pw.print("mDismissKeyguard="); pw.print(mDismissKeyguard);
                pw.print(" mWinDismissingKeyguard="); pw.print(mWinDismissingKeyguard);
                pw.print(" mHomePressed="); pw.println(mHomePressed);
        pw.print(prefix); pw.print("mAllowLockscreenWhenOn="); pw.print(mAllowLockscreenWhenOn);
                pw.print(" mLockScreenTimeout="); pw.print(mLockScreenTimeout);
                pw.print(" mLockScreenTimerActive="); pw.println(mLockScreenTimerActive);
        pw.print(prefix); pw.print("mEndcallBehavior="); pw.print(mEndcallBehavior);
                pw.print(" mIncallPowerBehavior="); pw.print(mIncallPowerBehavior);
                pw.print(" mLongPressOnHomeBehavior="); pw.println(mLongPressOnHomeBehavior);
        pw.print(prefix); pw.print("mLandscapeRotation="); pw.print(mLandscapeRotation);
                pw.print(" mSeascapeRotation="); pw.println(mSeascapeRotation);
        pw.print(prefix); pw.print("mPortraitRotation="); pw.print(mPortraitRotation);
                pw.print(" mUpsideDownRotation="); pw.println(mUpsideDownRotation);
        pw.print(prefix); pw.print("mDemoHdmiRotation="); pw.print(mDemoHdmiRotation);
                pw.print(" mDemoHdmiRotationLock="); pw.println(mDemoHdmiRotationLock);
        pw.print(prefix); pw.print("mUndockedHdmiRotation="); pw.println(mUndockedHdmiRotation);
        // BEGIN: archie_huang@asus.com
        // For feature: Colorful Navigation Bar
        pw.print(prefix); pw.print("mNavigationBarColor="); pw.println(mNavigationBarColor);
        // END: archie_huang@asus.com

        mGlobalKeyManager.dump(prefix, pw);
        mStatusBarController.dump(pw, prefix);
        mNavigationBarController.dump(pw, prefix);
        PolicyControl.dump(prefix, pw);

        if (mWakeGestureListener != null) {
            mWakeGestureListener.dump(pw, prefix);
        }
        if (mOrientationListener != null) {
            mOrientationListener.dump(pw, prefix);
        }
        if (mBurnInProtectionHelper != null) {
            mBurnInProtectionHelper.dump(prefix, pw);
        }
        if (mKeyguardDelegate != null) {
            mKeyguardDelegate.dump(prefix, pw);
        }
    }

    /// M: for build type check
    static final boolean IS_USER_BUILD = ("user".equals(Build.TYPE)
            || "userdebug".equals(Build.TYPE));

    /// M: power-off alarm @{
    private boolean mIsAlarmBoot = isAlarmBoot();
    private boolean mIsShutDown = false;
    private static final String NORMAL_SHUTDOWN_ACTION = "android.intent.action.normal.shutdown";
    private static final String NORMAL_BOOT_ACTION = "android.intent.action.normal.boot";
    ///@}

    ///M : power-off alarm @{
    BroadcastReceiver mPoweroffAlarmReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "mIpoEventReceiver -- onReceive -- entry");
            String action = intent.getAction();
            SystemProperties.set("sys.boot.reason", "0");
            mIsAlarmBoot = false;
            if (action.equals(NORMAL_SHUTDOWN_ACTION)) {
                Log.v(TAG, "Receive NORMAL_SHUTDOWN_ACTION");
                mIsShutDown = true;
            } else if (NORMAL_BOOT_ACTION.equals(action)) {
                Log.v(TAG, "Receive NORMAL_BOOT_ACTION");
                SystemProperties.set("service.bootanim.exit", "0");
                SystemProperties.set("ctl.start", "bootanim");
            }
        }
    };
    ///@}

    /// M: power-off alarm
    ///    add for power-off alarm Check the boot mode whether alarm boot or
    ///    normal boot (including ipo boot). {@
    private boolean isAlarmBoot() {
        String bootReason = SystemProperties.get("sys.boot.reason");
        boolean ret = (bootReason != null && bootReason.equals("1")) ? true
                : false;
        return ret;
    }
    /// @}

    /// M: IPO migration @{
    final Object mKeyDispatchLock = new Object();
    /// M: [ALPS01186555]Fix WUXGA IPO charging animation issue
    int mIPOUserRotation = Surface.ROTATION_0;
    public static final String IPO_DISABLE = "android.intent.action.ACTION_BOOT_IPO";
    public static final String IPO_ENABLE = "android.intent.action.ACTION_SHUTDOWN_IPO";
    /// @}

    /// M: IPO migration @{
    BroadcastReceiver mIpoEventReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "mIpoEventReceiver -- onReceive -- entry");
            String action = intent.getAction();
            if (action.equals(IPO_ENABLE)) {
                Log.v(TAG, "Receive IPO_ENABLE");
                ipoSystemShutdown();
            } else if (action.equals(IPO_DISABLE)) {
                Log.v(TAG, "Receive IPO_DISABLE");
                ipoSystemBooted();
            } else {
                Log.v(TAG, "Receive Fake Intent");
            }
        }
    };
    /// @}

    /// M: IPO migration
    ///    Called after IPO system boot @{
    private void ipoSystemBooted() {

        ///M: power-off alarm @{
        mIsAlarmBoot = isAlarmBoot();
        mIsShutDown = false;
        ///@}

        /// M: [ALPS00519547] Reset effect of FLAG_SHOW_WHEN_LOCKED @{
        mHideLockScreen = false;
        /// @}

        /// M:[ALPS00637635]Solve the disappear GlobalActions dialog
        mScreenshotChordVolumeDownKeyTriggered = false;
        mScreenshotChordVolumeUpKeyTriggered = false;

        // Enable key dispatch
        synchronized (mKeyDispatchLock) {
            mKeyDispatcMode = KEY_DISPATCH_MODE_ALL_ENABLE;
            if (DEBUG_INPUT) {
                Log.v(TAG, "mIpoEventReceiver=" + mKeyDispatcMode);
            }
        }
        /// M: [ALPS01186555]Fix WUXGA IPO charging animation issue @{
        if (mIPOUserRotation != Surface.ROTATION_0) {
            mUserRotation = mIPOUserRotation;
            mIPOUserRotation = Surface.ROTATION_0;
        }
        /// @}
    }
    /// @}

    /// M: IPO migration
    ///    Called before IPO system shutdown @{
    private void ipoSystemShutdown() {
        // Disable key dispatch
        synchronized (mKeyDispatchLock) {
            mKeyDispatcMode = KEY_DISPATCH_MODE_ALL_DISABLE;
            if (DEBUG_INPUT) {
                Log.v(TAG, "mIpoEventReceiver=" + mKeyDispatcMode);
            }
        }
        /// M: [ALPS01186555]Fix WUXGA IPO charging animation issue @{
        if (mUserRotationMode == WindowManagerPolicy.USER_ROTATION_LOCKED
            && mUserRotation != Surface.ROTATION_0) {
            mIPOUserRotation = mUserRotation;
            mUserRotation = Surface.ROTATION_0;
        }
        /// @}
    }
    /// @}

    /// M: [ALPS00062902]THE INTENT of STK UserActivity
    public static final String STK_USERACTIVITY =
        "android.intent.action.stk.USER_ACTIVITY";
    public static final String STK_USERACTIVITY_ENABLE =
        "android.intent.action.stk.USER_ACTIVITY.enable";
    /// M: [ALPS00062902]The global variable to save the state of stk.enable.user_activity
    boolean mIsStkUserActivityEnabled = false;
    /// M: [ALPS00062902]Protect mIsStkUserActivityEnabled be accessed at the multiple places
    private Object mStkLock = new Object();

    /// M: [ALPS00062902]
    BroadcastReceiver mStkUserActivityEnReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.v(TAG, "mStkUserActivityEnReceiver -- onReceive -- entry");

            synchronized (mStkLock) {
                if (action.equals(STK_USERACTIVITY_ENABLE)) {
                    if (DEBUG_INPUT) {
                        Log.v(TAG, "Receive STK_ENABLE");
                    }
                    boolean enabled = intent.getBooleanExtra("state", false);
                    if (enabled != mIsStkUserActivityEnabled) {
                        mIsStkUserActivityEnabled = enabled;
                    }
                } else {
                    if (DEBUG_INPUT) {
                        Log.e(TAG, "Receive Fake Intent");
                    }
                }
            }
            if (DEBUG_INPUT) {
                Log.v(TAG, "mStkUserActivityEnReceiver -- onReceive -- exist "
                            + mIsStkUserActivityEnabled);
            }
        }
    };


    /// M: [ALPS00062902][ALPS00389865]Avoid deadlock @{
    Runnable mNotifyStk = new Runnable() {
        public void run() {
            Intent intent = new Intent(STK_USERACTIVITY);
            mContext.sendBroadcast(intent);
        }
    };
    /// @}

    /// M: Save the screen off reason from the power manager service.
    int mScreenOffReason = -1; //useless

    /// M: KeyDispatch mode @{
    static final int KEY_DISPATCH_MODE_ALL_ENABLE = 0;
    static final int KEY_DISPATCH_MODE_ALL_DISABLE = 1;
    static final int KEY_DISPATCH_MODE_HOME_DISABLE = 2;
    /// @}
    /// M: mKeyDispatcMode : the default value is all enabled.
    int mKeyDispatcMode = KEY_DISPATCH_MODE_ALL_ENABLE;

    private Runnable mKeyRemappingVolumeDownLongPress_Test = new Runnable() {
        public void run() {
            //            mHandler.postDelayed( mKeyRemappingVolumeDownLongPress,0);
            KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK);
            InputManager inputManager
                    = (InputManager) mContext.getSystemService(Context.INPUT_SERVICE);
            Log.d(TAG, ">>>>>>>> InjectEvent Start");
            inputManager.injectInputEvent(keyEvent
                    , InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
            try {
                Log.d(TAG, "***** Sleeping.");
                Thread.sleep(10 * 1000);
                Log.d(TAG, "***** Waking up.");
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "IllegalArgumentException: ", e);
            } catch (SecurityException e) {
                Log.d(TAG, "SecurityException: ", e);
            } catch (InterruptedException e) {
                Log.d(TAG, "InterruptedException: ", e);
            }
            Log.d(TAG, "<<<<<<<< InjectEvent End");
        }
    };
    private long mKeyRemappingSendFakeKeyDownTime;
    private void keyRemappingSendFakeKeyEvent(int action, int keyCode) {
        long eventTime = SystemClock.uptimeMillis();
        if (action == KeyEvent.ACTION_DOWN) {
            mKeyRemappingSendFakeKeyDownTime = eventTime;
        }

        KeyEvent keyEvent
                = new KeyEvent(mKeyRemappingSendFakeKeyDownTime, eventTime, action, keyCode, 0);
        InputManager inputManager = (InputManager) mContext.getSystemService(Context.INPUT_SERVICE);
        inputManager.injectInputEvent(keyEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private boolean mKeyRemappingVolumeUpLongPressed;

    private Runnable mKeyRemappingVolumeUpLongPress = new Runnable() {
        public void run() {
            showRecentApps(false);

            mKeyRemappingVolumeUpLongPressed = true;
        }
    };

    private boolean mKeyRemappingVolumeDownLongPressed;

    private Runnable mKeyRemappingVolumeDownLongPress = new Runnable() {
        public void run() {
            // Emulate clicking Menu key
            keyRemappingSendFakeKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU);
            keyRemappingSendFakeKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU);

            mKeyRemappingVolumeDownLongPressed = true;
        }
    };

    /// M: Screen unpinning @{
    private static final int DISMISS_SCREEN_PINNING_KEY_CODE = KeyEvent.KEYCODE_BACK;
    private void interceptDismissPinningChord() {
        IActivityManager activityManager =
            ActivityManagerNative.asInterface(ServiceManager.checkService("activity"));
        try {
            if (activityManager.isInLockTaskMode()) {
                activityManager.stopLockTaskMode();
            }
        } catch (RemoteException e) {
        }
    }
    /// @}

    /// M:[AppLaunchTime] Improve the mechanism of AppLaunchTime {@
    boolean mAppLaunchTimeEnabled
        = (1 == SystemProperties.getInt("ro.mtk_perf_response_time", 0)) ? true : false;
    /// @}

    /// M: [App Launch Reponse Time Enhancement][FSW] Policy implementation. {@
    /** {@inheritDoc} */
    @Override
    public View addFastStartingWindow(IBinder appToken, String packageName, int theme,
            CompatibilityInfo compatInfo, CharSequence nonLocalizedLabel, int labelRes,
            int icon, int logo, int windowFlags, Bitmap bitmap) {
        if (!SHOW_STARTING_ANIMATIONS) {
            return null;
        }
        if (packageName == null) {
            return null;
        }

        WindowManager wm = null;

        if (true) {
            View view = new View(mContext);

            try {
                Context context = mContext;
                if (DEBUG_STARTING_WINDOW) {
                    Slog.d(TAG, "addFastStartingWindow " + packageName
                        + ": nonLocalizedLabel=" + nonLocalizedLabel + " theme="
                        + Integer.toHexString(theme));
                }

                final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);

                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_STARTING;
                params.flags = windowFlags |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                    ///| WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN

                TypedArray windowStyle = mContext.obtainStyledAttributes(
                    com.android.internal.R.styleable.Window);
                params.windowAnimations = windowStyle.getResourceId(
                    com.android.internal.R.styleable.Window_windowAnimationStyle, 0);

                params.token = appToken;
                params.packageName = packageName;
                params.privateFlags |=
                        WindowManager.LayoutParams.PRIVATE_FLAG_FAKE_HARDWARE_ACCELERATED;
                params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;

                if (!compatInfo.supportsScreen()) {
                    params.privateFlags
                            |= WindowManager.LayoutParams.PRIVATE_FLAG_COMPATIBLE_WINDOW;
                }
                params.setTitle("FastStarting");
                wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

                if (DEBUG_STARTING_WINDOW) {
                    Slog.d(
                        TAG, "Adding starting window for " + packageName
                        + " / " + appToken + ": "
                        + (view.getParent() != null ? view : null));
                }

                //view.setBackground(new BitmapDrawable(mContext.getResources(), bitmap));
                wm.addView(view, params);

                if (mAppLaunchTimeEnabled) {
                    /// M: [App Launch Reponse Time Enhancement] Merge Traversal.
                    WindowManagerGlobal.getInstance().doTraversal(view, true);
                }

                // Only return the view if it was successfully added to the
                // window manager... which we can tell by it having a parent.
                return view.getParent() != null ? view : null;
            } catch (WindowManager.BadTokenException e) {
                // ignore
                Log.w(TAG, appToken + " already running, starting window not displayed. " +
                        e.getMessage());
            } catch (RuntimeException e) {
                // don't crash if something else bad happens, for example a
                // failure loading resources because we are loading from an app
                // on external storage that has been unmounted.
                Log.w(TAG, appToken + " failed creating starting window", e);
            } finally {
                if (view != null && view.getParent() == null) {
                    Log.w(TAG, "view not successfully added to wm, removing view");
                    wm.removeViewImmediate(view);
                }
            }
        }
        return null;
    }
    /// @}

    /// M: Support feature that intercept key before WMS handle @{
    boolean isUspEnable = !"no".equals(SystemProperties.get("ro.mtk_carrierexpress_pack", "no"));
    private boolean interceptKeyBeforeHandling(KeyEvent event) {
        /// M: Support USP feature: disable KEYCODE_POWER when USP is freezed
        if (isUspEnable && KeyEvent.KEYCODE_POWER == event.getKeyCode() &&
                (SystemProperties.getInt("persist.mtk_usp_cfg_ctrl", 0) & 0x4) == 4) {
            return true;
        }
        return false;
    }
    /// @}

    //+++Chilin_Wang@asus.com Instant Camera porting
    private void launchInstantCamera(String src, String suspend, int keyCode) {
        if(isDeviceProvisioned()) {
            mVibrator.vibrate(300);
            //add mohongwu@wind-mobi.com for TT978970 2017/3/31 start
            wakeUp(mScreenshotChordVolumeDownKeyTime,mAllowTheaterModeWakeFromKey,"android.policy:KEY");
            //add mohongwu@wind-mobi.com for TT978970 2017/3/31 end
            Intent intent = null;
            final boolean secureKeyguard = isKeyguardSecure(mCurrentUserId);
            final boolean secureKeyguardLocked = isKeyguardLocked();
            Slog.d(TAG, "InstantCamera : Keyguard is secure ? " + secureKeyguard + ", locked = " + secureKeyguardLocked);
            //modify mohongwu@wind-mobi.com for TT1013495 2017/6/15 start
            //modify sunxiaolong@wind-mobi.com for TT1094777 20171108 begin
            //if (secureKeyguard && secureKeyguardLocked) {
            //if (secureKeyguard) {
            if (secureKeyguard && !mIsLaunchCameraFromFpPending) {
            //modify sunxiaolong@wind-mobi.com for TT1094777 20171108 end
            //modify mohongwu@wind-mobi.com for TT1013495 2017/6/15 end
                intent = new Intent(
                        "com.asus.camera.action.STILL_IMAGE_CAMERA_SECURE")
                        .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            } else {
                intent = new Intent()
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_DEFAULT)
                        .setComponent(new ComponentName("com.asus.camera", "com.asus.camera.CameraApp"));
            }

            intent.putExtra("suspend_status", suspend);
            intent.putExtra("camera_intent_source", src);
            Log.i(TAG, "camera_intent_source: " + src + ", suspend: " + suspend);

            Log.i(TAG, "AddCameraIntentKeyCode: " + keyCode);
            intent.putExtra("instant_camera_keycode", keyCode);

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_SINGLE_TOP |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (!secureKeyguard) {
                try {
                    mWindowManager.dismissKeyguard();
                } catch (RemoteException e) {
                    Log.w(TAG, "InstantCamera : Error dismissing keyguard when launchInstantCamera.", e);
                }
            }

            final Bundle animation = android.app.ActivityOptions.makeCustomAnimation(mContext, 0, 0).toBundle();

            try {
                Slog.d(TAG, String.format("InstantCamera : Starting activity for intent %s at %s",
                        intent, SystemClock.uptimeMillis()));

                final UserHandle user = new UserHandle(UserHandle.USER_CURRENT);

                mContext.startActivityAsUser(intent, animation, user);
            } catch (ActivityNotFoundException e) {
                Slog.w(TAG, "Activity not found for intent + " + intent.getAction());
            }
 //Begin:HJ@asus.com.Adding for inadvertentTouch
            if (Build.FEATURES.ENABLE_INADVERTENTTOUCH) {
                Slog.d(TAG, "<launchInstantCamera>notifyLaunchInstantCamera to mInadvertentTouchController ");
                if (mInadvertentTouchController == null) {
                    mInadvertentTouchController = LocalServices.getService(InadvertentTouchControllerInternal.class);
                    Slog.d(TAG, "<launchInstantCamera> GetLocalService mInadvertentTouchController: " + mInadvertentTouchController);
                }
                if (mInadvertentTouchController != null) {
                    mInadvertentTouchController.notifyLaunchInstantCamera();
                }
            }
            //End:HJ@asus.com.Adding for inadvertentTouch
        }
    }

    private boolean isMusicActive() {
        boolean isMusicActive = false;
        final AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        if (audioManager == null) {
            Slog.w(TAG, "InstantCamera : isMusicActive: Couldn't get AudioManager reference !");
            return isMusicActive;
        }
        isMusicActive = audioManager.isMusicActive() || audioManager.isMusicActiveRemotely();
        Slog.d(TAG, "InstantCamera : isMusicActive=" + isMusicActive);
        return isMusicActive;
    }


    class LongPressLaunchCamera implements Runnable {
        private String mSrc;
        private String mSuspend;
        private int mKeyCode;
        public void set(String intent, String suspend, int keyCode) {
            mSrc = intent;
            mSuspend = suspend;
            mKeyCode = keyCode;
        }
        @Override
        public void run() {
            Slog.d(TAG, "handleLongPressLaunchCamera");
            launchInstantCamera(mSrc,mSuspend,mKeyCode);
        }
    }

    private void handleDoubleClickLaunchCamera(String src, String suspend, int keyCode) {
        Slog.d(TAG, "handleDoubleClickLaunchCamera");
        launchInstantCamera(src,suspend,keyCode);
    }

    private final Runnable mVolumeDownDoubleClickTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVolumeDownDoubleClickPending) {
                mVolumeDownDoubleClickPending = false;
                //Handle volume down key
            }
        }
    };
    private final Runnable mVolumeUpDoubleClickTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVolumeUpDoubleClickPending) {
                mVolumeUpDoubleClickPending = false;
                //Handle volume up key
            }
        }
    };

    private void handleCameraMessage(String event, int what,boolean interactive,int keyCode) {
        mHandler.removeMessages(what);
        Message msg = new Message();
        msg.obj = new String(event);
        if (!interactive) {
            msg.arg1 = 1;
        } else {
            msg.arg1 = 0;
        }
        msg.arg2 = new Integer(keyCode);
        msg.what = new Integer(what);
        mHandler.sendMessage(msg);
    }
    private void handleZennyLaunchCamera(String src, String suspend) {
        Slog.d(TAG, "handleZennyLaunchCamera");
        launchInstantCamera(src,suspend,0);
    }
    private void handleKeyguardScreenStatusMessage(int what, String status) {
        Message msg = new Message();
        msg.what = what;
        msg.obj = new String(status);
        mHandler.sendMessage(msg);
    }

    private void handleLockPhysicalKeyStatusMessage(boolean lock) {
        Message msg = new Message();
        msg.what = MSG_UPDATE_LOCK_PHYSICAL_STATUS;
        msg.arg1 = new Integer(lock ? 1 : 0);
        mHandler.sendMessage(msg);
    }

    class NotifyCameraRunnable implements Runnable {
        private String mEvent;
        public void set(String event) {
            mEvent = event;
        }
        @Override
        public void run() {
            if ("down".equals(mEvent)) {
                Log.i("ToCamera","recent key down");
            } else {
                Log.i("ToCamera","recent key up");
            }
            Intent intent = new Intent();
            intent.setAction("com.asus.camera.action.BLOCK_RECENT_KEY");
            intent.putExtra("hardware:recentkey",mEvent);
            mContext.sendBroadcast(intent);
        }
    }

    private final Runnable mLockRecentKeyTimoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mLockRecentKeyEnabled) {
                Slog.d(TAG, "Recent key toast is time out!");
                mLockRecentKeyEnabled = false;
            }
        }
    };
    //---Instant camera porting

    class PassFunctionKey implements Runnable {
        KeyEvent mKeyEvent;

        PassFunctionKey(KeyEvent keyEvent) {
            mKeyEvent = keyEvent;
        }

        public void run() {
            if (ActivityManagerNative.isSystemReady()) {
                Intent intent = new Intent("com.asus.keyboard.action.FUNCTION_KEY", null); // need to sync with KeyboardService
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.putExtra(Intent.EXTRA_KEY_EVENT, mKeyEvent);
                mContext.sendOrderedBroadcastAsUser(intent, UserHandle.CURRENT, null, mBroadcastDone,
                        mHandler, Activity.RESULT_OK, null, null);
            }
        }
    }

    BroadcastReceiver mBroadcastDone = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            mBroadcastWakeLock.release();
        }
    };
    /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 begin
    private void chooseOtgMode(int which) {
        switch (which) {
            case OTG_PLUG_IN_NORMOL_MODE:
                setPowerReverseChargeEnable(false, OTG_REVERSE_CHARGING_CONTROL);
                break;
            case OTG_PLUG_IN_REVERSE_CHARGE_MODE:
                setPowerReverseChargeEnable(false, OTG_CHARGING_ENABLE);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //M: modify by cenxingcan@wind-mobi.com 2017/01/12 strart
                        if(otgState) {
                            setPowerReverseChargeEnable(true, OTG_REVERSE_CHARGING_CONTROL);
                            setPowerReverseChargeEnable(true, OTG_CHARGING_ENABLE);
                        }
                        //M: modify by cenxingcan@wind-mobi.com 2017/01/12 end
                    }
                }, 200);
                break;
        }
    }

    private void setPowerReverseChargeEnable(boolean enable, String path) {
        Log.d(TAG,"setPowerReverseChargeEnable -> enable : " + enable + " , path = " + path);
        BufferedWriter mBufferedWriter = null;
        final String value = (enable ? ("1") : ("0"));
        try {
            mBufferedWriter = new BufferedWriter(new FileWriter(path));
            mBufferedWriter.write(value);
            mBufferedWriter.flush();  //M: modify by cenxingcan@wind-mobi.com 2017/01/12
            mBufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG,"setPowerReverseChargeEnable throw exception : " + e);
        } finally {
            mBufferedWriter = null;
        }
    }

    private boolean getOtgEnabled() {
        FileInputStream mFileInputStream = null;
        try {
            mFileInputStream = new FileInputStream(OTG_CURRENT_STATE);
            final int state = mFileInputStream.read();
            Log.d(TAG,"getOtgEnabled state = " + state);
            mFileInputStream.close();
            return state != '0';
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG,"getOtgEnabled throw exception : " + e);
        } finally {
            mFileInputStream = null;
        }
        return false;
    }

    private static int getCurrBatteryPercent() {
        File localFile = new File(OTG_BATTERY_PERCENT);
        boolean bresult = false;
        int percent = 0;
        FileReader localFileReader = null;
        try {
            localFileReader = new FileReader(localFile);
            char[] arrayOfChar = new char[30];
            String[] arrayOfString = new String(arrayOfChar, 0, localFileReader.read(arrayOfChar)).trim().split("\n");
            percent = Integer.parseInt(arrayOfString[0]);
            Log.d(TAG,"getCurrBatteryPercent percent = " + percent);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "FileNotFoundException err!", e);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "IOException err!", e);
        } finally {
            try {
                if (localFileReader != null) {
                    localFileReader.close();
                    localFileReader = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return percent;
    }

    private BroadcastReceiver mBatteryForOtgCtrlBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final boolean chargeEnable = (Settings.System.getInt(context.getContentResolver(), ASUS_OTG_REVERSE_CHARGE_ENABLE, 1) == 1);
            Log.d(TAG, "mBatteryForOtgCtrlBroadcastReceiver , intent.getAction() = " + action + " , chargeEnable = " + chargeEnable);
            if (getOtgEnabled() && (!getPopOtgDialogEnabled())) {
                setPopOtgDialogValue(true);
            }
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                BATTERY_CURRENT_LEVEL = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, BATTERY_LEVEL_FULL);
                if (BATTERY_CURRENT_LEVEL <= BATTERY_LEVEL_20 && getPopOtgDialogEnabled()) {
                    setOtgSelectedMode(OTG_PLUG_IN_NORMOL_MODE);
                    setPowerReverseChargeEnable(false,OTG_REVERSE_CHARGING_CONTROL);
                }
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                if(getPopOtgDialogEnabled() && (BATTERY_CURRENT_LEVEL > BATTERY_LEVEL_20)) {
                    setPowerReverseChargeEnable(true,OTG_CHARGING_ENABLE);
                }
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                //+++cenxingcan@wind-mobi.com modifi fix bug #144122 20161201+++
                if (getPopOtgDialogEnabled() && (!chargeEnable) && (getOtgCurrSelctMode() == OTG_PLUG_IN_REVERSE_CHARGE_MODE)) {
                    setPowerReverseChargeEnable(false,OTG_CHARGING_ENABLE);
                }
            }
        }
    };
    //++gaohui@wind-mobi.com add for usb_ntc 20170323 begin
    private BroadcastReceiver mBatteryForUsbNtclBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int batteryUsbNtc = 0;
            if(action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                batteryUsbNtc = intent.getIntExtra("usb_ntc",0);
                Log.d(TAG,"get batteryUsbNtc: " +batteryUsbNtc);
                boolean enabledUsbNtc = Settings.Global.getInt(mContext.getContentResolver(), ENABLED_USB_NTC_NOTIFICATION, 1) == 1;
                //chenyangqing@wind-mobi.com modify for usb_ntc 20170930 begin
                if((batteryUsbNtc > 70) && enabledUsbNtc) {
                    Settings.Global.putInt(mContext.getContentResolver(), "usb_ntc_emode_notification", 1);
                    //chenyangqing@wind-mobi.com modify for usb_ntc 20170930 end
                    String title = mContext.getString(com.android.internal.R.string.usb_ntc_notification_title);
                    String message = mContext.getString(com.android.internal.R.string.usb_ntc_notification_content);
                    Intent intentBattery = Intent.makeRestartActivityTask(
                            new ComponentName("com.android.settings",
                                    "com.android.settings.deviceinfo.BatteryUsbNtcInfoActivity"));
                    PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0,
                            intentBattery, 0, null, UserHandle.CURRENT);

                    Notification notification = new Notification.Builder(mContext)
                            .setSmallIcon(com.android.internal.R.drawable.stat_sys_battery)
                            .setWhen(0)
                            .setTicker(title)
                            .setDefaults(Notification.DEFAULT_ALL)
                            .setPriority(Notification.PRIORITY_MIN)
                            .setColor(mContext.getColor(
                                    com.android.internal.R.color.system_notification_accent_color))
                            .setContentTitle(title)
                            .setContentText(message)
                            .setContentIntent(pi)
                            .setVisibility(Notification.VISIBILITY_PUBLIC)
                            .build();
                    notification.flags |= Notification.FLAG_NO_CLEAR;
                    Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.ENABLED_USB_NTC_NOTIFICATION, 0);
                    mNotificationManager.notifyAsUser(null, USB_NTC_NOTICATION_ID, notification,
                            UserHandle.ALL);
                }
            }
        }
    };
    //--gaohui@wind-mobi.com add for usb_ntc 20170323 end

    private boolean getPopOtgDialogEnabled() {
        return canPopOtgDialog;
    }

    private void setPopOtgDialogValue(boolean val) {
        canPopOtgDialog = val;
    }

    private static Intent getOtgSelecModeIntent() {
        if (mOtgSelectIntent == null) {
            mOtgSelectIntent = new Intent();
            mOtgSelectIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //+++cenxingcan@wind-mobi.com add begin +++
            mOtgSelectIntent.putExtra("fromwhere", "PhoneWindowManager");
            //cenxingcan@wind-mobi.com add end
            mOtgSelectIntent.setClassName("com.android.settings","com.mediatek.otg.OtgModeChooserActivity");
        }
        return mOtgSelectIntent;
    }

    private void startOtgModeChooserActivity(Context context) {
        if (getOtgSelecModeIntent() != null) {
            try {
                context.startActivity(getOtgSelecModeIntent());
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "startOtgModeChooserActivity , exception : " + e);
            }
        }
    }

    private int getOtgCurrSelctMode() {
        return Settings.System.getInt(mContext.getContentResolver(), WIND_DEF_DATA_OTG_SELECT_MODE, 0);
    }

    private void setOtgSelectedMode(int mode) {
        Settings.System.putInt(mContext.getContentResolver(), WIND_DEF_DATA_OTG_SELECT_MODE, mode);
    }

    private void updateOtgCtrlSettings() {
        final int mode = getOtgCurrSelctMode();
        Log.d(TAG, "updateOtgCtrlSettings , mode = " + ((mode == OTG_PLUG_IN_NORMOL_MODE) ? ("otg mode") : ("otg reverse charge mode")));
        chooseOtgMode(mode);
    }

    private void sendDismissOtgDialogBroadcast(Context context) {
        context.sendBroadcast(new Intent(ACTION_CLOSE_OTG_PLUG_IN_DIALOG));
    }
    /// M: cenxingcan@wind-mobi.com add for otg reverse 20161119 end

    //BEGIN: Jeffrey_Chiang@asus.com
    private Runnable mScreenUnpinningRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                IActivityManager activityManager = ActivityManagerNative.getDefault();
                if (activityManager.isInLockTaskMode()) {
                    Log.i(TAG,"mScreenUnpinningRunnable : stopLockTaskMode");
                    activityManager.stopLockTaskMode();
                    mIsScreenUnpinning = true;
                    performHapticFeedbackLw(null, HapticFeedbackConstants.VIRTUAL_KEY, false);
                }
            }
            catch (RemoteException e) {
                Log.d(TAG, "Unable to reach activity manager", e);
            }
     }};

    private void cancelPendingScreenUnpinningAction() {
        mHandler.removeCallbacks(mScreenUnpinningRunnable);
    }

    private boolean isAllowLaunchCamera() {
        try {
            String pkg = getCurrentFocusPackageName();
            Log.i(TAG,"Current focus app is : "+pkg);
            // When camera is activity
            if ("com.asus.camera".equals(pkg)) {
                //swipe keyguard and lock state is disable
                if (!isKeyguardSecure(mCurrentUserId) && !isKeyguardLocked()) {
                    return false;
                }

                //secure keyguard and lock state is disable
                if (isKeyguardSecure(mCurrentUserId) && !isKeyguardLocked()) {
                    return false;
                }

                //secure keyguard but lock state is true and keyguard screen is show
                if (isKeyguardSecure(mCurrentUserId) && !mIsKeyguardShow) {
                    return false;
                }
            }

            // When phone is activity
            if ("com.asus.asusincallui".equals(pkg)) {
                return false;
            }

            // When zenflash is activity
            if ("com.eostek.asuszenflash".equals(pkg)) {
                return false;
            }
        } catch (Exception e) {
            Log.i(TAG,"Exception : "+e.getMessage());
        }
        return true;
    }

    private boolean isAllowedHandleKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        boolean result = false;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_FINGERPRINT_TAP:
            case KeyEvent.KEYCODE_FINGERPRINT_DTAP:
            case KeyEvent.KEYCODE_FINGERPRINT_LONGPRESS: {
                String focusCls = getCurrentFocusClassName();
                if (focusCls != null &&
                        (focusCls.endsWith("FingerprintEnrollEnrolling") ||
                                focusCls.endsWith("FingerprintEnrollFinish"))) {
                    result = false;
                } else {
                    result = true;
                }
            } break;

            case KeyEvent.KEYCODE_FINGERPRINT_SWIPE_UP:
            case KeyEvent.KEYCODE_FINGERPRINT_SWIPE_DOWN:
            case KeyEvent.KEYCODE_FINGERPRINT_SWIPE_LEFT:
            case KeyEvent.KEYCODE_FINGERPRINT_SWIPE_RIGHT: {
                result = true;
            } break;
        }
        return result;
    }

    private String getCurrentFocusPackageName() {
        return mSystemMonitorInternal.getFocusAppPackageName();
    }

    private String getCurrentFocusClassName() {
        return mSystemMonitorInternal.getFocusAppClassName();
    }

    private boolean isCameraActive() {
        if ("com.asus.camera".equals(getCurrentFocusPackageName())) {
            return true;
        }
        return false;
    }

    private void notifyCameraRecentKey(String status) {
        mHandler.removeCallbacks(mNotifyCameraRunnable);
        if ("com.asus.camera".equals(getCurrentFocusPackageName())) {
            if ("down".equals(status)) {
                mNotifyCameraRunnable.set("down");
            } else {
                mNotifyCameraRunnable.set("up");
            }
            mHandler.post(mNotifyCameraRunnable);
        }
    }

     private final Runnable mRecentLongPressRunnable = new Runnable() {
         @Override
         public void run() {
             switch (mFuncWhenLongPressAppSwitch){
                 case Settings.System.LONG_PRESSED_FUNC_SCREENSHOT:
                     mSwitchKeyHandled = true;
                     if(mDisplay != null && mDisplay.getState() != Display.STATE_OFF) {
                         Log.i(TAG,"Recent key trigger takeScreenshot");
                         if (Build.FEATURES.ENABLE_LONG_SCREENSHOT) {
                             takeLongScreenshot(TAKE_SCREENSHOT_FULLSCREEN);
                         } else {
                             takeScreenshot(TAKE_SCREENSHOT_FULLSCREEN);
                         }
                     }
                     break;
                 case Settings.System.LONG_PRESSED_FUNC_MULTIWINDOW:
                     mSwitchKeyHandled = true;
                     StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
                     if (statusbar != null) {
                         statusbar.toggleSplitScreen();
                     }
                     break;
                 case Settings.System.LONG_PRESSED_FUNC_MENU:
                     mSwitchKeyHandled = true;
                     sendKeyEvent(KeyEvent.KEYCODE_MENU);
                     break;
                 case Settings.System.LONG_PRESSED_FUNC_STITCHIMAGE:
                     mSwitchKeyHandled = true;
                     if(mDisplay != null && mDisplay.getState() != Display.STATE_OFF && !mSafeMode) {
                         try {
                             Log.i(TAG, "Recent key trigger stitchimage");
                             Intent intent = new Intent();
                             intent.setComponent(new ComponentName(STITCHIMAGE_APP_PACKAGE_NAME, ACTION_START_STITCHIMAGE));
                             intent.putExtra(EXTRA_KEY_STITCHIMAGE_SETTINGS_CALLFROM, EXTRA_VALUE_STITCHIMAGE_SETTINGS_CALLFROM_ASUSSETTINGS);
                             mContext.startService(intent);
                         } catch (Exception e) {
                             if (Build.FEATURES.ENABLE_LONG_SCREENSHOT) {
                                 takeLongScreenshot(TAKE_SCREENSHOT_FULLSCREEN);
                             } else {
                                 takeScreenshot(TAKE_SCREENSHOT_FULLSCREEN);
                             }
                             Log.i(TAG,"trigger stitchimage failed, Exception :"+e);
                         }
                     } else {
                         if (Build.FEATURES.ENABLE_LONG_SCREENSHOT) {
                             takeLongScreenshot(TAKE_SCREENSHOT_FULLSCREEN);
                         } else {
                             takeScreenshot(TAKE_SCREENSHOT_FULLSCREEN);
                         }
                     }
                     break;
                 default:
                     Log.w(TAG,"Undefined Recent Long Press Func: " + mFuncWhenLongPressAppSwitch);
                     break;
             }
         }
     };

    private boolean sendKeyEvent(int event) {
        long now = SystemClock.uptimeMillis();
        try {
            int policyFlags = 0;
            switch (event) {
                case KeyEvent.KEYCODE_HOME:
                    policyFlags |= KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
                    break;
                default:
                    break;
            }

            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, event, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 , policyFlags);
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, event, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 , policyFlags);
            boolean downResult = InputManager.getInstance().injectInputEvent(down, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            boolean upResult = InputManager.getInstance().injectInputEvent(up, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            return downResult && upResult;
        } catch (Exception e) {
        }
        return false;
    }
// +++ yuchen_chang@asus.com 	
	private void sendGestureBroadcast(int keycode){	
		Log.d(TAG, "sendGestureBroadcast " + keycode);	
		Intent it = new Intent();	
		it.setAction("com.asus.launch_app_by_gesture");	
		it.putExtra("keyCode", Integer.toString(keycode));	
		mContext.sendBroadcast(it);	
	}	
// --- yuchen_chang@asus.com 

     class CombineKeyListener implements CombineKeyDetector.OnCombineKeyListener {
         public boolean onHandleEnterWithVolumeUp(boolean interactive) {
             if ((mIsInstantCameraEnabled && !interactive && mLidState != LID_CLOSED)
                     || (mIsLongPressInstantCameraEnabled && interactive && isAllowLaunchCamera())) {
                 Message msg = mHandler.obtainMessage();
                 msg.what = MSG_ZENNY_EVENT;
                 msg.arg1 = new Integer((interactive) ? 1 : 0);
                 mHandler.removeMessages(MSG_ZENNY_EVENT);
                 mHandler.sendMessage(msg);
                 return true;
             }
             return false;
         }

         public boolean onHandleBackWithVolumeDown(boolean interactive) {
                Message msg = mHandler.obtainMessage();
                msg.what = MSG_LOGUPLOADER_EVENT;
                msg.arg1 = new Integer((interactive) ? 1 : 0);
                mHandler.removeMessages(MSG_LOGUPLOADER_EVENT);
                mHandler.sendMessage(msg);
                return true;
         }

         public boolean onHandleTripleTapPowerKey() {
             Log.d(TAG, "triple tap power key");
             try {
                 PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo("com.asus.emergencyhelp",0);
                 mContext.sendBroadcast(new Intent("com.asus.emergencyhelp.action.SOS_HELP"));
                 return true;
             } catch (Exception e) {
             }
             if (mEmergencyAffordanceManager.needsEmergencyAffordance()) {
                 mEmergencyCallWakeLock.acquire();
                 mEmergencyAffordanceManager.performEmergencyCall();
                 mEmergencyCallWakeLock.release();
                 return true;
             }
             return false;
         }

         /*public boolean onHandleBackWithSwitch(boolean interactive) {
             try {
                 IActivityManager activityManager = ActivityManagerNative.getDefault();
                 if (activityManager.isInLockTaskMode()) {
                     Log.i(TAG, "combinekeydetector trigger upinning event");
                     Message msg = mHandler.obtainMessage();
                     msg.what = MSG_SCREEN_UNPINNING_EVENT;
                     msg.arg1 = new Integer((interactive) ? 1 : 0);
                     mHandler.removeMessages(MSG_SCREEN_UNPINNING_EVENT);
                     mHandler.sendMessage(msg);
                     return true;
                 }
            } catch (RemoteException e) {
                 Log.i(TAG,"Remote exception : "+e.getMessage());
            }
            return false;
         }*/
         
         @Override
         public boolean onHandleDoubleTapHomeKey(boolean interactive) {
             Log.d(TAG,"double tap home key");
             Message msg = mHandler.obtainMessage();
             msg.what = MSG_DOUBLE_TAP_ON_HOME_TARGET_APP;
             msg.arg1 = new Integer((interactive) ? 1 : 0);
             mHandler.removeMessages(MSG_DOUBLE_TAP_ON_HOME_TARGET_APP);
             mHandler.sendMessage(msg);
             return true;
         }
     }
    //END: Jeffrey_Chiang@asus.com

//BEGIN : roy_huang@asus.com
    final Object mScreenshotWithBroadcastLock = new Object();
    ServiceConnection mScreenshotWithBroadcastConnection = null;

    final Runnable mScreenshotWithBroadcastTimeout = new Runnable() {
        @Override public void run() {
            synchronized (mScreenshotWithBroadcastLock) {
                if (mScreenshotWithBroadcastConnection != null) {
                    mContext.unbindService(mScreenshotWithBroadcastConnection);
                    mScreenshotWithBroadcastConnection = null;
                    notifyScreenshotError();
                }
            }
        }
    };

    private void takeScreenshot(final int screenshotType, boolean sendBroadcast) {
        synchronized (mScreenshotWithBroadcastLock) {
            if (mScreenshotWithBroadcastConnection != null) {
                return;
            }
            final boolean isSendBroadcast = sendBroadcast;
            final ComponentName serviceComponent = new ComponentName(SYSUI_PACKAGE,
                    SYSUI_SCREENSHOT_SERVICE);
            final Intent serviceIntent = new Intent();
            serviceIntent.setComponent(serviceComponent);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotWithBroadcastLock) {
                        if (mScreenshotWithBroadcastConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, screenshotType);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotWithBroadcastLock) {
                                    if (mScreenshotWithBroadcastConnection == myConn) {
                                        mContext.unbindService(mScreenshotWithBroadcastConnection);
                                        mScreenshotWithBroadcastConnection = null;
                                        mHandler.removeCallbacks(mScreenshotWithBroadcastTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
                        if (mStatusBar != null && mStatusBar.isVisibleLw())
                            msg.arg1 = 1;
                        if (mNavigationBar != null && mNavigationBar.isVisibleLw())
                            msg.arg2 = 1;

                        Bundle data = new Bundle();
                        data.putBoolean("send-broadcast", isSendBroadcast);
                        msg.setData(data);

                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    notifyScreenshotError();
                }
            };
            if (mContext.bindServiceAsUser(
                    serviceIntent, conn, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)) {
                mScreenshotWithBroadcastConnection = conn;
                mHandler.postDelayed(mScreenshotWithBroadcastTimeout, 10000);
            }
        }
    }

    BroadcastReceiver mScreenShotReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("ACTION_APP_TAKE_SCREENSHOT".equals(intent.getAction())) {
                mTakeScreenshotWithBroadcastRunnable.setScreenshotType(TAKE_SCREENSHOT_FULLSCREEN);
                mHandler.post(mTakeScreenshotWithBroadcastRunnable);
            }
        }
    };

    private class ScreenshotWithBroadcastRunnable implements Runnable {
        private int mScreenshotType = TAKE_SCREENSHOT_FULLSCREEN;

        public void setScreenshotType(int screenshotType) {
            mScreenshotType = screenshotType;
        }

        @Override
        public void run() {
            if (Build.FEATURES.ENABLE_LONG_SCREENSHOT) {
                takeLongScreenshot(mScreenshotType, true);
            } else {
                takeScreenshot(mScreenshotType, true);
            }
        }
    }

    private final ScreenshotWithBroadcastRunnable mTakeScreenshotWithBroadcastRunnable = new ScreenshotWithBroadcastRunnable();
//END : roy_huang@asus.com

    // BEGIN leo_liao@asus.com, One-hand control
    @Override
    public boolean canOneHandCtrlWindow(int windowType) {
        switch (windowType) {
            case WindowManager.LayoutParams.TYPE_MAGNIFICATION_OVERLAY: {
                return false;
            }
        }
        return true;
    }

    private final Runnable mActivateOneHandCtrlRunnable = new Runnable() {
        @Override
        public void run() {
            final int enabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ONEHAND_CTRL_ENABLED, 0, UserHandle.USER_CURRENT);
            Settings.Secure.putIntForUser(mContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ONEHAND_CTRL_ENABLED,
                    (enabled != 0) ? 0 : 1, UserHandle.USER_CURRENT);
        }
    };

    private void readOneHandCtrlConfigurationDependentBehaviors() {
        mOneHandCtrlFeatureEnabled = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_ASUS_WHOLE_SYSTEM_ONEHAND);
        if (mOneHandCtrlFeatureEnabled) {
            mOneHandCtrlQuickTriggerByDefault = mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_oneHandCtrlQuickTriggerByDefault);
            final int enabled = Settings.Secure.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ONEHAND_CTRL_QUICK_TRIGGER_ENABLED,
                    mOneHandCtrlQuickTriggerByDefault, UserHandle.USER_CURRENT);
            if (enabled > 0) {
                mDoubleTapOnHomeBehavior = DOUBLE_TAP_HOME_ONEHAND_CTRL;
            }
        }
    }

    private void updateOneHandCtrlSettings() {
        if (mOneHandCtrlFeatureEnabled) {
            final int enabled = Settings.Secure.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ONEHAND_CTRL_QUICK_TRIGGER_ENABLED,
                    mOneHandCtrlQuickTriggerByDefault, UserHandle.USER_CURRENT);
            if (enabled > 0) {
                mDoubleTapOnHomeBehavior = DOUBLE_TAP_HOME_ONEHAND_CTRL;
            } else {
                // Fallback to the default system default of double-taps on home
                mDoubleTapOnHomeBehavior = mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_doubleTapOnHomeBehavior);
                if (mDoubleTapOnHomeBehavior < DOUBLE_TAP_HOME_NOTHING ||
                        mDoubleTapOnHomeBehavior > DOUBLE_TAP_HOME_RECENT_SYSTEM_UI) {
                    mDoubleTapOnHomeBehavior = DOUBLE_TAP_HOME_NOTHING;
                }
            }
        }
    }
    // END leo_liao@asus.com

    // BEGIN: oliver_hu@asus.com
    public boolean isAlwaysOnWindow(WindowState win) {
        final WindowManager.LayoutParams attrs = (win == null) ? null : win.getAttrs();
        if (win == null || attrs == null) {
            return false;
        }
        int fl = PolicyControl.getWindowFlags(win, attrs);
        final int flag = FLAG_FULLSCREEN | FLAG_SHOW_WHEN_LOCKED | FLAG_LAYOUT_IN_SCREEN
                | FLAG_HARDWARE_ACCELERATED | FLAG_TRANSLUCENT_NAVIGATION | FLAG_TRANSLUCENT_STATUS;
        final boolean isAlwaysOnWindow = (attrs.type == TYPE_SYSTEM_ERROR)
                && ((fl & flag) == flag)
                && (win.getOwningUid() == Process.SYSTEM_UID);
            return isAlwaysOnWindow;
    }
    // END: oliver_hu@asus.com
// Begin: hungjie_tseng@asus.com,Adding for dimiss colorfade when alwayson window add/remove
    public void notifyDismissColorFade(WindowState win, int action) {
        if(mAlwaysOnController != null) {
            //begin:adding for add/remove alwayson
             final int ADD_ALWAYSON_WINDOW = 1;
            final int REVMOE_ALWAYSON_WINDOW = 2;
            //end:adding for add/remove alwayson
            switch (action) {
                case ADD_ALWAYSON_WINDOW: {
                    Slog.d(TAG, "TYPE_SYSTEM_ERROR success notifyDismissColorfade, arg1: " + TURNING_SCREEN_OFF);
                    mAlwaysOnController.notifyDismissColorfade(TURNING_SCREEN_OFF);
                } break;
                case REVMOE_ALWAYSON_WINDOW: {
                    Slog.d(TAG, "removeWindowLw=" + win + " success notifyDismissColorfade, arg1: " + TURNING_SCREEN_ON);
                    mAlwaysOnController.notifyDismissColorfade(TURNING_SCREEN_ON);
                    //begin:fix statusbar hide when opening screen by fingerprint
                    if((mLastSystemUiFlags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
                        mLastSystemUiFlags = mLastSystemUiFlags & ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                    }
                    //end:fix statusbar hide when opening screen by fingerprint
                } break;
            }
        }
    }
// End: hungjie_tseng@asus.com,Adding for dimiss colorfade when alwayson window add/remove

	//add mohongwu@wind-mobi.com 2016/11/23 start
    public int interceptMotionWhenProximityEnabled(long nanos,int policyFlags) {
        Log.d(TAG,"mShouldTurnOffTouch="+mShouldTurnOffTouch);
        if(mShouldTurnOffTouch) {
            return 0;
        }else{
            return ACTION_PASS_TO_USER;
        }
    }
    //add mohongwu@wind-mobi.com 2016/11/23 end

    //dongjiangpeng@wind-mobi.com add 2016/12/21 start
    private final SensorEventListener mDistanceSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values != null) {
                float[] its = event.values;
                float distance = event.sensor.getMaximumRange();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    public int getDataReadFiveNodeValue() {
        BufferedReader br;
        FileReader fr = null;
        String value = "0";
        try {
            fr = new FileReader("/proc/psensor/data_read_5cm");
            br = new BufferedReader(fr);
            while ((value = br.readLine()) != null) {
                value = value.trim();
                if(value != null) {
                    Log.i(TAG, "getDataReadFiveNodeValue data_read_5cm:" + Integer.parseInt(value));
                    return Integer.parseInt(value);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        Log.i(TAG, "getDataReadFiveNodeValue return default 100");
        return 100;
    }

    private int getPsNodeValue(){
        File mProximsenor = new File("/sys/bus/platform/drivers/als_ps/psdata");
        int data = 0;
        FileInputStream fis = null;
        String st = "0";
        if (mProximsenor.exists()) {
            try {
                fis = new FileInputStream(mProximsenor);
                int len = fis.available();
                byte[] buf = new byte[len];
                fis.read(buf);
                st = new String(buf);
                Log.d(TAG, "getPsNodeValue: st " + st);
                st = st.trim();
                for(int i=0;i<st.length();i++) {
                    if(st.charAt(i) != '0') {
                        st = st.substring(i);
                        Slog.i(TAG, "getPsNodeValue st: " + st);
                        break;
                    }
                }
                data = Integer.valueOf(st,16);
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Slog.i(TAG, "getPsNodeValue ps:" + data);
        }
        return data;
    }
    //dongjiangpeng@wind-mobi.com add 2016/12/21 end
    /*wangchaobin@wind-mobi.com added  2017.01.10 for new feature begin*/
    private void listenForCallHangup() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ASUS_HUNGUP_BROADCAST);
        mSensorManager.registerListener(mDisplayIncallControlListener, mProximityMotion, SensorManager.SENSOR_DELAY_FASTEST);
        isListenerRegisted = true;
        mContext.registerReceiver(phoneHungupReceiver, intentFilter);
    }
    private final SensorEventListener mDisplayIncallControlListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values != null) {
                final float distance = event.values[0];
                boolean positive = distance >= 0.0f && distance < mProximityThreshold;
                isProximmitySensorPositive = positive;
                TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
                PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                if (telecomManager == null) {
                    Slog.d(TAG, "telecomManager is null...");
                }
                if (telecomManager != null && telecomManager.isInCall() && !positive) {
                    if (!isAsusPowerDown) {
                        powerManager.wakeUp(SystemClock.uptimeMillis());
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };
    /*wangchaobin@wind-mobi.com added  2017.01.10 for new feature end*/

    //BEGIN : roy_huang@asus.com, Long screenshot feature in CN sku
    final Object mLongScreenshotLock = new Object();
    ServiceConnection mLongScreenshotConnection = null;

    final Runnable mLongScreenshotTimeout = new Runnable() {
        @Override public void run() {
            synchronized (mLongScreenshotLock) {
                if (mLongScreenshotConnection != null) {
                    mContext.unbindService(mLongScreenshotConnection);
                    mLongScreenshotConnection = null;
                    notifyLongScreenshotError();
                }
            }
        }
    };

    private void takeLongScreenshot(final int screenshotType) {
        synchronized (mLongScreenshotLock) {
            if (mLongScreenshotConnection != null) {
                return;
            }
            final ComponentName serviceComponent = new ComponentName(CNSMARTSCREENSHOT_PACKAGE,
                    CNSMARTSCREENSHOT_SCREENSHOT_SERVICE);
            final Intent serviceIntent = new Intent();
            serviceIntent.setComponent(serviceComponent);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.i(TAG, "gauss-onServiceConnected");
                    synchronized (mLongScreenshotLock) {
                        if (mLongScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, screenshotType);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mLongScreenshotLock) {
                                    if (mLongScreenshotConnection == myConn) {
                                        Log.i(TAG, "gauss-unbindService");
                                        mContext.unbindService(mLongScreenshotConnection);
                                        mLongScreenshotConnection = null;
                                        mHandler.removeCallbacks(mLongScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
                        if (mStatusBar != null && mStatusBar.isVisibleLw())
                            msg.arg1 = 1;
                        if (mNavigationBar != null && mNavigationBar.isVisibleLw())
                            msg.arg2 = 1;
                        try {
                            Log.i(TAG, "gauss-messenger.send(msg)");
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    notifyLongScreenshotError();
                }
            };
            if (mContext.bindServiceAsUser(serviceIntent, conn,
                    Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE_WHILE_AWAKE,
                    UserHandle.CURRENT)) {
                Log.i(TAG, "gauss-bindServiceAsUser");
                mLongScreenshotConnection = conn;
                mHandler.postDelayed(mLongScreenshotTimeout, 120000);
            }
        }
    }

    /**
     * Notifies the screenshot service to show an error.
     */
    private void notifyLongScreenshotError() {
        // If the service process is killed, then ask it to clean up after itself
        final ComponentName errorComponent = new ComponentName(CNSMARTSCREENSHOT_PACKAGE,
                CNSMARTSCREENSHOT_SCREENSHOT_ERROR_RECEIVER);
        Intent errorIntent = new Intent(Intent.ACTION_USER_PRESENT);
        errorIntent.setComponent(errorComponent);
        errorIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT |
                Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcastAsUser(errorIntent, UserHandle.CURRENT);
    }

    final Object mLongScreenshotWithBroadcastLock = new Object();
    ServiceConnection mLongScreenshotWithBroadcastConnection = null;

    final Runnable mLongScreenshotWithBroadcastTimeout = new Runnable() {
        @Override public void run() {
            synchronized (mLongScreenshotWithBroadcastLock) {
                if (mLongScreenshotWithBroadcastConnection != null) {
                    mContext.unbindService(mLongScreenshotWithBroadcastConnection);
                    mLongScreenshotWithBroadcastConnection = null;
                    notifyLongScreenshotError();
                }
            }
        }
    };

    private void takeLongScreenshot(final int screenshotType, boolean sendBroadcast) {
        synchronized (mLongScreenshotWithBroadcastLock) {
            if (mLongScreenshotWithBroadcastConnection != null) {
                return;
            }
            final boolean isSendBroadcast = sendBroadcast;
            final ComponentName serviceComponent = new ComponentName(CNSMARTSCREENSHOT_PACKAGE,
                    CNSMARTSCREENSHOT_SCREENSHOT_SERVICE);
            final Intent serviceIntent = new Intent();
            serviceIntent.setComponent(serviceComponent);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mLongScreenshotWithBroadcastLock) {
                        if (mLongScreenshotWithBroadcastConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, screenshotType);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mLongScreenshotWithBroadcastLock) {
                                    if (mLongScreenshotWithBroadcastConnection == myConn) {
                                        mContext.unbindService(mLongScreenshotWithBroadcastConnection);
                                        mLongScreenshotWithBroadcastConnection = null;
                                        mHandler.removeCallbacks(mLongScreenshotWithBroadcastTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
                        if (mStatusBar != null && mStatusBar.isVisibleLw())
                            msg.arg1 = 1;
                        if (mNavigationBar != null && mNavigationBar.isVisibleLw())
                            msg.arg2 = 1;

                        Bundle data = new Bundle();
                        data.putBoolean("send-broadcast", isSendBroadcast);
                        msg.setData(data);
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    notifyLongScreenshotError();
                }
            };
            if (mContext.bindServiceAsUser(
                    serviceIntent, conn, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)) {
                mLongScreenshotWithBroadcastConnection = conn;
                mHandler.postDelayed(mLongScreenshotWithBroadcastTimeout, 120000);
            }
        }
    }
    //END : roy_huang@asus.com

    //BEGIN: Chilin_Wang@asus.com, For game genie lock mode ,blocking home/recent key
    BroadcastReceiver mGameGenieLockModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.asus.gamewidget.app.SET_LOCK_MODE_LOCK".equals(intent.getAction())) {
                sendKeyEvent(KeyEvent.KEYCODE_GAMEGENIE_LOCK);
                Log.d(TAG, "GameGenieLockModeReceiver, SET_LOCK_MODE_LOCK");
                mHandler.removeCallbacks(mNotifyGameGenieLockModeRunnable);
                mNotifyGameGenieLockModeRunnable.setNotifyMessage(GAMEGENIE_LOCK_MODE_ENABLE);
                mHandler.post(mNotifyGameGenieLockModeRunnable);

            } else if ("com.asus.gamewidget.app.SET_LOCK_MODE_UNLOCK".equals(intent.getAction())
                    || "com.asus.gamewidget.app.STOP".equals(intent.getAction())) {
                sendKeyEvent(KeyEvent.KEYCODE_GAMEGENIE_UNLOCK);
                Log.d(TAG, "GameGenieLockModeReceiver, SET_LOCK_MODE_UNLOCK");
                if ("com.asus.gamewidget.app.SET_LOCK_MODE_UNLOCK".equals(intent.getAction())) {
                    mHandler.removeCallbacks(mNotifyGameGenieLockModeRunnable);
                    mNotifyGameGenieLockModeRunnable.setNotifyMessage(GAMEGENIE_UNLOCK);
                    mHandler.post(mNotifyGameGenieLockModeRunnable);
                }

            }
        }
    };

    private static class NotifyGameGenieLockModeRunnable implements Runnable {
        private Context mContext;
        private Toast mGameGenieToast = null;
        private int mNotifyMessage = -1;

        public NotifyGameGenieLockModeRunnable (Context context) {
            mContext = context;
        }

        public void setNotifyMessage(int notifyMessage) {
            mNotifyMessage = notifyMessage;
        }

        @Override
        public void run() {
            String message = null;
            switch (mNotifyMessage) {
                case GAMEGENIE_KEY_LOCKED:
                    message = mContext.getResources().getString(
                            com.android.internal.R.string.gamegenie_key_locked);
                    break;
                case GAMEGENIE_LOCK_MODE_ENABLE:
                    message = mContext.getResources().getString(
                        com.android.internal.R.string.gamegenie_lock_mode_enable);
                    break;
                case GAMEGENIE_UNLOCK:
                    message = mContext.getResources().getString(
                        com.android.internal.R.string.gamegenie_unlock);
                    break;
                default:
                    break;
            }
            if (mGameGenieToast != null) {
                mGameGenieToast.setText(message);
                mGameGenieToast.setDuration(Toast.LENGTH_SHORT);
            } else {
                mGameGenieToast = Toast.makeText(mContext, message, Toast.LENGTH_SHORT);
            }
            mGameGenieToast.show();
        }
    }
    //END: Chilin_Wang@asus.com

    // BEGIN: archie_huang@asus.com
    // For feature: Colorful Navigation Bar
    public void updateNavigationBarColorLw(WindowState win) {
        if (win == null || !win.isVisibleLw()) {
            return;
        }

        if (win == mNavigationBar || win == mStatusBar) {
            return;
        }

        Rect screenBounds = new Rect(mSystemLeft, mSystemTop, mSystemRight, mSystemBottom);
        final Rect cFrame = win.getFrameLw();
        if (!screenBounds.contains(cFrame)) {
            return;
        }

        Rect navBarFrame = new Rect();
        if (mNavigationBar != null) {
            navBarFrame = mNavigationBar.getFrameLw();
        }

        // Re-examine nav bar color provider
        if (mNavBarColorProvider != null) {
            final Rect pFrame = mNavBarColorProvider.getFrameLw();
            if (!mNavBarColorProvider.isVisibleLw()
                    || !screenBounds.contains(pFrame)
                    || !(pFrame.contains(navBarFrame)
                            || mNavBarColorProvider.getOverscanFrameLw().contains(navBarFrame))) {
                mNavBarColorProvider = null;
            }
        }

        WindowState prevProvider = mNavBarColorProvider;
        int providerLayer = mNavBarColorProvider != null ? mNavBarColorProvider.getSurfaceLayer() : -1;
        int providerColor = mNavBarColorProvider != null ? mNavBarColorProvider.getAttrs().navigationBarColor : 0;
        int challengerLayer = win.getSurfaceLayer();
        int challengerColor = win.getAttrs().navigationBarColor;
        if (providerColor != 0 && (challengerColor == 0 || challengerLayer < providerLayer)) {
            return;
        }

        boolean isOverlap = cFrame.contains(navBarFrame)
                    || win.getOverscanFrameLw().contains(navBarFrame);
        if (isOverlap) {
            mNavBarColorProvider = win;
        }

        if (mNavBarColorProvider == null) {
            if (mTopFullscreenOpaqueWindowState != null) {
                mNavBarColorProvider = mTopFullscreenOpaqueWindowState;
            } else {
                return;
            }
        }

        final int color;
        if (!mNavBarColorProvider.isFitScreen()) {
            color = 0;
        } else {
            color = mNavBarColorProvider.getAttrs().navigationBarColor;
        }

        if ((prevProvider == mNavBarColorProvider) && (mNavigationBarColor == color)) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
                if (statusbar != null) {
                    statusbar.setNavigationBarColor(color);
                }
            }
        });
        mNavigationBarColor = color;
    }
    // END: archie_huang@asus.com

    //BEGIN : roy_huang@asus.com
    private void handleLongPressOnVolumeUp() {
        Message msg = mHandler.obtainMessage();
        msg.what = MSG_INADVERTENT_TOUCH_EVENT;
        mHandler.removeMessages(MSG_INADVERTENT_TOUCH_EVENT);
        mHandler.sendMessage(msg);
    }
    //END : roy_huang@asus.com
    //Begin: HJ@asus.com
    private void notifyHardwareKeyPressed() {
        Message msg = mHandler.obtainMessage();
        msg.what = MSG_INADVERTENT_HARDWAREKEY_PRESSED_EVENT;
        mHandler.removeMessages(MSG_INADVERTENT_HARDWAREKEY_PRESSED_EVENT);
        mHandler.sendMessage(msg);
    }
    //End: HJ@asus.com

    // This is added for keyguard to tell if power key + fingerpirnt unlock
    private void injectSpecialPowerKey(boolean down) {
        long now = SystemClock.uptimeMillis();
        if (down) {
            InputManager.getInstance().injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FINGERPRINT_POWERKEY, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD), InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        } else {
            InputManager.getInstance().injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FINGERPRINT_POWERKEY, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD), InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    }

    /// M: add for fullscreen switch feature @{
    static final Rect mTmpSwitchFrame = new Rect();
    private static final int SWITCH_TARGET_WIDTH = 9;
    private static final int SWITCH_TARGET_HEIGHT = 16;
    private boolean mSupportFullscreenSwitch = false;

    /**
     * @param left , left shit value
     * @param top , top shit value
     * @param right , right shit value
     * @param bottom , bottom shit value
     */
    private void updateRect(int left, int top, int right, int bottom) {
        mStableLeft += left;
        mStableTop += top;
        mStableRight -= right;
        mStableBottom -= bottom;

        mDockLeft += left;
        mDockTop += top;
        mDockRight -= right;
        mDockBottom -= bottom;

        mSystemLeft = mDockLeft;
        mSystemTop = mDockTop;
        mSystemRight = mDockRight;
        mSystemBottom = mDockBottom;

        mStableFullscreenLeft += left;
        mStableFullscreenTop += top;
        mStableFullscreenRight -= right;
        mStableFullscreenBottom -= bottom;

        mContentLeft += left;
        mContentTop += top;
        mContentRight -= right;
        mContentBottom -= bottom;

        mCurLeft += left;
        mCurTop += top;
        mCurRight -= right;
        mCurBottom -= bottom;

        mOverscanScreenLeft += left;
        mOverscanScreenTop += top;
        mOverscanScreenWidth -= (left + right);
        mOverscanScreenHeight -= (top + bottom);

        mUnrestrictedScreenLeft += left;
        mUnrestrictedScreenTop += top;
        mUnrestrictedScreenWidth -= (left + right);
        mUnrestrictedScreenHeight -= (top + bottom);

        mRestrictedScreenLeft += left;
        mRestrictedScreenTop += top;
        mRestrictedScreenWidth -= (left + right);
        mRestrictedScreenHeight -= (top + bottom);

        mRestrictedOverscanScreenLeft += left;
        mRestrictedOverscanScreenTop += top;
        mRestrictedOverscanScreenWidth -= (left + right);
        mRestrictedOverscanScreenHeight -= (top + bottom);
    }

    private void applyFullScreenSwitch(WindowState win) {
        Slog.i(TAG,
                "applyFullScreenSwitch win.isFullscreenOn() = "
                        + win.isFullscreenOn());

        if (!win.isFullscreenOn() && !win.isInMultiWindowMode()) {
            getSwitchFrame(win);
            if (mTmpSwitchFrame.left != 0 || mTmpSwitchFrame.right != 0
                    || mTmpSwitchFrame.top != 0 || mTmpSwitchFrame.bottom != 0) {
                updateRect(mTmpSwitchFrame.left, mTmpSwitchFrame.top,
                        mTmpSwitchFrame.right, mTmpSwitchFrame.bottom);
            }
        }
    }

    /**
     * Compute screen shift value if not at fullscreen mode.
     */
    private void getSwitchFrame(WindowState win) {
        mTmpSwitchFrame.setEmpty();
        int diff = 0;

        if (mOverscanScreenWidth > mOverscanScreenHeight) {
            diff = (mOverscanScreenWidth - (mOverscanScreenHeight / SWITCH_TARGET_WIDTH)
                    * SWITCH_TARGET_HEIGHT) / 2;
            if (diff > 0) {
                mTmpSwitchFrame.left = diff;
                mTmpSwitchFrame.top = 0;
                mTmpSwitchFrame.right = diff;
                mTmpSwitchFrame.bottom = 0;
            }
        } else {
            diff = (mOverscanScreenHeight - (mOverscanScreenWidth / SWITCH_TARGET_WIDTH)
                    * SWITCH_TARGET_HEIGHT) / 2;
            if (diff > 0) {
                mTmpSwitchFrame.left = 0;
                mTmpSwitchFrame.top = diff;
                mTmpSwitchFrame.right = 0;
                mTmpSwitchFrame.bottom = diff;
            }
        }

        Slog.i(TAG, "applyFullScreenSwitch mOverscanScreenWidth = "
                + mOverscanScreenWidth + " mOverscanScreenHeight ="
                + mOverscanScreenHeight + " diff =" + diff
                + " mTmpSwitchFrame =" + mTmpSwitchFrame);
    }

    private void resetFullScreenSwitch(WindowState win, Rect of) {
        if (!mTmpSwitchFrame.isEmpty()) {
            Slog.i(TAG, "resetFullScreenSwitch mTmpSwitchFrame = "
                    + mTmpSwitchFrame);
            updateRect(-mTmpSwitchFrame.left, -mTmpSwitchFrame.top,
                    -mTmpSwitchFrame.right, -mTmpSwitchFrame.bottom);

            mTmpSwitchFrame.setEmpty();
        }
    }
    /// @}

    // BEGIN: archie_huang@asus.com
    // For feature: Navigation visibility control - Hide forever
    private boolean canShowNavigationBar() {
        if (mTopFullscreenOpaqueWindowState == null) {
            return true;
        }
        return (mTopFullscreenOpaqueWindowState.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION_FOREVER) == 0;
    }
    // END: archie_huang@asus.com
}
