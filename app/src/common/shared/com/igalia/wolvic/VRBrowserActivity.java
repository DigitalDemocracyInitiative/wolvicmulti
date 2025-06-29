/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic;

import static com.igalia.wolvic.ui.widgets.UIWidget.REMOVE_WIDGET;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentController;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleOwnerKt;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.preference.PreferenceManager;

import com.igalia.wolvic.audio.AndroidMediaPlayer;
import com.igalia.wolvic.audio.AndroidMediaPlayerSoundPool;
import com.igalia.wolvic.audio.AudioEngine;
import com.igalia.wolvic.browser.Accounts;
import com.igalia.wolvic.browser.Media;
// import com.igalia.wolvic.browser.PermissionDelegate; // REMOVED
import com.igalia.wolvic.browser.SettingsStore; // Kept for now, might be needed for app settings
// import com.igalia.wolvic.browser.api.WRuntime; // REMOVED
// import com.igalia.wolvic.browser.api.WSession; // REMOVED
// import com.igalia.wolvic.browser.engine.EngineProvider; // REMOVED
// import com.igalia.wolvic.browser.engine.Session; // REMOVED
// import com.igalia.wolvic.browser.engine.SessionStore; // REMOVED
import com.igalia.wolvic.crashreporting.CrashReporterService; // Kept for app crashes
import com.igalia.wolvic.crashreporting.GlobalExceptionHandler; // Kept for app crashes
// import com.igalia.wolvic.geolocation.GeolocationWrapper; // REMOVED
import com.igalia.wolvic.input.MotionEventGenerator;
// import com.igalia.wolvic.search.SearchEngineWrapper; // REMOVED
// import com.igalia.wolvic.speech.SpeechRecognizer; // REMOVED - revisit if local voice commands needed
// import com.igalia.wolvic.speech.SpeechServices; // REMOVED - revisit if local voice commands needed
import com.igalia.wolvic.telemetry.OpenTelemetry;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.OffscreenDisplay;
import com.igalia.wolvic.ui.adapters.Language;
import com.igalia.wolvic.ui.widgets.AbstractTabsBar;
import com.igalia.wolvic.ui.widgets.AppServicesProvider;
import com.igalia.wolvic.ui.widgets.HorizontalTabsBar;
import com.igalia.wolvic.ui.widgets.KeyboardWidget;
import com.igalia.wolvic.ui.widgets.NavigationBarWidget;
import com.igalia.wolvic.ui.widgets.OverlayContentWidget;
import com.igalia.wolvic.ui.widgets.RootWidget;
import com.igalia.wolvic.ui.widgets.TrayWidget;
import com.igalia.wolvic.ui.widgets.UISurfaceTextureRenderer;
import com.igalia.wolvic.ui.widgets.UIWidget;
import com.igalia.wolvic.ui.widgets.VerticalTabsBar;
import com.igalia.wolvic.ui.widgets.WebXRInterstitialWidget;
import com.igalia.wolvic.ui.widgets.Widget;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.ui.widgets.WindowWidget;
import com.igalia.wolvic.ui.widgets.Windows;
import com.igalia.wolvic.ui.widgets.dialogs.CrashDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.LegalDocumentDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.PromptDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.SendTabDialogWidget;
import com.igalia.wolvic.ui.widgets.dialogs.WhatsNewWidget;
import com.igalia.wolvic.ui.widgets.menus.VideoProjectionMenuWidget;
import com.igalia.wolvic.utils.BitmapCache;
import com.igalia.wolvic.utils.ConnectivityReceiver;
import com.igalia.wolvic.utils.DeviceType;
import com.igalia.wolvic.utils.LocaleUtils;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import kotlinx.coroutines.CoroutineScope;

