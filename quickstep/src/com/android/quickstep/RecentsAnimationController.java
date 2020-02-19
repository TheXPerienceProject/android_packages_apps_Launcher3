/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.UiThread;

import com.android.launcher3.util.Preconditions;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wrapper around RecentsAnimationController to help with some synchronization
 */
public class RecentsAnimationController {

    private static final String TAG = "RecentsAnimationController";

    private final RecentsAnimationControllerCompat mController;
    private final Consumer<RecentsAnimationController> mOnFinishedListener;
    private final boolean mShouldMinimizeSplitScreen;

    private InputConsumerController mInputConsumerController;
    private Supplier<InputConsumer> mInputProxySupplier;
    private InputConsumer mInputConsumer;
    private boolean mWindowThresholdCrossed = false;
    private boolean mTouchInProgress;
    private boolean mFinishPending;

    public RecentsAnimationController(RecentsAnimationControllerCompat controller,
            boolean shouldMinimizeSplitScreen,
            Consumer<RecentsAnimationController> onFinishedListener) {
        mController = controller;
        mOnFinishedListener = onFinishedListener;
        mShouldMinimizeSplitScreen = shouldMinimizeSplitScreen;
    }

    /**
     * Synchronously takes a screenshot of the task with the given {@param taskId} if the task is
     * currently being animated.
     */
    public ThumbnailData screenshotTask(int taskId) {
        return mController.screenshotTask(taskId);
    }

    /**
     * Indicates that the gesture has crossed the window boundary threshold and system UI can be
     * update the represent the window behind
     */
    public void setWindowThresholdCrossed(boolean windowThresholdCrossed) {
        if (mWindowThresholdCrossed != windowThresholdCrossed) {
            mWindowThresholdCrossed = windowThresholdCrossed;
            UI_HELPER_EXECUTOR.execute(() -> {
                mController.setAnimationTargetsBehindSystemBars(!windowThresholdCrossed);
                SystemUiProxy p = SystemUiProxy.INSTANCE.getNoCreate();
                if (p != null && mShouldMinimizeSplitScreen) {
                    p.setSplitScreenMinimized(windowThresholdCrossed);
                }
            });
        }
    }

    /**
     * Notifies the controller that we want to defer cancel until the next app transition starts.
     * If {@param screenshot} is set, then we will receive a screenshot on the next
     * {@link RecentsAnimationCallbacks#onAnimationCanceled(ThumbnailData)} and we must also call
     * {@link #cleanupScreenshot()} when that screenshot is no longer used.
     */
    public void setDeferCancelUntilNextTransition(boolean defer, boolean screenshot) {
        mController.setDeferCancelUntilNextTransition(defer, screenshot);
    }

    /**
     * Cleans up the screenshot previously returned from
     * {@link RecentsAnimationCallbacks#onAnimationCanceled(ThumbnailData)}.
     */
    public void cleanupScreenshot() {
        UI_HELPER_EXECUTOR.execute(() -> mController.cleanupScreenshot());
    }

    @UiThread
    public void finishAnimationToHome() {
        finishAndClear(true /* toRecents */, null, false /* sendUserLeaveHint */);
    }

    @UiThread
    public void finishAnimationToApp() {
        finishAndClear(false /* toRecents */, null, false /* sendUserLeaveHint */);
    }

    /** See {@link #finish(boolean, Runnable, boolean)} */
    @UiThread
    public void finish(boolean toRecents, Runnable onFinishComplete) {
        finish(toRecents, onFinishComplete, false /* sendUserLeaveHint */);
    }

