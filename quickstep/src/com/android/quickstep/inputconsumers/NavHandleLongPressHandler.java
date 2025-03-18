/*
 * Copyright (C) 2023 The Android Open Source Project
 *               2023-2024 The risingOS Android Project
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
package com.android.quickstep.inputconsumers;

import static android.app.contextualsearch.ContextualSearchManager.ENTRYPOINT_LONG_PRESS_NAV_HANDLE;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_ASSISTANT_SUCCESSFUL_NAV_HANDLE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OMNI_GET_LONG_PRESS_RUNNABLE;
import static com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_OMNI_RUNNABLE;
import static android.os.VibrationEffect.createPredefined;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.contextualsearch.ContextualSearchManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.R;
import com.android.quickstep.util.ImageActionUtils;
import com.android.launcher3.util.VibrationUtils;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.Utilities;
import com.android.quickstep.NavHandle;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.util.AssistUtils;

import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.List;
import java.util.concurrent.TimeUnit;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.DeviceConfigWrapper;
import com.android.quickstep.NavHandle;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.util.ContextualSearchHapticManager;
import com.android.quickstep.util.ContextualSearchInvoker;
import com.android.quickstep.util.ContextualSearchStateManager;

/**
 * Class for extending nav handle long press behavior
 */
public class NavHandleLongPressHandler {