public class VRBrowserActivity extends PlatformActivity implements WidgetManagerDelegate,
        ComponentCallbacks2, LifecycleOwner, ViewModelStoreOwner, SharedPreferences.OnSharedPreferenceChangeListener, PlatformActivityPlugin.PlatformActivityPluginListener {

    public static final String CUSTOM_URI_SCHEME = "wolvic";
    public static final String CUSTOM_URI_HOST = "com.igalia.wolvic";
    public static final String EXTRA_INTENT_CMD = "intent_cmd";
    public static final String JSON_OVR_SOCIAL_LAUNCH = "ovr_social_launch";
    public static final String JSON_DEEPLINK_MESSAGE = "deeplink_message";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_OPEN_IN_BACKGROUND = "background";
    public static final String EXTRA_CREATE_NEW_WINDOW = "create_new_window";
    public static final String EXTRA_HIDE_WEBXR_INTERSTITIAL = "hide_webxr_interstitial";
    public static final String EXTRA_HIDE_WHATS_NEW = "hide_whats_new";
    public static final String EXTRA_KIOSK = "kiosk";
    private static final long BATTERY_UPDATE_INTERVAL = 60 * 1_000_000_000L; // 60 seconds

    private boolean mLaunchImmersive = false;
    public static final String EXTRA_LAUNCH_IMMERSIVE = "launch_immersive";
    // Element where a click would be simulated to launch the WebXR experience.
    public static final String EXTRA_LAUNCH_IMMERSIVE_PARENT_XPATH = "launch_immersive_parent_xpath";
    public static final String EXTRA_LAUNCH_IMMERSIVE_ELEMENT_XPATH = "launch_immersive_element_xpath";

    private boolean shouldRestoreHeadLockOnVRVideoExit;

    private BroadcastReceiver mCrashReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if((intent.getAction() != null) && intent.getAction().equals(CrashReporterService.CRASH_ACTION)) {
                Intent crashIntent;
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                    crashIntent = intent.getParcelableExtra(CrashReporterService.DATA_TAG, Intent.class);
                } else {
                    crashIntent = intent.getParcelableExtra(CrashReporterService.DATA_TAG);
                }
                handleContentCrashIntent(crashIntent);
            }
        }
    };

    private LifecycleRegistry mLifeCycle;

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        // Ensure that mLifeCycle is initialized, because this method
        // may be called early by a superclass at construction time.
        return getLifecycleRegistry();
    }

    private LifecycleRegistry getLifecycleRegistry() {
        if (mLifeCycle == null) {
            mLifeCycle = new LifecycleRegistry(this);
        }
        return mLifeCycle;
    }

    public CoroutineScope getCoroutineScope() {
        return LifecycleOwnerKt.getLifecycleScope(this);
    }

    private final ViewModelStore mViewModelStore;

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return mViewModelStore;
    }

    public VRBrowserActivity() {
        getLifecycleRegistry().setCurrentState(Lifecycle.State.INITIALIZED);

        mViewModelStore = new ViewModelStore();
    }

    class SwipeRunnable implements Runnable {
        boolean mCanceled = false;
        @Override
        public void run() {
            if (!mCanceled) {
                mLastGesture = NoGesture;
            }
        }
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    static final int NoGesture = -1;
    static final int GestureSwipeLeft = 0;
    static final int GestureSwipeRight = 1;
    static final int SwipeDelay = 1000; // milliseconds
    static final long RESET_CRASH_COUNT_DELAY = 5000;
    static final int UPDATE_NATIVE_WIDGETS_DELAY = 50; // milliseconds

    static final String LOGTAG = SystemUtils.createLogtag(VRBrowserActivity.class);
    ConcurrentHashMap<Integer, Widget> mWidgets;
    private int mWidgetHandleIndex = 1;
    AudioEngine mAudioEngine;
    OffscreenDisplay mOffscreenDisplay;
    FrameLayout mWidgetContainer;
    int mLastGesture;
    SwipeRunnable mLastRunnable;
    Handler mHandler = new Handler(Looper.getMainLooper());
    Runnable mAudioUpdateRunnable;
    Windows mWindows;
    RootWidget mRootWidget;
    KeyboardWidget mKeyboard; // May be kept for input
    // NavigationBarWidget mNavigationBar; // REMOVED
    // AbstractTabsBar mTabsBar; // REMOVED
    CrashDialogWidget mCrashDialog; // Kept for app crashes
    TrayWidget mTray; // May be repurposed or removed
    // WhatsNewWidget mWhatsNewWidget = null; // REMOVED
    // WebXRInterstitialWidget mWebXRInterstitial; // REMOVED
    // PermissionDelegate mPermissionDelegate; // REMOVED
    LinkedList<UpdateListener> mWidgetUpdateListeners;
    // LinkedList<PermissionListener> mPermissionListeners; // REMOVED
    LinkedList<FocusChangeListener> mFocusChangeListeners;
    LinkedList<WorldClickListener> mWorldClickListeners;
    LinkedList<WebXRListener> mWebXRListeners;
    LinkedList<Runnable> mBackHandlers;
    private final MutableLiveData<Boolean> mIsPresentingImmersive = new MutableLiveData<>(false);
    private Thread mUiThread;
    private LinkedList<Pair<Object, Float>> mBrightnessQueue;
    private Pair<Object, Float> mCurrentBrightness;
    // private SearchEngineWrapper mSearchEngineWrapper; // REMOVED
    private SettingsStore mSettings;
    private SharedPreferences mPrefs;
    // private boolean mConnectionAvailable = true; // REMOVED (ConnectivityReceiver also likely to be removed)
    private Widget mActiveDialog;
    private Set<String> mPoorPerformanceAllowList;
    private float mCurrentCylinderDensity = 0;
    private boolean mHideWebXRIntersitial = false;
    private FragmentController mFragmentController;
    private LinkedHashMap<Integer, WidgetPlacement> mPendingNativeWidgetUpdates = new LinkedHashMap<>();
    private ScheduledThreadPoolExecutor mPendingNativeWidgetUpdatesExecutor = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> mNativeWidgetUpdatesTask = null;
    private Media mPrevActiveMedia = null;
    private boolean mIsPassthroughEnabled = false;
    private boolean mIsHandTrackingEnabled = true;
    private boolean mIsHandTrackingSupported = false;
    private boolean mAreControllersAvailable = false;
    private long mLastBatteryUpdate = System.nanoTime();
    private int mLastBatteryLevel = -1;
    private PlatformActivityPlugin mPlatformPlugin;
    private int mLastMotionEventWidgetHandle;
    private boolean mIsEyeTrackingSupported;
    private String mImmersiveParentElementXPath;
    private String mImmersiveTargetElementXPath;

    private ViewTreeObserver.OnGlobalFocusChangeListener globalFocusListener = new ViewTreeObserver.OnGlobalFocusChangeListener() {
        @Override
        public void onGlobalFocusChanged(View oldFocus, View newFocus) {
            Log.d(LOGTAG, "======> OnGlobalFocusChangeListener: old(" + oldFocus + ") new(" + newFocus + ")");
            // TODO: Which controller should we send the haptic feedback to ?
            triggerHapticFeedback(0);
            for (FocusChangeListener listener: mFocusChangeListeners) {
                listener.onGlobalFocusChanged(oldFocus, newFocus);
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        Context newContext = LocaleUtils.init(base);
        super.attachBaseContext(newContext);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mFragmentController = FragmentController.createController(new FragmentControllerCallbacks(this, new Handler(Looper.getMainLooper()), 0));
        mFragmentController.attachHost(null);
        mFragmentController.dispatchActivityCreated();

        SettingsStore.getInstance(getBaseContext()).setPid(Process.myPid());
        ((VRBrowserApplication)getApplication()).onActivityCreate(this);

        if (!DeviceType.isHVRBuild() && SettingsStore.getInstance(getBaseContext()).isTelemetryEnabled()) {
            TelemetryService.setService(new OpenTelemetry(getApplication()));
        }

        // Fix for infinite restart on startup crashes.
        long count = SettingsStore.getInstance(getBaseContext()).getCrashRestartCount();
        boolean cancelRestart = count > CrashReporterService.MAX_RESTART_COUNT;
        if (cancelRestart) {
            super.onCreate(savedInstanceState);
            Log.e(LOGTAG, "Cancel Restart");
            finish();
            return;
        }
        SettingsStore.getInstance(getBaseContext()).incrementCrashRestartCount();
        mHandler.postDelayed(() -> SettingsStore.getInstance(getBaseContext()).resetCrashRestartCount(), RESET_CRASH_COUNT_DELAY);
        // Set a global exception handler as soon as possible
        GlobalExceptionHandler.register(this.getApplicationContext());

        if (DeviceType.isOculusBuild()) {
            workaroundGeckoSigAction();
        }
        mUiThread = Thread.currentThread();

        BitmapCache.getInstance(this).onCreate();

        // WRuntime runtime = EngineProvider.INSTANCE.getOrCreateRuntime(this); // REMOVED
        // runtime.appendAppNotesToCrashReport("Wolvic " + BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE + "-" + BuildConfig.FLAVOR + "-" + BuildConfig.BUILD_TYPE + " (" + BuildConfig.GIT_HASH + ")"); // REMOVED

        // Create broadcast receiver for getting crash messages from crash process
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CrashReporterService.CRASH_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerReceiver(mCrashReceiver, intentFilter, BuildConfig.APPLICATION_ID + "." + getString(R.string.app_permission_name), null, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mCrashReceiver, intentFilter, BuildConfig.APPLICATION_ID + "." + getString(R.string.app_permission_name), null);
        }

        mLastGesture = NoGesture;
        mWidgetUpdateListeners = new LinkedList<>();
        mPermissionListeners = new LinkedList<>();
        mFocusChangeListeners = new LinkedList<>();
        mWorldClickListeners = new LinkedList<>();
        mWebXRListeners = new LinkedList<>();
        mBackHandlers = new LinkedList<>();
        mBrightnessQueue = new LinkedList<>();
        mCurrentBrightness = Pair.create(null, 1.0f);
        mWidgets = new ConcurrentHashMap<>();

        mIsPresentingImmersive.observe(this, this::onPresentingImmersiveChange);

        super.onCreate(savedInstanceState);

        mWidgetContainer = new FrameLayout(this);
        // runtime.setContainerView(mWidgetContainer); // REMOVED

        // mPermissionDelegate = new PermissionDelegate(this, this); // REMOVED

        mAudioEngine = new AudioEngine(this,
                BuildConfig.USE_SOUNDPOOL
                        ? new AndroidMediaPlayerSoundPool(getBaseContext())
                        : new AndroidMediaPlayer(getBaseContext()));
        mAudioEngine.setEnabled(SettingsStore.getInstance(this).isAudioEnabled());
        mAudioEngine.preloadAsync(() -> {
            Log.i(LOGTAG, "AudioEngine sounds preloaded!");
            // mAudioEngine.playSound(AudioEngine.Sound.AMBIENT, true);
        });
        mAudioUpdateRunnable = () -> mAudioEngine.update();

        mSettings = SettingsStore.getInstance(this);
        mSettings.initModel(this);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        queueRunnable(() -> {
            createOffscreenDisplay();
            createCaptureSurface();
        });
        final String tempPath = getCacheDir().getAbsolutePath();
        queueRunnable(() -> setTemporaryFilePath(tempPath));

        initializeWidgets(); // This will need significant changes later

        // loadFromIntent(getIntent()); // REMOVED - Will be replaced by logic to load specific local video

        // Setup the search engine
        // mSearchEngineWrapper = SearchEngineWrapper.get(this); // REMOVED
        // mSearchEngineWrapper.registerForUpdates(); // REMOVED

        // getServicesProvider().getConnectivityReceiver().addListener(mConnectivityDelegate); // REMOVED - Connectivity not primary for local player

        // GeolocationWrapper.INSTANCE.update(this); // REMOVED

        // initializeSpeechRecognizer(); // REMOVED - Revisit if local voice commands needed

        mPoorPerformanceAllowList = new HashSet<>(); // Likely REMOVE later, no "pages" to perform poorly

        // FIXME: We don't have any crash report analysis tool, so we need to disable this for the time being.
        // if (false) // REMOVED
            // checkForCrash(); // Kept, app crashes are still relevant

        // setLockMode(mSettings.isHeadLockEnabled() ? WidgetManagerDelegate.HEAD_LOCK : WidgetManagerDelegate.NO_LOCK); // REMOVED for now, spatial controls will manage this
        // if (mSettings.getPointerMode() == WidgetManagerDelegate.TRACKED_EYE) // REMOVED
            // checkEyeTrackingPermissions(aPermissionGranted -> setPointerMode(aPermissionGranted ? WidgetManagerDelegate.TRACKED_EYE : WidgetManagerDelegate.TRACKED_POINTER)); // REMOVED
        // else // REMOVED
            // setPointerMode(mSettings.getPointerMode()); // REMOVED

        // Show the launch dialogs, if needed.
        // if (!showTermsServiceDialogIfNeeded()) { // REMOVED - Legal dialogs might be different for a pure video player
            // if (!showPrivacyDialogIfNeeded()) { // REMOVED
                // showWhatsNewDialogIfNeeded(); // REMOVED
            // }
        // }

        getLifecycleRegistry().setCurrentState(Lifecycle.State.CREATED);
    }

    protected void initializeWidgets() {
        UISurfaceTextureRenderer.setRenderActive(true);

        // Empty widget just for handling focus on empty space
        mRootWidget = new RootWidget(this);
        mRootWidget.setClickCallback(() -> {
            for (WorldClickListener listener: mWorldClickListeners) {
                listener.onWorldClick();
            }
        });

        // Create Browser navigation widget
        // mNavigationBar = new NavigationBarWidget(this); // REMOVED

        // Create keyboard widget
        mKeyboard = new KeyboardWidget(this); // Kept for now

        // Create the WebXR interstitial
        // mWebXRInterstitial = new WebXRInterstitialWidget(this); // REMOVED

        // Windows
        mWindows = new Windows(this); // Kept, will manage video panels instead of web pages
        mWindows.setDelegate(new Windows.Delegate() { // Simplified delegate
            @Override
            public void onFocusedWindowChanged(@NonNull WindowWidget aFocusedWindow, @Nullable WindowWidget aPrevFocusedWindow) {
                // attachToWindow(aFocusedWindow, aPrevFocusedWindow); // Will need rework for video panels
                // mTray.setAddWindowVisible(mWindows.canOpenNewWindow()); // Tray logic to be re-evaluated
                // mNavigationBar.hideAllNotifications(); // REMOVED
                Log.d(LOGTAG, "onFocusedWindowChanged - needs rework for video panels");
            }
            @Override
            public void onWindowBorderChanged(@NonNull WindowWidget aChangeWindow) {
                // mKeyboard.proxifyLayerIfNeeded(mWindows.getCurrentWindows()); // Keyboard interaction to be re-evaluated
                Log.d(LOGTAG, "onWindowBorderChanged - needs rework for video panels");
            }

            @Override
            public void onWindowsMoved() {
                // mNavigationBar.hideAllNotifications(); // REMOVED
                // updateWidget(mTray); // Tray logic to be re-evaluated
                Log.d(LOGTAG, "onWindowsMoved - needs rework for video panels");
            }

            @Override
            public void onWindowClosed() {
                // mTray.setAddWindowVisible(mWindows.canOpenNewWindow()); // Tray logic to be re-evaluated
                // mNavigationBar.hideAllNotifications(); // REMOVED
                // updateWidget(mTray); // Tray logic to be re-evaluated
                Log.d(LOGTAG, "onWindowClosed - needs rework for video panels");
            }

            @Override
            public void onWindowVideoAvailabilityChanged(@NonNull WindowWidget aWindow) {
                // @CPULevelFlags int cpuLevel = mWindows.isVideoAvailable() ? WidgetManagerDelegate.CPU_LEVEL_HIGH :
                //         WidgetManagerDelegate.CPU_LEVEL_NORMAL; // This logic might change with ExoPlayer

                // queueRunnable(() -> setCPULevelNative(cpuLevel)); // Native CPU level might not be relevant

                if (mPlatformPlugin != null) {
                    // mPlatformPlugin.onVideoAvailabilityChange(); // This callback might be repurposed
                }
                Log.d(LOGTAG, "onWindowVideoAvailabilityChanged - needs rework for ExoPlayer");
            }

            @Override
            public void onIsWindowFullscreenChanged(boolean isFullscreen) {
                if (mPlatformPlugin != null) {
                    // mPlatformPlugin.onIsFullscreenChange(isFullscreen); // This callback might be repurposed
                }
                Log.d(LOGTAG, "onIsWindowFullscreenChanged - needs rework");
            }
        });

        // Create the tray
        mTray = new TrayWidget(this); // Kept for now, may be repurposed for video controls or removed
        // mTray.addListeners(mWindows); // Tray interaction with Windows to be re-evaluated
        // mTray.setAddWindowVisible(mWindows.canOpenNewWindow()); // Tray logic to be re-evaluated

        // Create Tabs bar widget
        // if (mSettings.getTabsLocation() == SettingsStore.TABS_LOCATION_HORIZONTAL) { // REMOVED
        //     mTabsBar = new HorizontalTabsBar(this, mWindows); // REMOVED
        // } else if (mSettings.getTabsLocation() == SettingsStore.TABS_LOCATION_VERTICAL) { // REMOVED
        //     mTabsBar = new VerticalTabsBar(this, mWindows); // REMOVED
        // } else { // REMOVED
        //     mTabsBar = null; // REMOVED
        // } // REMOVED

        // attachToWindow(mWindows.getFocusedWindow(), null); // Will need rework

        addWidgets(Arrays.asList(mRootWidget, mKeyboard, mTray)); // mNavigationBar, mTabsBar, mWebXRInterstitial removed

        // Create the platform plugin after widgets are created to be extra safe.
        mPlatformPlugin = createPlatformPlugin(this); // Kept
        if (mPlatformPlugin != null)
            mPlatformPlugin.registerListener(this);

        mWindows.restoreSessions();
    }

    private void onPresentingImmersiveChange(boolean presenting) {
        if (mPlatformPlugin != null) {
            mPlatformPlugin.onIsPresentingImmersiveChange(presenting);
        }
    }

    private void attachToWindow(@NonNull WindowWidget aWindow, @Nullable WindowWidget aPrevWindow) {
        // mPermissionDelegate.setParentWidgetHandle(aWindow.getHandle()); // REMOVED
        // mNavigationBar.attachToWindow(aWindow); // REMOVED
        // mKeyboard.attachToWindow(aWindow); // Keyboard logic to be re-evaluated
        // mTray.attachToWindow(aWindow); // Tray logic to be re-evaluated

        // if (mTabsBar != null) { // REMOVED
            // mTabsBar.attachToWindow(aWindow); // REMOVED
        // } // REMOVED
        // mWindows.adjustWindowOffsets(); // Window logic to be re-evaluated

        // if (aPrevWindow != null) { // REMOVED
            // updateWidget(mNavigationBar); // REMOVED
            // updateWidget(mKeyboard); // Keyboard logic to be re-evaluated
            // updateWidget(mTray); // Tray logic to be re-evaluated
            // if (mTabsBar != null) { // REMOVED
                // updateWidget(mTabsBar); // REMOVED
            // }
        // }
        Log.d(LOGTAG, "attachToWindow - needs complete rework for video panels");
    }

    // WRuntime.CrashReportIntent getCrashReportIntent() { // REMOVED
        // return EngineProvider.INSTANCE.getOrCreateRuntime(this).getCrashReportIntent(); // REMOVED
    // }

    private void initializeSpeechRecognizer() {
        try {
            String speechService = SettingsStore.getInstance(this).getVoiceSearchService();
            SpeechRecognizer speechRecognizer = SpeechServices.getInstance(this, speechService);
            ((VRBrowserApplication) getApplication()).setSpeechRecognizer(speechRecognizer);
        } catch (Exception e) {
            Log.e(LOGTAG, "Exception creating the speech recognizer: " + e);
            ((VRBrowserApplication) getApplication()).setSpeechRecognizer(null);
        }
    }

    // Returns true if the dialog was shown, false otherwise.
    // private boolean showTermsServiceDialogIfNeeded() { // REMOVED
    //     if (SettingsStore.getInstance(this).isTermsServiceAccepted()) {
    //         return false;
    //     }
    //
    //     LegalDocumentDialogWidget termsServiceDialog =
    //             new LegalDocumentDialogWidget(this, LegalDocumentDialogWidget.LegalDocument.TERMS_OF_SERVICE);
    //
    //     termsServiceDialog.setDelegate(response -> {
    //         if (response) {
    //             SettingsStore.getInstance(this).setTermsServiceAccepted(true);
    //             if (!showPrivacyDialogIfNeeded()) {
    //                 showWhatsNewDialogIfNeeded();
    //             }
    //         } else {
    //             // TODO ask for confirmation ("are you really sure that you want to close Wolvic?")
    //             Log.w(LOGTAG, "The user rejected the privacy policy, closing the app.");
    //             finish();
    //         }
    //     });
    //     termsServiceDialog.attachToWindow(mWindows.getFocusedWindow());
    //     termsServiceDialog.show(UIWidget.REQUEST_FOCUS);
    //     return true;
    // }

    // Returns true if the dialog was shown, false otherwise.
    // private boolean showPrivacyDialogIfNeeded() { // REMOVED
    //     if (SettingsStore.getInstance(this).isPrivacyPolicyAccepted()) {
    //         return false;
    //     }
    //
    //     LegalDocumentDialogWidget privacyPolicyDialog
    //             = new LegalDocumentDialogWidget(this, LegalDocumentDialogWidget.LegalDocument.PRIVACY_POLICY);
    //     privacyPolicyDialog.setDelegate(response -> {
    //         if (response) {
    //             SettingsStore.getInstance(this).setPrivacyPolicyAccepted(true);
    //             showWhatsNewDialogIfNeeded();
    //         } else {
    //             // TODO ask for confirmation ("are you really sure that you want to close Wolvic?")
    //             Log.w(LOGTAG, "The user rejected the privacy policy, closing the app.");
    //             finish();
    //         }
    //     });
    //     privacyPolicyDialog.attachToWindow(mWindows.getFocusedWindow());
    //     privacyPolicyDialog.show(UIWidget.REQUEST_FOCUS);
    //     return true;
    // }

    // private void showWhatsNewDialogIfNeeded() { // REMOVED
    //     if (SettingsStore.getInstance(this).isWhatsNewDisplayed() || mWindows.getFocusedWindow().isKioskMode()
    //         || BuildConfig.FLAVOR_backend.equals("chromium")) {
    //         return;
    //     }
    //
    //     mWhatsNewWidget = new WhatsNewWidget(this);
    //     mWhatsNewWidget.setLoginOrigin(Accounts.LoginOrigin.NONE);
    //     mWhatsNewWidget.getPlacement().parentHandle = mWindows.getFocusedWindow().getHandle();
    //     mWhatsNewWidget.show(UIWidget.REQUEST_FOCUS);
    // }

    @Override
    protected void onStart() {
        SettingsStore.getInstance(getBaseContext()).setPid(Process.myPid());
        super.onStart();
        mFragmentController.dispatchStart();
        getLifecycleRegistry().setCurrentState(Lifecycle.State.STARTED);
        if (mTray == null) {
            Log.e(LOGTAG, "Failed to start Tray clock");
        } else {
            mTray.start(this);
        }
    }

    @Override
    protected void onStop() {
        SettingsStore.getInstance(getBaseContext()).setPid(0);
        super.onStop();
        mFragmentController.dispatchStop();
        TelemetryService.sessionStop();
        if (mTray != null) {
            mTray.stop(this);
        }
    }

    public void flushBackHandlers() {
        int backCount = mBackHandlers.size();
        while (backCount > 0) {
            mBackHandlers.getLast().run();
            int newBackCount = mBackHandlers.size();
            if (newBackCount == backCount) {
                Log.e(LOGTAG, "Back counter is not decreasing,");
                break;
            }
            backCount = newBackCount;
        }
    }

    @Override
    protected void onPause() {
        if (mIsPresentingImmersive.getValue()) {
            // This needs to be sync to ensure that WebVR is correctly paused.
            // Also prevents a deadlock in onDestroy when the BrowserWidget is released.
            exitImmersiveSync();
        }

        mAudioEngine.pauseEngine();
        mFragmentController.dispatchPause();

        mWindows.onPause();

        for (Widget widget: mWidgets.values()) {
            widget.onPause();
        }
        // Reset so the dialog will show again on resume.
        // mConnectionAvailable = true; // REMOVED
        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.onPause();
        }
        mWidgetContainer.getViewTreeObserver().removeOnGlobalFocusChangeListener(globalFocusListener);
        super.onPause();
        UISurfaceTextureRenderer.setRenderActive(false);
    }

    @Override
    protected void onResume() {
        UISurfaceTextureRenderer.setRenderActive(true);
        MotionEventGenerator.clearDevices();
        mWidgetContainer.getViewTreeObserver().addOnGlobalFocusChangeListener(globalFocusListener);
        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.onResume();
        }

        mFragmentController.dispatchResume();
        // mWindows.onResume(); // Needs review for video player

        mAudioEngine.resumeEngine();
        for (Widget widget: mWidgets.values()) {
            widget.onResume();
        }

        // If we're signed-in, poll for any new device events (e.g. received tabs) on activity resume.
        // There's no push support right now, so this helps with the perception of speedy tab delivery.
        // ((VRBrowserApplication)getApplicationContext()).getAccounts().refreshDevicesAsync(); // REMOVED
        // ((VRBrowserApplication)getApplicationContext()).getAccounts().pollForEventsAsync(); // REMOVED

        super.onResume();
        ((VRBrowserApplication)getApplication()).setCurrentActivity(this);
        getLifecycleRegistry().setCurrentState(Lifecycle.State.RESUMED);
    }

    @Override
    protected void onDestroy() {
        ((VRBrowserApplication)getApplication()).onActivityDestroy();
        SettingsStore.getInstance(getBaseContext()).setPid(0);
        // Unregister the crash service broadcast receiver
        unregisterReceiver(mCrashReceiver); // Kept for app crashes
        // mSearchEngineWrapper.unregisterForUpdates(); // REMOVED
        if (mPlatformPlugin != null)
            mPlatformPlugin.unregisterListener(this); // Kept

        mFragmentController.dispatchDestroy(); // Kept

        for (Widget widget: mWidgets.values()) {
            widget.releaseWidget(); // Kept
        }

        if (mOffscreenDisplay != null) {
            mOffscreenDisplay.release(); // Kept
        }
        if (mAudioEngine != null) {
            mAudioEngine.release(); // Kept
        }
        // if (mPermissionDelegate != null) { // REMOVED
            // mPermissionDelegate.release(); // REMOVED
        // }

        // mTray.removeListeners(mWindows); // Tray logic to be re-evaluated

        // Remove all widget listeners
        // mWindows.onDestroy(); // Needs review for video player

        BitmapCache.getInstance(this).onDestroy(); // Kept

        // SessionStore.get().onDestroy(); // REMOVED

        // getServicesProvider().getConnectivityReceiver().removeListener(mConnectivityDelegate); // REMOVED

        mPrefs.unregisterOnSharedPreferenceChangeListener(this); // Kept

        super.onDestroy();
        getLifecycleRegistry().setCurrentState(Lifecycle.State.DESTROYED);
        mViewModelStore.clear();
        // Always exit to work around https://github.com/MozillaReality/FirefoxReality/issues/3363
        finish();
        System.exit(0);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        Log.d(LOGTAG,"VRBrowserActivity onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);

        // if (getCrashReportIntent().action_crashed.equals(intent.getAction())) { // REMOVED - CrashReportIntent logic removed
            // Log.e(LOGTAG, "Restarted after a crash");
        // } else if (isLaunchImmersive()) { // REMOVED - Immersive launch logic removed
            // We need to restart the Activity to ensure that the engine settings are initialized.
            // finish();
            // startActivity(intent);
        // } else {
            // loadFromIntent(intent); // REMOVED - Intent loading logic for URLs removed
        // }
        // For now, onNewIntent will do nothing specific to video player. Revisit if needed.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Language language = LocaleUtils.getDisplayLanguage(this);
        newConfig.setLocale(language.getLocale());
        // TODO: Deprecated updateConfiguration(Configuration,DisplayMetrics),
        //  see https://github.com/Igalia/wolvic/issues/797
        getBaseContext().getResources().updateConfiguration(newConfig, getBaseContext().getResources().getDisplayMetrics());

        LocaleUtils.update(this, language); // Kept for general localization

        // SessionStore.get().onConfigurationChanged(newConfig); // REMOVED
        mWidgets.forEach((i, widget) -> widget.onConfigurationChanged(newConfig)); // Kept for remaining widgets
        // SendTabDialogWidget.getInstance(this).onConfigurationChanged(newConfig); // REMOVED

        // SearchEngineWrapper s = SearchEngineWrapper.get(this); // REMOVED
        // s.setupPreferredSearchEngine(); // REMOVED
        // s.setCurrentSearchEngine(null); // REMOVED

        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // if (Objects.equals(key, getString(R.string.settings_key_voice_search_service))) { // REMOVED
            // initializeSpeechRecognizer(); // REMOVED
        // } else if (Objects.equals(key, getString(R.string.settings_key_head_lock))) { // REMOVED
            // boolean isHeadLockEnabled = mSettings.isHeadLockEnabled(); // REMOVED
            // setLockMode(isHeadLockEnabled ? WidgetManagerDelegate.HEAD_LOCK : WidgetManagerDelegate.NO_LOCK); // REMOVED
            // if (!isHeadLockEnabled) // REMOVED
                // recenterUIYaw(WidgetManagerDelegate.YAW_TARGET_ALL); // REMOVED
        // } else if (Objects.equals(key, getString(R.string.settings_key_tabs_location))) { // REMOVED
            // remove the previous widget
            // if (mTabsBar != null) { // REMOVED
                // removeWidget(mTabsBar); // REMOVED
                // mTabsBar.releaseWidget(); // REMOVED
            // }

            // switch (mSettings.getTabsLocation()) { // REMOVED
                // case SettingsStore.TABS_LOCATION_HORIZONTAL: // REMOVED
                    // mTabsBar = new HorizontalTabsBar(this, mWindows); // REMOVED
                    // break; // REMOVED
                // case SettingsStore.TABS_LOCATION_VERTICAL: // REMOVED
                    // mTabsBar = new VerticalTabsBar(this, mWindows); // REMOVED
                    // break; // REMOVED
                // case SettingsStore.TABS_LOCATION_TRAY: // REMOVED
                // default: // REMOVED
                    // mTabsBar = null; // REMOVED
                    // mWindows.adjustWindowOffsets(); // REMOVED
                    // return; // REMOVED
            // }
            // addWidget(mTabsBar); // REMOVED
            // mTabsBar.attachToWindow(mWindows.getFocusedWindow()); // REMOVED
            // updateWidget(mTabsBar); // REMOVED
            // mWindows.adjustWindowOffsets(); // REMOVED
        // }
        // Most preferences were browser related. This method might become empty or handle video player specific settings.
        Log.d(LOGTAG, "onSharedPreferenceChanged for key: " + key + " - needs review for video player settings");
    }

    void loadFromIntent(final Intent intent) {
        // This entire method is for loading URLs from intents, which is not applicable to local video player.
        // It will be replaced by logic to handle opening a specific hardcoded video file, or later,
        // perhaps a file picker intent if we add that functionality.
        Log.d(LOGTAG, "loadFromIntent called, but web loading logic is removed. Video player specific loading to be implemented.");

        // Example of what might go here later for a specific file:
        // String hardcodedVideoPath = "/sdcard/Movies/test_video.mp4";
        // mWindows.getFocusedWindow().loadVideo(hardcodedVideoPath); // Assuming WindowWidget gets a loadVideo method
    }

    // private ConnectivityReceiver.Delegate mConnectivityDelegate = connected -> { // REMOVED
        // mConnectionAvailable = connected; // REMOVED
    // };

    private void checkForCrash() {
        // This logic for app crash files (not web content crashes) can be kept.
        final ArrayList<String> files = CrashReporterService.findCrashFiles(getBaseContext());
        if (files.isEmpty()) {
            Log.d(LOGTAG, "No crash files found.");
            return;
        }
        boolean isCrashReportingEnabled = SettingsStore.getInstance(this).isCrashReportingEnabled();
        if (isCrashReportingEnabled) {
            // SystemUtils.postCrashFiles(this, files); // SystemUtils might need review if it's web specific
            Log.d(LOGTAG, "Crash reporting enabled, would post files.");
        } else {
            if (mCrashDialog == null) {
                mCrashDialog = new CrashDialogWidget(this, files);
            }
            mCrashDialog.show(UIWidget.REQUEST_FOCUS);
        }
    }

    private void handleContentCrashIntent(@NonNull final Intent intent) {
        // This is specifically for web content crashes. Can be removed.
        Log.e(LOGTAG, "handleContentCrashIntent called - REMOVING web content crash logic.");
        // final String dumpFile = intent.getStringExtra(getCrashReportIntent().extra_minidump_path);
        // final String extraFile = intent.getStringExtra(getCrashReportIntent().extra_extras_path);
        // Log.d(LOGTAG, "Dump File: " + dumpFile);
        // Log.d(LOGTAG, "Extras File: " + extraFile);
        // Log.d(LOGTAG, "Fatal: " + intent.getBooleanExtra(getCrashReportIntent().extra_crash_fatal, false));
        //
        // boolean isCrashReportingEnabled = SettingsStore.getInstance(this).isCrashReportingEnabled();
        // if (isCrashReportingEnabled) {
            // SystemUtils.postCrashFiles(this, dumpFile, extraFile);
        // } else {
            // if (mCrashDialog == null) {
                // mCrashDialog = new CrashDialogWidget(this, dumpFile, extraFile);
            // }
            // mCrashDialog.show(UIWidget.REQUEST_FOCUS);
        // }
    }

    FrameLayout getWidgetContainer() {
        return mWidgetContainer;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        // Determine which lifecycle or system event was raised.
        switch (level) {

            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                // Curently ignore these levels. They are handled somewhere else.
                break;
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
                // It looks like these come in all at the same time so just always suspend inactive Sessions.
                Log.d(LOGTAG, "Memory pressure observed. Old logic for suspending inactive sessions removed.");
                // SessionStore.get().suspendAllInactiveSessions(); // REMOVED
                break;
            default:
                Log.e(LOGTAG, "onTrimMemory unknown level: " + level);
                break;
        }
    }

    private void showAppExitDialog() {
        mWindows.getFocusedWindow().showConfirmPrompt(
            getString(R.string.app_name),
            getString(R.string.exit_confirm_dialog_body, getString(R.string.app_name)),
                new String[]{
                        getString(R.string.exit_confirm_dialog_button_cancel),
                        getString(R.string.exit_confirm_dialog_button_quit),
                }, (index, isChecked) -> {
                    if (index == PromptDialogWidget.POSITIVE) {
                        VRBrowserActivity.super.onBackPressed();
                        finishAndRemoveTask();
                    }
                });
    }

    @Override
    @Deprecated
    public void onBackPressed() {
        // if (mPlatformPlugin != null && mPlatformPlugin.onBackPressed()) { // Platform plugin logic might be reused
            // return;
        // }
        // if (mIsPresentingImmersive.getValue()) { // REMOVED WebXR
            // queueRunnable(this::exitImmersiveNative); // REMOVED WebXR
            // return;
        // }
        if (mBackHandlers.size() > 0) { // This generic back handling could be kept if UI dialogs are used
            mBackHandlers.getLast().run();
            return;
        }
        // if (!mWindows.handleBack()) { // Window back handling needs review for video player
            // showAppExitDialog(); // App exit dialog can be kept
        // }
        // Simplified:
        showAppExitDialog();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // if (isNotSpecialKey(event) && mKeyboard.dispatchKeyEvent(event)) { // Keyboard input to webview removed
            // return true;
        // }
        // For now, all key events go to super. Revisit for keyboard controls for video player.
        return super.dispatchKeyEvent(event);
    }

    // final Object mWaitLock = new Object(); // REMOVED WebXR sync

    // final Runnable mExitImmersive = new Runnable() { // REMOVED WebXR
        // @Override
        // public void run() {
            // exitImmersiveNative();
            // synchronized(mWaitLock) {
                // mWaitLock.notifyAll();
            // }
        // }
    // };

    // private void exitImmersiveSync() { // REMOVED WebXR
        // synchronized (mWaitLock) {
            // queueRunnable(mExitImmersive);
            // try {
                // mWaitLock.wait();
            // } catch (InterruptedException e) {
                // Log.e(LOGTAG, "Waiting for exit immersive onPause interrupted");
            // }
        // }
    // }

    @Keep
    @SuppressWarnings("unused")
    void dispatchCreateWidget(final int aHandle, final SurfaceTexture aTexture, final int aWidth, final int aHeight) {
        // This is for native UI widgets. Will be needed for ExoPlayer output to a SurfaceTexture.
        // The logic for adding to mWidgetContainer might change.
        runOnUiThread(() -> {
            final Widget widget = mWidgets.get(aHandle);
            if (widget == null) {
                Log.e(LOGTAG, "Widget " + aHandle + " not found");
                return;
            }
            if (aTexture == null) {
                Log.d(LOGTAG, "Widget: " + aHandle + " (" + aWidth + "x" + aHeight + ") received a null surface texture.");
            } else {
                Runnable aFirstDrawCallback = () -> {
                    if (!widget.isFirstPaintReady()) {
                        widget.setFirstPaintReady(true);
                        updateWidget(widget);
                    }
                };
                widget.setSurfaceTexture(aTexture, aWidth, aHeight, aFirstDrawCallback);
            }
            // Add widget to a virtual display for invalidation
            View view = (View) widget;
            if (view.getParent() == null) {
                mWidgetContainer.addView(view, new FrameLayout.LayoutParams(widget.getPlacement().viewWidth(), widget.getPlacement().viewHeight()));
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void dispatchCreateWidgetLayer(final int aHandle, final Surface aSurface, final int aWidth, final int aHeight, final long aNativeCallback) {
        // This is for native UI widgets using SurfaceView (Hardware Layer). Will be needed for ExoPlayer output.
        runOnUiThread(() -> {
            final Widget widget = mWidgets.get(aHandle);
            if (widget == null) {
                Log.e(LOGTAG, "Widget " + aHandle + " not found");
                return;
            }

            FinalizerRunnable firstDrawCallback = new FinalizerRunnable(() -> {
                if (aNativeCallback != 0) {
                    queueRunnable(() -> runCallbackNative(aNativeCallback));
                }
                if (aSurface != null && !widget.isFirstPaintReady()) {
                    widget.setFirstPaintReady(true);
                    updateWidget(widget);
                }
            },
            () -> {
                if (aNativeCallback != 0) {
                    queueRunnable(() -> deleteCallbackNative(aNativeCallback));
                }
            });

            widget.setSurface(aSurface, aWidth, aHeight, firstDrawCallback);

            UIWidget view = (UIWidget) widget;
            // Add widget to a virtual display for invalidation
            if (aSurface != null && view.getParent() == null) {
                mWidgetContainer.addView(view, new FrameLayout.LayoutParams(widget.getPlacement().viewWidth(), widget.getPlacement().viewHeight()));
            }  else if (aSurface == null && view.getParent() != null) {
                mWidgetContainer.removeView(view);
            }
            view.setResizing(false);
            view.postInvalidate();
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void handleMotionEvent(final int aHandle, final int aDevice, final boolean aFocused, final boolean aPressed, final float aX, final float aY) {
        // This logic will need to be adapted for interaction with video player controls if any are drawn as widgets.
        // The current scaling and WindowWidget specific logic will be removed/changed.
        runOnUiThread(() -> {
            Widget widget = mWidgets.get(aHandle);

            if (!isWidgetInputEnabled(widget)) { // isWidgetInputEnabled might need changes based on active dialogs for video player
                widget = null;
            }
            mLastMotionEventWidgetHandle = widget != null ? widget.getHandle() : 0;

            // float scale = widget != null ? widget.getPlacement().textureScale : SettingsStore.getInstance(this).getDisplayDpi() / 100.0f; // REMOVED old scaling
            // // We shouldn't divide the scale factor when we pass the motion event to the web engine
            // if (widget instanceof WindowWidget) { // WindowWidget will be different
                // WindowWidget windowWidget = (WindowWidget) widget;
                // if (!windowWidget.isNativeContentVisible()) { // This concept will change
                    // scale = 1.0f;
                // }
            // } else if (widget instanceof OverlayContentWidget) { // OverlayContentWidget might be removed
                // scale = 1.0f;
            // }
            // final float x = aX / scale; // Simplified coordinates for now
            // final float y = aY / scale; // Simplified coordinates for now
            final float x = aX;
            final float y = aY;


            if (widget == null) {
                MotionEventGenerator.dispatch(this, mRootWidget, aDevice, aFocused, aPressed, x, y);
            } else if (widget.getBorderWidth() > 0) {
                final int border = widget.getBorderWidth();
                MotionEventGenerator.dispatch(this, widget, aDevice, aFocused, aPressed, x - border, y - border);
            } else {
                MotionEventGenerator.dispatch(this, widget, aDevice, aFocused, aPressed, x, y);
            }
            Log.d(LOGTAG, "handleMotionEvent - needs review for video player controls");
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void handleScrollEvent(final int aHandle, final int aDevice, final float aX, final float aY) {
        // This might be used for volume control or seeking if mapped to scroll.
        runOnUiThread(() -> {
            Widget widget = mWidgets.get(aHandle);
            if (!isWidgetInputEnabled(widget)) {
                return;
            }
            if (widget == null) {
                // if (getNavigationBar().isInVRVideo()) { // REMOVED NavigationBar
                    // widget = getNavigationBar().getMediaControlsWidget();
                // } else {
                    Log.e(LOGTAG, "Failed to find widget for scroll event: " + aHandle);
                    return;
                // }
            }
            // float scrollDirection = mSettings.getScrollDirection() == 0 ? 1.0f : -1.0f; // Scroll direction setting might be removed
            // MotionEventGenerator.dispatchScroll(widget, aDevice, true,aX * scrollDirection, aY * scrollDirection);
            Log.d(LOGTAG, "handleScrollEvent - needs review for video player controls. X: " + aX + " Y: " + aY);
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void handleGesture(final int aType) {
        // Gestures like swipe left/right for back/forward are web-specific.
        // This might be repurposed for e.g. next/prev video in a playlist.
        runOnUiThread(() -> {
            // boolean consumed = false;
            // if ((aType == GestureSwipeLeft) && (mLastGesture == GestureSwipeLeft)) {
                // Log.d(LOGTAG, "Go back!");
                // SessionStore.get().getActiveSession().goBack(); // REMOVED
                // consumed = true;
            // } else if ((aType == GestureSwipeRight) && (mLastGesture == GestureSwipeRight)) {
                // Log.d(LOGTAG, "Go forward!");
                // SessionStore.get().getActiveSession().goForward(); // REMOVED
                // consumed = true;
            // }
            // if (mLastRunnable != null) {
                // mLastRunnable.mCanceled = true;
                // mLastRunnable = null;
            // }
            // if (consumed) {
                // mLastGesture = NoGesture;
            // } else {
                // mLastGesture = aType;
                // mLastRunnable = new SwipeRunnable();
                // mHandler.postDelayed(mLastRunnable, SwipeDelay);
            // }
            Log.d(LOGTAG, "handleGesture type: " + aType + " - web specific logic removed.");
        });
    }

    @SuppressWarnings({"UnusedDeclaration"})
    @Keep
    void handleBack() { // Native call
        runOnUiThread(() -> {
            // dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)); // This now calls our simplified onBackPressed
            // dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
            onBackPressed(); // Call our own onBackPressed directly
        });
    }

    @SuppressWarnings({"UnusedDeclaration"})
    @Keep
    void handleAppExit() { // Native call
        runOnUiThread(() -> {
            showAppExitDialog();
        });
    }

    @Keep
    @SuppressWarnings({"UnusedDeclaration"})
    void handleAudioPose(float qx, float qy, float qz, float qw, float px, float py, float pz) {
        // Audio engine spatialization. Kept.
        mAudioEngine.setPose(qx, qy, qz, qw, px, py, pz);

        // https://developers.google.com/vr/reference/android/com/google/vr/sdk/audio/GvrAudioEngine.html#resume()
        // The initialize method must be called from the main thread at a regular rate.
        runOnUiThread(mAudioUpdateRunnable);
    }

    @Keep
    @SuppressWarnings("unused")
    void handleResize(final int aHandle, final float aWorldWidth, final float aWorldHeight) {
        // This was for window resizing, will be different for video panels.
        // runOnUiThread(() -> mWindows.getFocusedWindow().handleResizeEvent(aWorldWidth, aWorldHeight));
        Log.d(LOGTAG, "handleResize - needs rework for video panels");
    }

    @Keep
    @SuppressWarnings("unused")
    void handleMoveEnd(final int aHandle, final float aDeltaX, final float aDeltaY, final float aDeltaZ, final float aRotation) {
        // This was for window moving, will be different for video panels.
        runOnUiThread(() -> {
            Widget widget = mWidgets.get(aHandle);
            if (widget != null) {
                // widget.handleMoveEvent(aDeltaX, aDeltaY, aDeltaZ, aRotation);
                Log.d(LOGTAG, "handleMoveEnd for widget " + aHandle + " - needs rework for video panels");
            }
        });
    }

    @Keep
    @SuppressWarnings("unused")
    void registerExternalContext(long aContext) {
        // EngineProvider.INSTANCE.getOrCreateRuntime(this).setExternalVRContext(aContext); // REMOVED EngineProvider
        Log.d(LOGTAG, "registerExternalContext - EngineProvider removed.");
    }

    // final Object mCompositorLock = new Object(); // REMOVED WebXR compositor sync

    // class PauseCompositorRunnable implements Runnable { // REMOVED WebXR compositor sync
        // public boolean done;
        // @Override
        // public void run() {
            // synchronized (mCompositorLock) {
                // Log.d(LOGTAG, "About to pause Compositor");
                // mWindows.pauseCompositor(); // REMOVED
                // Log.d(LOGTAG, "Compositor Paused");
                // done = true;
                // mCompositorLock.notify();
            // }
        // }
    // }

    @Keep
    @SuppressWarnings("unused")
    void onEnterWebXR() { // REMOVED
        // if (Thread.currentThread() == mUiThread) {
            // return;
        // }
        // mIsPresentingImmersive.postValue(true);
        // runOnUiThread(() -> {
            // mWindows.enterImmersiveMode();
            // for (WebXRListener listener: mWebXRListeners) {
                // listener.onEnterWebXR();
            // }
        // });
        // TelemetryService.startImmersive(); // REMOVED
        //
        // PauseCompositorRunnable runnable = new PauseCompositorRunnable();
        //
        // synchronized (mCompositorLock) {
            // runOnUiThread(runnable);
            // while (!runnable.done) {
                // try {
                    // mCompositorLock.wait();
                // } catch (InterruptedException e) {
                    // Log.e(LOGTAG, "Waiting for compositor pause interrupted");
                // }
            // }
        // }
        Log.d(LOGTAG, "onEnterWebXR called - REMOVED");
    }

    @Keep
    @SuppressWarnings("unused")
    void onExitWebXR(long aCallback) { // REMOVED
        // if (Thread.currentThread() == mUiThread) {
            // return;
        // }
        // mIsPresentingImmersive.postValue(false);
        // TelemetryService.stopImmersive(); // REMOVED
        //
        // if (mLaunchImmersive) {
            // Log.d(LOGTAG, "Launched in immersive mode: exiting WebXR will finish the app");
            // finish();
        // }
        //
        // runOnUiThread(() -> {
            // mWindows.exitImmersiveMode();
            // for (WebXRListener listener: mWebXRListeners) {
                // listener.onExitWebXR();
            // }
        // });
        //
        // // Show the window in front of you when you exit immersive mode.
        // recenterUIYaw(WidgetManagerDelegate.YAW_TARGET_ALL);
        //
        // Handler handler = new Handler(Looper.getMainLooper());
        // handler.postDelayed(() -> {
            // if (!mWindows.isPaused()) {
                // Log.d(LOGTAG, "Compositor resume begin");
                // mWindows.resumeCompositor();
                // if (aCallback != 0) {
                    // queueRunnable(() -> runCallbackNative(aCallback));
                // }
                // Log.d(LOGTAG, "Compositor resume end");
            // }
        // }, 20);
        Log.d(LOGTAG, "onExitWebXR called - REMOVED");
    }
    @Keep
    @SuppressWarnings("unused")
    void onDismissWebXRInterstitial() { // REMOVED
        // runOnUiThread(() -> {
            // for (WebXRListener listener: mWebXRListeners) {
                // listener.onDismissWebXRInterstitial();
            // }
        // });
        Log.d(LOGTAG, "onDismissWebXRInterstitial called - REMOVED");
    }

    @Keep
    @SuppressWarnings("unused")
    void onWebXRRenderStateChange(boolean aRendering) { // REMOVED
        // runOnUiThread(() -> {
            // for (WebXRListener listener: mWebXRListeners) {
                // listener.onWebXRRenderStateChange(aRendering);
            // }
        // });
        Log.d(LOGTAG, "onWebXRRenderStateChange called - REMOVED");
    }

    @Keep
    @SuppressWarnings("unused")
    void renderPointerLayer(final Surface aSurface, int color, final long aNativeCallback) { // REMOVED - Pointer rendering will be different
        // runOnUiThread(() -> {
            // try {
                // Canvas canvas = aSurface.lockHardwareCanvas();
                // canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                // Paint paint = new Paint();
                // paint.setAntiAlias(true);
                // paint.setDither(true);
                // paint.setColor(color);
                // paint.setStyle(Paint.Style.FILL);
                // final float x = canvas.getWidth() * 0.5f;
                // final float y = canvas.getHeight() * 0.5f;
                // final float radius = canvas.getWidth() * 0.4f;
                // canvas.drawCircle(x, y, radius, paint);
                // paint.setColor(Color.BLACK);
                // paint.setStrokeWidth(4);
                // paint.setStyle(Paint.Style.STROKE);
                // canvas.drawCircle(x, y, radius, paint);
                // aSurface.unlockCanvasAndPost(canvas);
            // }
            // catch (Exception ex) {
                // ex.printStackTrace();
            // }
            // if (aNativeCallback != 0) {
                // queueRunnable(() -> runCallbackNative(aNativeCallback));
            // }
        // });
        Log.d(LOGTAG, "renderPointerLayer called - REMOVED");
    }

    @Keep
    @SuppressWarnings("unused")
    String getStorageAbsolutePath() { // Kept - generic utility
        final File path = getExternalFilesDir(null);
        if (path == null) {
            return "";
        }
        return path.getAbsolutePath();
    }

    @Keep
    @SuppressWarnings("unused")
    public boolean isOverrideEnvPathEnabled() { // REMOVED - Environment/Skybox logic might change
        // return SettingsStore.getInstance(this).isEnvironmentOverrideEnabled();
        return false;
    }

    @Keep
    @SuppressWarnings("unused")
    public void checkTogglePassthrough() { // Kept - Passthrough is a device feature
        if (mSettings.isStartWithPassthroughEnabled() && !mIsPassthroughEnabled) {
            runOnUiThread(this::togglePassthrough);
        }
    }

    @Keep
    @SuppressWarnings("unused")
    public void resetWindowsPosition() { // Window positioning logic will change
        // Reset the position of the windows when we are not in headlock.
        // if (mSettings.isHeadLockEnabled())
            // return;
        // runOnUiThread(() -> mWindows.resetWindowsPosition());
        Log.d(LOGTAG, "resetWindowsPosition - needs rework for video panels");
    }

    @Keep
    @SuppressWarnings("unused")
    public boolean areLayersEnabled() { // Kept - may be relevant for OpenXR layers
        return SettingsStore.getInstance(this).getLayersEnabled();
    }

    @Keep
    @SuppressWarnings("unused")
    public String getActiveEnvironment() { // REMOVED - Environment/Skybox logic might change
        // return getServicesProvider().getEnvironmentsManager().getOrDownloadEnvironment();
        return "";
    }

    @Keep
    @SuppressWarnings("unused")
    public int getPointerColor() { // REMOVED - Pointer rendering will change
        // return SettingsStore.getInstance(this).getPointerColor();
        return Color.WHITE;
    }

    private void setUseHardwareAcceleration() { // Kept - UI rendering setting
        UISurfaceTextureRenderer.setUseHardwareAcceleration(SettingsStore.getInstance(getBaseContext()).isUIHardwareAccelerationEnabled());
    }

    @Keep
    @SuppressWarnings("unused")
    private void setDeviceType(int aType) { // Kept - Device type detection
        DeviceType.setType(aType);
        setUseHardwareAcceleration();
    }

    @Keep
    @SuppressWarnings("unused")
    private void haltActivity(final int aReason) { // REMOVED - Entitlement check likely not needed for local player
        // runOnUiThread(() -> {
            // if (mConnectionAvailable && mWindows.getFocusedWindow() != null) {
                // mWindows.getFocusedWindow().showAlert(
                        // getString(R.string.not_entitled_title),
                        // getString(R.string.not_entitled_message, getString(R.string.app_name)),
                        // (index, isChecked) -> finish());
            // }
        // });
        Log.d(LOGTAG, "haltActivity called - REMOVED");
    }

    @Keep
    @SuppressWarnings("unused")
    private void handlePoorPerformance() { // REMOVED - No web pages to perform poorly
        // runOnUiThread(() -> {
            // if (!mSettings.isPerformanceMonitorEnabled()) {
                // return;
            // }
            // // Don't block poorly performing immersive pages.
            // if (mIsPresentingImmersive.getValue()) {
                // return;
            // }
            // WindowWidget window = mWindows.getFocusedWindow();
            // if (window == null || window.getSession() == null) {
                // return;
            // }
            // final String originalUri = window.getSession().getCurrentUri();
            // if (mPoorPerformanceAllowList.contains(originalUri)) {
                // return;
            // }
            // window.getSession().loadHomePage();
            // final String[] buttons = {getString(R.string.ok_button), getString(R.string.performance_unblock_page)};
            // window.showConfirmPrompt(getString(R.string.performance_title),
                    // getString(R.string.performance_message),
                    // buttons,
                    // (index, isChecked) -> {
                // if (index == PromptDialogWidget.NEGATIVE) {
                    // mPoorPerformanceAllowList.add(originalUri);
                    // window.getSession().loadUri(originalUri);
                // }
            // });
        // });
        Log.d(LOGTAG, "handlePoorPerformance called - REMOVED");
    }

    @Keep
    @SuppressWarnings("unused")
    private void onAppLink(String aJSON) { // REMOVED - No web app links
        // runOnUiThread(() -> {
            // try {
                // JSONObject object = new JSONObject(aJSON);
                // String uri = object.optString(EXTRA_URL);
                // Session session = SessionStore.get().getActiveSession();
                // if (!StringUtils.isEmpty(uri) && session != null) {
                    // session.loadUri(uri);
                // }
                //
            // } catch (Exception ex) {
                // Log.e(LOGTAG, "Error parsing app link JSON: " + ex.toString());
            // }
            //
        // });
        Log.d(LOGTAG, "onAppLink called - REMOVED");
    }

    @Keep
    @SuppressWarnings("unused")
    private void disableLayers() { // Kept - OpenXR layers setting
        runOnUiThread(() -> {
            SettingsStore.getInstance(this).setDisableLayers(true);
        });
    }

    @Keep
    @SuppressWarnings("unused")
    private void appendAppNotesToCrashReport(String aNotes) { // REMOVED - EngineProvider removed
        // runOnUiThread(() -> EngineProvider.INSTANCE.getOrCreateRuntime(VRBrowserActivity.this).appendAppNotesToCrashReport(aNotes));
        Log.d(LOGTAG, "appendAppNotesToCrashReport - EngineProvider removed.");
    }

    @Keep
    @SuppressWarnings("unused")
    private void updateControllerBatteryLevels(final int leftLevel, final int rightLevel) { // Kept - Device feature
        runOnUiThread(() -> updateBatteryLevels(leftLevel, rightLevel));
    }

    private void updateBatteryLevels(final int leftLevel, final int rightLevel) { // Kept - Device feature
        long currentTime = System.nanoTime();
        if (((currentTime - mLastBatteryUpdate) >= BATTERY_UPDATE_INTERVAL) || mLastBatteryLevel == -1) {
            mLastBatteryUpdate = currentTime;
            BatteryManager bm = (BatteryManager)getSystemService(BATTERY_SERVICE);
            mLastBatteryLevel = bm == null ? 100 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }

        Intent intent = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            intent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        } else {
            intent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
        int plugged = intent == null ? -1 : intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        if (mTray != null) { // Tray might be removed
             mTray.setBatteryLevels(mLastBatteryLevel, isCharging, leftLevel, rightLevel);
        }
    }

    @Keep
    @SuppressWarnings("unused")
    private void onAppFocusChanged(final boolean aIsFocused) { // REMOVED - Web session video logic
        // runOnUiThread(() -> {
            // Session session = SessionStore.get().getActiveSession();
            // if (session.getActiveVideo() == null || !session.getActiveVideo().isActive())
                // return;
            // if (aIsFocused) {
                // if (mPrevActiveMedia != null && mPrevActiveMedia == session.getActiveVideo())
                    // mPrevActiveMedia.play();
            // } else if (session.getActiveVideo().isPlaying()) {
                // mPrevActiveMedia = session.getActiveVideo();
                // mPrevActiveMedia.pause();
            // }
        // });
        Log.d(LOGTAG, "onAppFocusChanged - web video logic removed. Needs rework for ExoPlayer focus handling.");
    }

    @Keep
    @SuppressWarnings("unused")
    private void setEyeTrackingSupported(final boolean isSupported) { mIsEyeTrackingSupported = isSupported; } // Kept - device feature

    @Keep
    @SuppressWarnings("unused")
    private void setHandTrackingSupported(final boolean isSupported) { // Kept - device feature
        mIsHandTrackingSupported = isSupported;
    }

    @Keep
    @SuppressWarnings("unused")
    private void onControllersAvailable() { // Kept - device feature
        mAreControllersAvailable = true;
    }

    private SurfaceTexture createSurfaceTexture() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0]);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(LOGTAG, "OpenGL Error creating SurfaceTexture: " + error);
        }

        return new SurfaceTexture(ids[0]);
    }

    void createOffscreenDisplay() {
        final SurfaceTexture texture = createSurfaceTexture();
        runOnUiThread(() -> {
            mOffscreenDisplay = new OffscreenDisplay(VRBrowserActivity.this, texture, 16, 16);
            mOffscreenDisplay.setContentView(mWidgetContainer);
        });
    }

    void createCaptureSurface() {
        final SurfaceTexture texture = createSurfaceTexture();
        runOnUiThread(() -> {
            SettingsStore settings = SettingsStore.getInstance(this);
            texture.setDefaultBufferSize(settings.getWindowWidth(), settings.getWindowHeight());
            BitmapCache.getInstance(this).setCaptureSurface(texture);
        });
    }

    @Override
    public int newWidgetHandle() {
        return mWidgetHandleIndex++;
    }


    public void addWidgets(final Iterable<? extends Widget> aWidgets) {
        for (Widget widget : aWidgets) {
            addWidget(widget);
        }
    }

    private void updateActiveDialog(final Widget aWidget) {
        if (!aWidget.isDialog()) {
            return;
        }

        if (aWidget.isVisible()) {
            mActiveDialog = aWidget;
        } else if (aWidget == mActiveDialog && !aWidget.isVisible()) {
            mActiveDialog = null;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isWidgetInputEnabled(Widget aWidget) {
        return mActiveDialog == null || aWidget == null || mActiveDialog == aWidget || aWidget instanceof KeyboardWidget;
    }

    // WidgetManagerDelegate
    @Override
    public void addWidget(Widget aWidget) {
        if (aWidget == null) {
            return;
        }
        mWidgets.put(aWidget.getHandle(), aWidget);
        ((View)aWidget).setVisibility(aWidget.getPlacement().visible ? View.VISIBLE : View.GONE);
        final int handle = aWidget.getHandle();
        final WidgetPlacement clone = aWidget.getPlacement().clone();
        queueRunnable(() -> addWidgetNative(handle, clone));
        updateActiveDialog(aWidget);
    }

    private void enqueueUpdateWidgetNativeCall(int handle, WidgetPlacement placement) {
        mPendingNativeWidgetUpdates.put(handle, placement);

        if (mNativeWidgetUpdatesTask == null || mNativeWidgetUpdatesTask.isDone()) {
            mNativeWidgetUpdatesTask = mPendingNativeWidgetUpdatesExecutor.schedule(() -> {
                for (Map.Entry<Integer, WidgetPlacement> entry : mPendingNativeWidgetUpdates.entrySet()) {
                    queueRunnable(() -> updateWidgetNative(entry.getKey(), entry.getValue()));
                }
                mPendingNativeWidgetUpdates.clear();
            }, UPDATE_NATIVE_WIDGETS_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void updateWidget(final Widget aWidget) {
        if (aWidget == null) {
            return;
        }
        // Enqueue widget update calls in order to batch updates on the same widget. If a widget
        // updates several times in a short period of time, it's enough to call the native
        // method just once. This effectively reduces the amount of XR layer creation/destruction.
        enqueueUpdateWidgetNativeCall(aWidget.getHandle(), aWidget.getPlacement().clone());

        final int textureWidth = aWidget.getPlacement().textureWidth();
        final int textureHeight = aWidget.getPlacement().textureHeight();
        final int viewWidth = aWidget.getPlacement().viewWidth();
        final int viewHeight = aWidget.getPlacement().viewHeight();

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)((View)aWidget).getLayoutParams();
        if (params == null) {
            // Widget not added yet
            return;
        }
        UIWidget view = (UIWidget)aWidget;

        if (params.width != viewWidth || params.height != viewHeight) {
            params.width = viewWidth;
            params.height = viewHeight;
            if (view.isLayer()) {
                // Reuse last frame and do not render while resizing surface with Layers enabled.
                // Fixes resizing glitches.
                view.setResizing(true);
            }
            ((View)aWidget).setLayoutParams(params);
            aWidget.resizeSurface(textureWidth, textureHeight);
        }

        boolean visible = aWidget.getPlacement().visible;

        if (visible != (view.getVisibility() == View.VISIBLE)) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        for (UpdateListener listener: mWidgetUpdateListeners) {
            listener.onWidgetUpdate(aWidget);
        }
        updateActiveDialog(aWidget);
    }

    @Override
    public void removeWidget(final Widget aWidget) {
        if (aWidget == null) {
            return;
        }
        mWidgets.remove(aWidget.getHandle());
        mWidgetContainer.removeView((View) aWidget);
        aWidget.setFirstPaintReady(false);
        queueRunnable(() -> removeWidgetNative(aWidget.getHandle()));
        if (aWidget == mActiveDialog) {
            mActiveDialog = null;
        }
    }

    @Override
    public void updateWidgetsPlacementTranslationZ() {
        for (Widget widget: mWidgets.values()) {
            widget.getPlacement().updateCylinderMapRadius();
            widget.updatePlacementTranslationZ();
            updateWidget(widget);
        }
    }

    @Override
    public void updateVisibleWidgets() {
        queueRunnable(this::updateVisibleWidgetsNative);
    }

    @Override
    public void recreateWidgetSurface(Widget aWidget) {
        queueRunnable(() -> recreateWidgetSurfaceNative(aWidget.getHandle()));
    }

    @Override
    public void startWidgetResize(final WindowWidget aWidget) {
        if (aWidget == null) {
            return;
        }
        mWindows.enterResizeMode();
        Pair<Float, Float> maxSize = aWidget.getMaxWorldSize();
        Pair<Float, Float> minSize = aWidget.getMinWorldSize();
        queueRunnable(() -> startWidgetResizeNative(aWidget.getHandle(), maxSize.first, maxSize.second, minSize.first, minSize.second));
    }

    @Override
    public void finishWidgetResize(final WindowWidget aWidget) {
        if (aWidget == null) {
            return;
        }
        mWindows.exitResizeMode();
        queueRunnable(() -> finishWidgetResizeNative(aWidget.getHandle()));
    }

    @Override
    public void startWidgetMove(final Widget aWidget, @WidgetMoveBehaviourFlags int aMoveBehaviour) {
        if (aWidget == null) {
            return;
        }
        queueRunnable(() -> startWidgetMoveNative(aWidget.getHandle(), aMoveBehaviour));
    }

    @Override
    public void finishWidgetMove() {
        queueRunnable(this::finishWidgetMoveNative);
    }

    @Override
    public void startWindowMove() {
        SettingsStore.getInstance(this).setHeadLockEnabled(false);
        setLockMode(WidgetManagerDelegate.CONTROLLER_LOCK);
    }

    @Override
    public void finishWindowMove() {
        setLockMode(WidgetManagerDelegate.NO_LOCK);
    }

    @Override
    public void addUpdateListener(@NonNull UpdateListener aUpdateListener) {
        if (!mWidgetUpdateListeners.contains(aUpdateListener)) {
            mWidgetUpdateListeners.add(aUpdateListener);
        }
    }

    @Override
    public void removeUpdateListener(@NonNull UpdateListener aUpdateListener) {
        mWidgetUpdateListeners.remove(aUpdateListener);
    }

    @Override
    public void addPermissionListener(PermissionListener aListener) {
        if (!mPermissionListeners.contains(aListener)) {
            mPermissionListeners.add(aListener);
        }
    }

    @Override
    public void removePermissionListener(PermissionListener aListener) {
        mPermissionListeners.remove(aListener);
    }

    @Override
    public void addFocusChangeListener(@NonNull FocusChangeListener aListener) {
        if (!mFocusChangeListeners.contains(aListener)) {
            mFocusChangeListeners.add(aListener);
        }
    }

    @Override
    public void removeFocusChangeListener(@NonNull FocusChangeListener aListener) {
        mFocusChangeListeners.remove(aListener);
    }


    @Override
    public void addWorldClickListener(WorldClickListener aListener) {
        if (!mWorldClickListeners.contains(aListener)) {
            mWorldClickListeners.add(aListener);
        }
    }

    @Override
    public void removeWorldClickListener(WorldClickListener aListener) {
        mWorldClickListeners.remove(aListener);
    }

    @Override
    public void addWebXRListener(WebXRListener aListener) {
        mWebXRListeners.add(aListener);
    }

    @Override
    public void removeWebXRListener(WebXRListener aListener) {
        mWebXRListeners.remove(aListener);
    }

    @Override
    public void setWebXRIntersitialState(@WebXRInterstitialState int aState) {
        queueRunnable(() -> setWebXRIntersitialStateNative(aState));
    }

    @Override
    public boolean isWebXRIntersitialHidden() {
        return mHideWebXRIntersitial;
    }

    @Override
    public boolean isWebXRPresenting() {
        return mIsPresentingImmersive.getValue();
    }

    @Override
    public boolean isLaunchImmersive() {
        // This method may be called before we have parsed the current Intent.
        return mLaunchImmersive ||
                (getIntent() != null && getIntent().getBooleanExtra(EXTRA_LAUNCH_IMMERSIVE, false)
                        && getIntent().getStringExtra(EXTRA_LAUNCH_IMMERSIVE_ELEMENT_XPATH) != null);
    }

    @Override
    public void pushBackHandler(@NonNull Runnable aRunnable) {
        mBackHandlers.addLast(aRunnable);
    }

    @Override
    public void popBackHandler(@NonNull Runnable aRunnable) {
        mBackHandlers.removeLastOccurrence(aRunnable);
    }

    @Override
    public void pushWorldBrightness(Object aKey, float aBrightness) {
        if (mCurrentBrightness.second != aBrightness) {
            queueRunnable(() -> setWorldBrightnessNative(aBrightness));
        }
        mBrightnessQueue.add(mCurrentBrightness);
        mCurrentBrightness = Pair.create(aKey, aBrightness);
    }

    @Override
    public void setWorldBrightness(Object aKey, final float aBrightness) {
        if (mCurrentBrightness.first == aKey) {
            if (mCurrentBrightness.second != aBrightness) {
                mCurrentBrightness = Pair.create(aKey, aBrightness);
                queueRunnable(() -> setWorldBrightnessNative(aBrightness));
            }
        } else {
            for (int i = mBrightnessQueue.size() - 1; i >= 0; --i) {
                if (mBrightnessQueue.get(i).first == aKey) {
                    mBrightnessQueue.set(i, Pair.create(aKey, aBrightness));
                    break;
                }
            }
        }
    }

    @Override
    public void popWorldBrightness(Object aKey) {
        if (mBrightnessQueue.size() == 0) {
            return;
        }
        if (mCurrentBrightness.first == aKey) {
            float brightness = mCurrentBrightness.second;
            mCurrentBrightness = mBrightnessQueue.removeLast();
            if (mCurrentBrightness.second != brightness) {
                queueRunnable(() -> setWorldBrightnessNative(mCurrentBrightness.second));
            }

            return;
        }
        for (int i = mBrightnessQueue.size() - 1; i >= 0; --i) {
            if (mBrightnessQueue.get(i).first == aKey) {
                mBrightnessQueue.remove(i);
                break;
            }
        }
    }

    @Override
    public void triggerHapticFeedback(int controllerId) {
        SettingsStore settings = SettingsStore.getInstance(this);
        if (settings.isHapticFeedbackEnabled()) {
            queueRunnable(() -> triggerHapticFeedbackNative(settings.getHapticPulseDuration(), settings.getHapticPulseIntensity(), controllerId));
        }
    }

    @Override
    public void setControllersVisible(final boolean aVisible) {
        queueRunnable(() -> setControllersVisibleNative(aVisible));
    }

    @Override
    public void keyboardDismissed() {
        mNavigationBar.showVoiceSearch();
    }

    @Override
    public void updateEnvironment() {
        queueRunnable(this::updateEnvironmentNative);
    }

    @Override
    public void updateKeyboardDictionary() {
        mKeyboard.updateDictionary();
    }

    @Override
    public void updatePointerColor() {
        queueRunnable(this::updatePointerColorNative);
    }

    @Override
    public boolean isPermissionGranted(@NonNull String permission) {
        return mPermissionDelegate.isPermissionGranted(permission);
    }

    @Override
    public void requestPermission(String originator, @NonNull String permission, OriginatorType originatorType, WSession.PermissionDelegate.Callback aCallback) {
        Session session = SessionStore.get().getActiveSession();
        if (originatorType == OriginatorType.WEBSITE) {
            mPermissionDelegate.onWebsitePermissionRequest(session.getWSession(), originator, permission, aCallback);
        } else {
            mPermissionDelegate.onAndroidPermissionsRequest(session.getWSession(), new String[]{permission}, aCallback);
        }
    }

    @Override
    @Deprecated
    public void onRequestPermissionsResult(int requestCode, @NonNull  String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        runOnUiThread(() -> {
            for (PermissionListener listener : mPermissionListeners) {
                listener.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        });
    }

    @Override
    public void showVRVideo(final int aWindowHandle, final @VideoProjectionMenuWidget.VideoProjectionFlags int aVideoProjection) {
        if (mSettings.isHeadLockEnabled()) {
            mSettings.setHeadLockEnabled(false);
            shouldRestoreHeadLockOnVRVideoExit = true;
        }
        queueRunnable(() -> showVRVideoNative(aWindowHandle, aVideoProjection));
    }

    @Override
    public void hideVRVideo() {
        queueRunnable(this::hideVRVideoNative);

        if (shouldRestoreHeadLockOnVRVideoExit) {
            mSettings.setHeadLockEnabled(true);
        }
    }

    @Override
    public void togglePassthrough() {
        mIsPassthroughEnabled = !mIsPassthroughEnabled;
        queueRunnable(() -> togglePassthroughNative());
    }

    @Override
    public boolean isPassthroughEnabled() {
        return mIsPassthroughEnabled;
    }
    @Override
    public boolean isPassthroughSupported() {
        return DeviceType.isOculusBuild() || DeviceType.isLynx() || DeviceType.isSnapdragonSpaces() || DeviceType.isPicoXR();
    }
    @Override
    public boolean areControllersAvailable() {
        return mAreControllersAvailable;
    }

    @Override
    public boolean isPageZoomEnabled() {
        return BuildConfig.ENABLE_PAGE_ZOOM;
    }

    @Override
    public void setLockMode(@LockMode int lockMode) {
        queueRunnable(() -> setLockEnabledNative(lockMode));
    }

    @Override
    public void recenterUIYaw(@YawTarget int aTarget) {
        queueRunnable(() -> recenterUIYawNative(aTarget));
    }

    @Override
    public void setCylinderDensity(final float aDensity) {
        if (mWindows != null && aDensity == 0.0f && mWindows.getWindowsCount() > 1) {
            return;
        }
        setCylinderDensityForce(aDensity);
    }

    @Override
    public void setCylinderDensityForce(final float aDensity) {
        mCurrentCylinderDensity = aDensity;
        queueRunnable(() -> setCylinderDensityNative(aDensity));
        if (mWindows != null) {
            mWindows.updateCurvedMode(false);
        }
    }

    @Override
    public void setCenterWindows(boolean isCenterWindows) {
        if (mWindows != null) {
            mWindows.setCenterWindows(isCenterWindows);
            updateVisibleWidgets();
        }
    }

    @Override
    public float getCylinderDensity() {
        return mCurrentCylinderDensity;
    }

    @Override
    public boolean canOpenNewWindow() {
        return mWindows.canOpenNewWindow();
    }

    @Override
    public void openNewWindow(String uri) {
        WindowWidget newWindow = mWindows.addWindow();
        if ((newWindow != null) && (newWindow.getSession() != null)) {
            newWindow.getSession().loadUri(uri);
        }
    }

    @Override
    public void openNewTab(@NonNull String uri) {
        mWindows.addBackgroundTab(mWindows.getFocusedWindow(), uri);
    }

    @Override
    public void openNewTabForeground(@NonNull String uri) {
        mWindows.addTab(mWindows.getFocusedWindow(), uri);
    }

    private boolean openNewTabNoInterrupt(@NonNull WindowWidget window, @NonNull String uri) {
        if (window.getSession() == null || window.getSession().getActiveVideo() != null) {
            return false;
        }

        mWindows.addTab(window, uri);
        mWindows.focusWindow(window);
        return true;
    }
    @Override
    public void openNewPageNoInterrupt(@NonNull String uri) {
        if (openNewTabNoInterrupt(mWindows.getFocusedWindow(), uri)) { return; }

        // If we have video playing in current window, ensure we don't open a new tab
        // in a window that has active video
        if (mWindows.getWindowsCount() > 1) {
            for (WindowWidget window : mWindows.getCurrentWindows()) {
                if (openNewTabNoInterrupt(window, uri)) { return; }
            }
        }
        // All the current opened Windows have video playing, so we have to open uri in a new window.
        // If we have maximum window number, then open the uri as a new tab in current window.
        if (canOpenNewWindow()) {
            openNewWindow(uri);
        } else {
            openNewTabForeground(uri);
        }
    }

    @Override
    public WindowWidget getFocusedWindow() {
        return mWindows.getFocusedWindow();
    }

    @Override
    public TrayWidget getTray() {
        return mTray;
    }

    @Override
    public NavigationBarWidget getNavigationBar() {
        return mNavigationBar;
    }

    @Override
    public Windows getWindows() {
        return mWindows;
    }

    @Override
    public void saveState() {
        mWindows.saveState();
    }

    @Override
    public void updateLocale(@NonNull Context context) {
        onConfigurationChanged(context.getResources().getConfiguration());
        getApplication().onConfigurationChanged(context.getResources().getConfiguration());
    }

    @Override
    public void setPointerMode(@PointerMode int mode) {
        queueRunnable(() -> setPointerModeNative(mode));
    }

    @Override
    public void setHandTrackingEnabled(boolean value) {
        mIsHandTrackingEnabled = value;
        queueRunnable(() -> setHandTrackingEnabledNative(value));
    }

    @Override
    public boolean isHandTrackingEnabled() {
        return mIsHandTrackingEnabled;
    }

    @Override
    public boolean isHandTrackingSupported() {
        return mIsHandTrackingSupported;
    }

    @Override
    @NonNull
    public AppServicesProvider getServicesProvider() {
        return (AppServicesProvider)getApplication();
    }

    @Override
    public KeyboardWidget getKeyboard() { return mKeyboard; }

    @Override
    public void onPlatformScrollEvent(float distanceX, float distanceY) {
        float SCROLL_SCALE = 32;
        handleScrollEvent(mLastMotionEventWidgetHandle, 0, distanceX / SCROLL_SCALE, distanceY / SCROLL_SCALE);
    }

    @Override
    public void checkEyeTrackingPermissions(@NonNull EyeTrackingCallback callback) {
        if (isPermissionGranted(getEyeTrackingPermissionString())) {
            callback.onEyeTrackingPermissionRequest(true);
            return;
        }

        PromptDialogWidget dialog = new PromptDialogWidget(this);
        dialog.setTitle(R.string.eye_tracking_permission_title);
        dialog.setDescription(R.string.eye_tracking_permission_message);
        dialog.setButtons(new int[] {R.string.ok_button});
        dialog.setCheckboxVisible(false);
        dialog.setIcon(R.drawable.mozac_ic_warning_fill_24);
        dialog.setButtonsDelegate((index, isChecked) -> {
            dialog.hide(UIWidget.REMOVE_WIDGET);
            dialog.releaseWidget();
            requestPermission(null, getEyeTrackingPermissionString(), OriginatorType.APPLICATION, new WSession.PermissionDelegate.Callback() {
                @Override
                public void grant() {
                    callback.onEyeTrackingPermissionRequest(true);
                }

                @Override
                public void reject() {
                    callback.onEyeTrackingPermissionRequest(false);
                }
            });
        });
        dialog.show(UIWidget.REQUEST_FOCUS);
    }

    @Override
    public boolean isEyeTrackingSupported() { return mIsEyeTrackingSupported; }

    @Keep
    @SuppressWarnings("unused")
    private void changeWindowDistance(float aDelta) {
        float increment = 0.05f;
        float clamped = Math.max(0.0f, Math.min(mSettings.getWindowDistance() + (aDelta > 0 ? increment : -increment), 1.0f));
        mSettings.setWindowDistance(clamped);
    }

    private native void addWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void updateWidgetNative(int aHandle, WidgetPlacement aPlacement);
    private native void updateVisibleWidgetsNative();
    private native void removeWidgetNative(int aHandle);
    private native void recreateWidgetSurfaceNative(int aHandle);
    private native void startWidgetResizeNative(int aHandle, float maxWidth, float maxHeight, float minWidth, float minHeight);
    private native void finishWidgetResizeNative(int aHandle);
    private native void startWidgetMoveNative(int aHandle, int aMoveBehaviour);
    private native void finishWidgetMoveNative();
    private native void setWorldBrightnessNative(float aBrightness);
    private native void triggerHapticFeedbackNative(float aPulseDuration, float aPulseIntensity, int aControllerId);
    private native void setTemporaryFilePath(String aPath);
    private native void exitImmersiveNative();
    private native void workaroundGeckoSigAction();
    private native void updateEnvironmentNative();
    private native void updatePointerColorNative();
    private native void showVRVideoNative(int aWindowHandler, int aVideoProjection);
    private native void hideVRVideoNative();
    private native void togglePassthroughNative();
    private native void setLockEnabledNative(@LockMode int aLockMode);
    private native void recenterUIYawNative(@YawTarget int aTarget);
    private native void setControllersVisibleNative(boolean aVisible);
    private native void runCallbackNative(long aCallback);
    private native void deleteCallbackNative(long aCallback);
    private native void setCylinderDensityNative(float aDensity);
    private native void setCPULevelNative(@CPULevelFlags int aCPULevel);
    private native void setWebXRIntersitialStateNative(@WebXRInterstitialState int aState);
    private native void setIsServo(boolean aIsServo);
    private native void setPointerModeNative(@PointerMode int aMode);
    private native void setHandTrackingEnabledNative(boolean value);

}
