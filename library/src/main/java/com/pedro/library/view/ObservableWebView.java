/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.library.view;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

/**
 * A WebView subclass that exposes a dirty-detection callback via invalidate().
 *
 * WebView renders through its own compositor thread and does not reliably
 * trigger
 * ViewTreeObserver.OnDrawListener when used off-screen (e.g. drawn to a
 * Surface).
 * Instead, the compositor calls invalidate() on the View whenever a new frame
 * is
 * ready. By overriding invalidate() here we get a precise, zero-overhead signal
 * that the WebView content has changed and needs to be redrawn onto the
 * Surface.
 *
 * Usage:
 * ObservableWebView webView = new ObservableWebView(context);
 * androidViewFilterRender.setView(webView);
 * // AndroidViewFilterRender detects ObservableWebView automatically and
 * // registers the dirty listener via setOnInvalidateListener().
 */
public class ObservableWebView extends WebView {

    /**
     * Called from the compositor thread whenever WebView has new pixels to show.
     * Must be volatile so writes from the compositor thread are visible to the
     * render thread immediately.
     */
    private volatile Runnable onInvalidateListener;

    public ObservableWebView(Context context) {
        super(context);
    }

    public ObservableWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ObservableWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Set a listener that will be called every time WebView invalidates itself.
     * The listener runs on whatever thread called invalidate() — typically the
     * WebView compositor thread — so keep it fast and thread-safe.
     */
    public void setOnInvalidateListener(Runnable listener) {
        this.onInvalidateListener = listener;
    }

    /**
     * Remove the invalidate listener. Call this before releasing the view to
     * avoid callbacks after the render pipeline has been torn down.
     */
    public void clearOnInvalidateListener() {
        this.onInvalidateListener = null;
    }

    /**
     * Overrides the single non-deprecated invalidate() entry point.
     * On API 28+, all partial-invalidation overloads delegate here internally,
     * so one override is sufficient to catch every invalidation.
     */
    @Override
    public void invalidate() {
        super.invalidate();
        // Local copy guards against the listener being cleared on another thread
        // between our null-check and our call.
        Runnable listener = onInvalidateListener;
        if (listener != null)
            listener.run();
    }
}