    private static final VibrationEffect EFFECT_HEAVY_CLICK =
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);
    private static final VibrationEffect EFFECT_TICK =
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);

    private static final Handler mHandler = new Handler(Looper.getMainLooper());

    public NavHandleLongPressHandler(Context context) {
        mContext = context;
        mAssistUtils = new AssistUtils(mContext);
    }

    private final String TAG = "NavHandleLongPressHandler";
    private boolean DEBUG = false;

    private ThumbnailData mThumbnailData;
    private TopTaskTracker mTopTaskTracker;
    private Context mContext;
    private AssistUtils mAssistUtils;

    private static final String TAG = "NavHandleLongPressHandler";

    protected final Context mContext;
    protected final VibratorWrapper mVibratorWrapper;
    protected final ContextualSearchHapticManager mContextualSearchHapticManager;
    protected final ContextualSearchInvoker mContextualSearchInvoker;
    protected final StatsLogManager mStatsLogManager;
    private boolean mPendingInvocation;

    public NavHandleLongPressHandler(Context context) {
        mContext = context;
        mStatsLogManager = StatsLogManager.newInstance(context);
        mVibratorWrapper = VibratorWrapper.INSTANCE.get(mContext);
        mContextualSearchHapticManager = ContextualSearchHapticManager.INSTANCE.get(context);
        mContextualSearchInvoker = new ContextualSearchInvoker(mContext);
    }

    /** Creates NavHandleLongPressHandler as specified by overrides */
    public NavHandleLongPressHandler(Context context, TopTaskTracker topTaskTracker) {
        mContext = context;
        mTopTaskTracker = topTaskTracker;
	mAssistUtils = new AssistUtils(mContext);
    }

    protected boolean isContextualSearchEntrypointEnabled(NavHandle navHandle) {
        return DeviceConfigWrapper.get().getEnableLongPressNavHandle();
    }

    /**
     * Called when nav handle is long pressed to get the Runnable that should be executed by the
     * caller to invoke long press behavior. If null is returned that means long press couldn't be
     * handled.
     * <p>
     * A Runnable is returned here to ensure the InputConsumer can call
     * {@link android.view.InputMonitor#pilferPointers()} before invoking the long press behavior
     * since pilfering can break the long press behavior.
     *
     * @param navHandle to handle this long press
     */
    public @Nullable Runnable getLongPressRunnable(NavHandle navHandle) {
	    if (!Utilities.isGSAEnabled(mContext) ||
            !Utilities.isLongPressToSearchEnabled(mContext)) {
            return null;
        }

        VibrationUtils.triggerVibration(mContext, 5);
        navHandle.animateNavBarLongPress(true, true, 200L);

        // CTS
        if (mAssistUtils.canDoContextualSearch()) {
            return new Runnable() {
                @Override
                public final void run() {
                    mHandler.postDelayed(() -> {
                        if (mAssistUtils.invokeContextualSearch(
                                ContextualSearchManager.ENTRYPOINT_LONG_PRESS_NAV_HANDLE)) {
                            VibratorWrapper.INSTANCE.get(mContext).vibrate(EFFECT_HEAVY_CLICK);
                        }
                    }, ViewConfiguration.getLongPressTimeout());
                }
            };
        }

        // Lens
        updateThumbnail();
        if (mThumbnailData != null && mThumbnailData.getThumbnail() != null) {
            if (DEBUG) Log.d(TAG, "getLongPressRunnable: Google lens should start now");
            ImageActionUtils.startLensActivity(mContext, mThumbnailData.getThumbnail(), null, TAG);
        } else {
            if (DEBUG) Log.d(TAG, "getLongPressRunnable: thumbnail is null");
        }
        return null;
    }

    @Nullable
    @VisibleForTesting
    final Runnable getLongPressRunnable(NavHandle navHandle) {
        if (!isContextualSearchEntrypointEnabled(navHandle)) {
            Log.i(TAG, "Contextual Search invocation failed: entry point disabled");
            mVibratorWrapper.cancelVibrate();
            return null;
        }

        if (!mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures()) {
            Log.i(TAG, "Contextual Search invocation failed: precondition not satisfied");
            mVibratorWrapper.cancelVibrate();
            return null;
        }

        mPendingInvocation = true;
        Log.i(TAG, "Contextual Search invocation: invocation runnable created");
        InstanceId instanceId = new InstanceIdSequence().newInstanceId();
        mStatsLogManager.logger().withInstanceId(instanceId).log(
                LAUNCHER_OMNI_GET_LONG_PRESS_RUNNABLE);
        long startTimeMillis = SystemClock.elapsedRealtime();
        return () -> {
            mStatsLogManager.latencyLogger().withInstanceId(instanceId).withLatency(
                    SystemClock.elapsedRealtime() - startTimeMillis).log(
                    LAUNCHER_LATENCY_OMNI_RUNNABLE);
            if (mContextualSearchInvoker.invokeContextualSearchUncheckedWithHaptic(
                    ENTRYPOINT_LONG_PRESS_NAV_HANDLE)) {
                Log.i(TAG, "Contextual Search invocation successful");

                String runningPackage = TopTaskTracker.INSTANCE.get(mContext).getCachedTopTask(
                        /* filterOnlyVisibleRecents */ true).getPackageName();
                mStatsLogManager.logger().withPackageName(runningPackage)
                        .log(LAUNCHER_LAUNCH_ASSISTANT_SUCCESSFUL_NAV_HANDLE);
            } else {
                mVibratorWrapper.cancelVibrate();
                if (DeviceConfigWrapper.get().getAnimateLpnh()
                        && !DeviceConfigWrapper.get().getShrinkNavHandleOnPress()) {
                    navHandle.animateNavBarLongPress(
                            /*isTouchDown*/false, /*shrink*/ false, /*durationMs*/160);
                }
            }
        };
    }

    /**
     * Called when nav handle gesture starts.
     *
     * @param navHandle to handle the animation for this touch
     */
    @VisibleForTesting
    final void onTouchStarted(NavHandle navHandle) {
        mPendingInvocation = false;
        if (isContextualSearchEntrypointEnabled(navHandle)
                && mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures()) {
            Log.i(TAG, "Contextual Search invocation: touch started");
            startNavBarAnimation(navHandle);
        }
        updateThumbnail();
    }

    private void updateThumbnail() {
	if (!Utilities.isGSAEnabled(mContext)) {
            return;
        }
        String runningPackage = mTopTaskTracker.getCachedTopTask(
                /* filterOnlyVisibleRecents */ true).getPackageName();
        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            List<RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);
            for (RunningTaskInfo task : tasks) {
                if (task.topActivity.getPackageName().equals(runningPackage)) {
                    int taskId = task.id;
                    mThumbnailData = ActivityManagerWrapper.getInstance().takeTaskThumbnail(taskId);
                    break;
                }
            }
        }
        if (DEBUG) Log.d(TAG, "updateThumbnail running, runningPackage: " + runningPackage);
    }

    /**
     * Called when nav handle gesture is finished by the user lifting their finger or the system
     * cancelling the touch for some other reason.
     *
     * @param navHandle to handle the animation for this touch
     * @param reason why the touch ended
     */
    @VisibleForTesting
    final void onTouchFinished(NavHandle navHandle, String reason) {
        Log.i(TAG, "Contextual Search invocation: touch finished with reason: " + reason);

        if (!DeviceConfigWrapper.get().getShrinkNavHandleOnPress() || !mPendingInvocation) {
            mVibratorWrapper.cancelVibrate();
        }

        if (DeviceConfigWrapper.get().getAnimateLpnh()) {
            if (DeviceConfigWrapper.get().getShrinkNavHandleOnPress()) {
                navHandle.animateNavBarLongPress(
                        /*isTouchDown*/false, /*shrink*/ true, /*durationMs*/200);
            } else {
                navHandle.animateNavBarLongPress(
                        /*isTouchDown*/false, /*shrink*/ false, /*durationMs*/ 160);
            }
        }
    }

    @VisibleForTesting
    final void startNavBarAnimation(NavHandle navHandle) {
        mContextualSearchHapticManager.vibrateForSearchHint();

        if (DeviceConfigWrapper.get().getAnimateLpnh()) {
            if (DeviceConfigWrapper.get().getShrinkNavHandleOnPress()) {
                navHandle.animateNavBarLongPress(
                        /*isTouchDown*/ true, /*shrink*/true, /*durationMs*/200);
            } else {
                long longPressTimeout;
                ContextualSearchStateManager contextualSearchStateManager =
                        ContextualSearchStateManager.INSTANCE.get(mContext);
                if (contextualSearchStateManager.getLPNHDurationMillis().isPresent()) {
                    longPressTimeout =
                            contextualSearchStateManager.getLPNHDurationMillis().get().intValue();
                } else {
                    longPressTimeout = ViewConfiguration.getLongPressTimeout();
                }
                navHandle.animateNavBarLongPress(
                        /*isTouchDown*/ true, /*shrink*/ false, /*durationMs*/ longPressTimeout);
            }
        }
    }
}