    /**
     * @param onFinishComplete A callback that runs on the main thread after the animation
     *                         controller has finished on the background thread.
     * @param sendUserLeaveHint Determines whether userLeaveHint flag will be set on the pausing
     *                          activity. If userLeaveHint is true, the activity will enter into
     *                          picture-in-picture mode upon being paused.
     */
    @UiThread
    public void finish(boolean toRecents, Runnable onFinishComplete, boolean sendUserLeaveHint) {
        Preconditions.assertUIThread();
        if (!toRecents) {
            finishAndClear(false, onFinishComplete, sendUserLeaveHint);
        } else {
            if (mTouchInProgress) {
                mFinishPending = true;
                // Execute the callback
                if (onFinishComplete != null) {
                    onFinishComplete.run();
                }
            } else {
                finishAndClear(true, onFinishComplete, sendUserLeaveHint);
            }
        }
    }

    private void finishAndClear(boolean toRecents, Runnable onFinishComplete,
            boolean sendUserLeaveHint) {
        disableInputProxy();
        finishController(toRecents, onFinishComplete, sendUserLeaveHint);
    }

    @UiThread
    public void finishController(boolean toRecents, Runnable callback, boolean sendUserLeaveHint) {
        mOnFinishedListener.accept(this);
        UI_HELPER_EXECUTOR.execute(() -> {
            mController.setInputConsumerEnabled(false);
            mController.finish(toRecents, sendUserLeaveHint);
            if (callback != null) {
                MAIN_EXECUTOR.execute(callback);
            }
        });
    }

    /**
     * Enables the input consumer to start intercepting touches in the app window.
     */
    public void enableInputConsumer() {
        UI_HELPER_EXECUTOR.submit(() -> {
            mController.hideCurrentInputMethod();
            mController.setInputConsumerEnabled(true);
        });
    }

    public void enableInputProxy(InputConsumerController inputConsumerController,
            Supplier<InputConsumer> inputProxySupplier) {
        mInputProxySupplier = inputProxySupplier;
        mInputConsumerController = inputConsumerController;
        mInputConsumerController.setInputListener(this::onInputConsumerEvent);
    }

    /** @return wrapper controller. */
    public RecentsAnimationControllerCompat getController() {
        return mController;
    }

    private void disableInputProxy() {
        if (mInputConsumer != null && mTouchInProgress) {
            long now = SystemClock.uptimeMillis();
            MotionEvent dummyCancel = MotionEvent.obtain(now,  now, ACTION_CANCEL, 0, 0, 0);
            mInputConsumer.onMotionEvent(dummyCancel);
            dummyCancel.recycle();
        }
        if (mInputConsumerController != null) {
            mInputConsumerController.setInputListener(null);
        }
        mInputProxySupplier = null;
    }

    private boolean onInputConsumerEvent(InputEvent ev) {
        if (ev instanceof MotionEvent) {
            onInputConsumerMotionEvent((MotionEvent) ev);
        } else if (ev instanceof KeyEvent) {
            if (mInputConsumer == null) {
                mInputConsumer = mInputProxySupplier.get();
            }
            mInputConsumer.onKeyEvent((KeyEvent) ev);
            return true;
        }
        return false;
    }

    private boolean onInputConsumerMotionEvent(MotionEvent ev) {
        int action = ev.getAction();

        // Just to be safe, verify that ACTION_DOWN comes before any other action,
        // and ignore any ACTION_DOWN after the first one (though that should not happen).
        if (!mTouchInProgress && action != ACTION_DOWN) {
            Log.w(TAG, "Received non-down motion before down motion: " + action);
            return false;
        }
        if (mTouchInProgress && action == ACTION_DOWN) {
            Log.w(TAG, "Received down motion while touch was already in progress");
            return false;
        }

        if (action == ACTION_DOWN) {
            mTouchInProgress = true;
            if (mInputConsumer == null) {
                mInputConsumer = mInputProxySupplier.get();
            }
        } else if (action == ACTION_CANCEL || action == ACTION_UP) {
            // Finish any pending actions
            mTouchInProgress = false;
            if (mFinishPending) {
                mFinishPending = false;
                finishAndClear(true /* toRecents */, null, false /* sendUserLeaveHint */);
            }
        }
        if (mInputConsumer != null) {
            mInputConsumer.onMotionEvent(ev);
        }

        return true;
    }
}
