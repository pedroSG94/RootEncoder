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

package com.pedro.encoder.input.gl.render.filters;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.R;
import com.pedro.encoder.input.gl.AndroidViewSprite;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.TranslateTo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by pedro on 4/02/18.
 *
 * Renders any Android View onto an OpenGL texture that can be composited with
 * a camera feed. Supports two dirty-detection strategies:
 *
 * - Normal Views: ViewTreeObserver.OnDrawListener fires on the main thread
 * before each draw pass, setting the dirty flag.
 *
 * - ObservableWebView: invalidate() is overridden to set the dirty flag
 * directly from the compositor thread, which is more reliable for off-screen
 * WebView rendering where ViewTreeObserver does not fire.
 *
 * When dirty is false the render thread sleeps and draws nothing — effectively
 * 0 FPS when the view is static. When content changes, it draws up to
 * TARGET_FPS.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AndroidViewFilterRender extends BaseFilterRender {

    private static final String TAG = "AndroidViewFilterRender";
    private static final int TARGET_FPS = 30;
    private static final long FRAME_INTERVAL_MS = 1000L / TARGET_FPS;

    // Vertex data: X, Y, Z, U, V
    private final float[] squareVertexDataFilter = {
      // X, Y, Z, U, V
      -1f, -1f, 0f, 0f, 0f, //bottom left
      1f, -1f, 0f, 1f, 0f, //bottom right
      -1f, 1f, 0f, 0f, 1f, //top left
      1f, 1f, 0f, 1f, 1f, //top right
    };

    private int program = -1;
    private int aPositionHandle = -1;
    private int aTextureHandle = -1;
    private int uMVPMatrixHandle = -1;
    private int uSTMatrixHandle = -1;
    private int uSamplerHandle = -1;
    private int uSamplerViewHandle = -1;
    private int uDrawViewHandle = -1;

    private int[] viewId = new int[] { -1, -1 };
    private View view;

    // Two surfaces so the render thread never blocks the GL thread
    private SurfaceTexture surfaceTexture, surfaceTexture2;
    private Surface surface, surface2;

    private final Handler mainHandler;
    private volatile boolean running = false;
    private ExecutorService thread = null;
    private boolean hardwareMode = true;
    private final AndroidViewSprite sprite;

    private volatile Status renderingStatus = Status.DONE1;

    // Each startRender() increments this. Threads capture their own value and
    // self-terminate when the global value no longer matches.
    private volatile int renderGeneration = 0;

    // Tracked so stopRender() can cancel only our own callback, not unrelated ones
    private volatile Runnable pendingMainThreadDraw = null;

    // Track current preview dimensions to avoid redundant GL updates
    private int currentWidth = -1;
    private int currentHeight = -1;

    // When false, the render thread stays alive but skips all drawing
    private boolean render = true;

    // Dirty flag: true means the view has new content and needs a frame drawn.
    // Initialised to true so the very first frame always renders.
    private volatile boolean dirty = true;

    // Fired by ViewTreeObserver for non-WebView views
    private final ViewTreeObserver.OnDrawListener onDrawListener = () -> dirty = true;

    private enum Status {
        RENDER1, RENDER2, DONE1, DONE2
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public AndroidViewFilterRender() {
        this(true);
    }

    /**
     * @param render if false, drawing is suppressed until setRender(true) is
     *               called.
     *               The render thread is still started so switching back is
     *               instant.
     */
    public AndroidViewFilterRender(boolean render) {
        this.render = render;
        squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        squareVertex.put(squareVertexDataFilter).position(0);
        Matrix.setIdentityM(MVPMatrix, 0);
        Matrix.setIdentityM(STMatrix, 0);
        sprite = new AndroidViewSprite();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void initGlFilter(Context context) {
        String vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex);
        String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.android_view_fragment);

        program = GlUtil.createProgram(vertexShader, fragmentShader);
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
        uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler");
        uSamplerViewHandle = GLES20.glGetUniformLocation(program, "uSamplerView");
        uDrawViewHandle = GLES20.glGetUniformLocation(program, "uDrawView");

        GlUtil.createExternalTextures(viewId.length, viewId, 0);
        surfaceTexture = new SurfaceTexture(viewId[0]);
        surfaceTexture2 = new SurfaceTexture(viewId[1]);
        surface = new Surface(surfaceTexture);
        surface2 = new Surface(surfaceTexture2);
    }

    @Override
    protected void drawFilter() {
        final Status status = renderingStatus;
        switch (status) {
            case DONE1:
        surfaceTexture.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
                surfaceTexture.updateTexImage();
                renderingStatus = Status.RENDER2;
                break;
            case DONE2:
        surfaceTexture2.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
                surfaceTexture2.updateTexImage();
                renderingStatus = Status.RENDER1;
                break;
            case RENDER1:
                // No new frame. Keep using surfaceTexture2.
                break;
            case RENDER2:
            default:
                // No new frame. Keep using surfaceTexture.
                break;
        }

        GLES20.glUseProgram(program);

        squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
        GLES20.glEnableVertexAttribArray(aPositionHandle);

        squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
                SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
        GLES20.glEnableVertexAttribArray(aTextureHandle);

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);
        GLES20.glUniform1f(uDrawViewHandle, render ? 1.0f : 0.0f);

        GLES20.glUniform1i(uSamplerHandle, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId);
    //android view
        GLES20.glUniform1i(uSamplerViewHandle, 1);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);

        switch (status) {
            case DONE2:
            case RENDER1:
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, viewId[1]);
                break;
            case RENDER2:
            case DONE1:
            default:
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, viewId[0]);
                break;
        }
    }

    @Override
    protected void disableResources() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GlUtil.disableResources(aTextureHandle, aPositionHandle);
    }

    @Override
    public void release() {
        stopRender();
        GLES20.glDeleteProgram(program);
        viewId = new int[] { -1, -1 };
    if (surfaceTexture != null) surfaceTexture.release();
    if (surfaceTexture2 != null) surfaceTexture2.release();
    }

    public View getView() {
        return view;
    }

    /**
     * Set the view to render onto the GL texture.
     *
     * Pass an {@link ObservableWebView} for accurate dirty-detection with WebView.
     * For all other View subclasses, ViewTreeObserver.OnDrawListener is used.
     *
     * Passing the same instance that is already set is a no-op.
     */
    public void setView(final View view) {
        if (this.view == view)
            return;

        unregisterDirtyListener(this.view);
        stopRender();

        this.view = view;

        if (view != null) {
            if (view.getMeasuredWidth() <= 0 || view.getMeasuredHeight() <= 0) {
                view.measure(View.MeasureSpec.makeMeasureSpec(getPreviewWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(getPreviewHeight(), View.MeasureSpec.EXACTLY));
                view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
            }
            sprite.setView(view);
            dirty = true; // always render at least one frame for the new view
            registerDirtyListener(view);
            startRender();
        }
    }

    /**
     * Force a redraw on the next render cycle regardless of dirty state.
     * Useful when WebView content changes via JavaScript without triggering
     * invalidate()
     * (rare, but possible with some JS frameworks).
     */
    public void markDirty() {
        dirty = true;
    }

    // -------------------------------------------------------------------------
    // Dirty listener
    // -------------------------------------------------------------------------

    private void registerDirtyListener(View view) {
        if (view == null)
            return;
        if (view instanceof ObservableWebView) {
            ((ObservableWebView) view).setOnInvalidateListener(() -> dirty = true);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                registerDirtyListener(group.getChildAt(i));
            }
        }
        // Always register OnDrawListener for the view itself
        try {
            view.getViewTreeObserver().addOnDrawListener(onDrawListener);
        } catch (Exception ignored) {
        }
    }

    private void unregisterDirtyListener(View view) {
        if (view == null)
            return;
        if (view instanceof ObservableWebView) {
            ((ObservableWebView) view).clearOnInvalidateListener();
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                unregisterDirtyListener(group.getChildAt(i));
            }
        }
        try {
            view.getViewTreeObserver().removeOnDrawListener(onDrawListener);
        } catch (Exception ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // Transform / property setters & getters
    // -------------------------------------------------------------------------

    /**
     * @param x Position in percent
     * @param y Position in percent
     */
    public void setPosition(float x, float y) {
        sprite.translate(x, y);
    }

    public void setPosition(TranslateTo positionTo) {
        sprite.translate(positionTo);
    }

    public void setScale(float scaleX, float scaleY) {
        sprite.scale(scaleX, scaleY);
    }

    public void setRotation(int rotation) {
        sprite.setRotation(rotation);
    }

    public PointF getPosition() {
        return sprite.getTranslation();
    }

    public PointF getScale() {
        return sprite.getScale();
    }

    public int getRotation() {
        return sprite.getRotation();
    }

    public boolean isHardwareMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hardwareMode;
    }

    /**
   * Draw in surface using hardware canvas. True by default
     */
    public void setHardwareMode(boolean hardwareMode) {
        this.hardwareMode = hardwareMode;
    }

    public boolean isRender() {
        return render;
    }

    /**
     * Enable or disable rendering. When false the render thread stays alive
     * but skips all drawing until re-enabled.
     */
    public void setRender(boolean render) {
        this.render = render;
        if (render)
            dirty = true; // force a frame immediately on re-enable
    }

    // -------------------------------------------------------------------------
    // Render thread
    // -------------------------------------------------------------------------

    private void startRender() {
        running = true;
        final int generation = ++renderGeneration;
        thread = Executors.newSingleThreadExecutor();
        thread.execute(() -> {
            Log.d(TAG, "render thread started, gen=" + generation);
            while (running && renderGeneration == generation) {
                final long frameStart = System.currentTimeMillis();
                final Status status = renderingStatus;

                if (status == Status.RENDER1 || status == Status.RENDER2) {

                    // If rendering is disabled or we have no new content,
                    // idle cheaply yielding to the system.
                    if (!render || !dirty) {
                        try {
                            Thread.sleep(FRAME_INTERVAL_MS);
                        } catch (InterruptedException e) {
                            break;
                        }
                        continue;
                    }

                    // Clear before locking so any invalidation during the draw
                    // isn't silently dropped
                    dirty = false;

                    // Lock the appropriate surface canvas
                    final Canvas canvas;
                    try {
                        if (isHardwareMode()) {
                            canvas = (status == Status.RENDER1)
                                    ? surface.lockHardwareCanvas()
                                    : surface2.lockHardwareCanvas();
                        } else {
                            canvas = (status == Status.RENDER1)
                                    ? surface.lockCanvas(null)
                                    : surface2.lockCanvas(null);
                        }
                    } catch (IllegalStateException e) {
                        // Surface not ready yet — back off briefly
                        try {
                            Thread.sleep(FRAME_INTERVAL_MS);
                        } catch (InterruptedException ie) {
                            break;
                        }
                        continue;
                    }

                    // Start with a clean slate
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                    if (render) {
                        // Apply sprite transform
                        sprite.calculateDefaultScale(getPreviewWidth(), getPreviewHeight());
                        PointF canvasPosition = sprite.getCanvasPosition(getPreviewWidth(), getPreviewHeight());
                        PointF canvasScale = sprite.getCanvasScale(getPreviewWidth(), getPreviewHeight());
                        PointF rotationAxis = sprite.getRotationAxis();
                        int rotation = sprite.getRotation();

                        canvas.translate(canvasPosition.x, canvasPosition.y);
                        canvas.scale(canvasScale.x, canvasScale.y);
                        canvas.rotate(rotation, rotationAxis.x, rotationAxis.y);

                        // Draw on this thread; fall back to main thread if the view
                        // requires it
                        try {
                            Log.d(TAG, "draw view, gen=" + generation);
                            view.draw(canvas);
                            postCanvas(canvas, status);
                        } catch (Exception e) {
                            final CountDownLatch latch = new CountDownLatch(1);
                            pendingMainThreadDraw = () -> {
                                try {
                                    if (renderGeneration != generation) {
                                        Log.w(TAG, "Main thread fallback aborted due to generation change. Gen: "
                                                + generation + ", Global: " + renderGeneration);
                                        return;
                                    }
                                    Log.d(TAG, "draw view (main thread fallback), gen=" + generation);
                                    view.draw(canvas);
                                    postCanvas(canvas, status);
                                } catch (Exception ex) {
                                    Log.e(TAG, "Main thread fallback draw failed", ex);
                                    // CRITICAL: Must still unlock even if draw fails
                                    try {
                                        if (status == Status.RENDER1)
                                            surface.unlockCanvasAndPost(canvas);
                                        else
                                            surface2.unlockCanvasAndPost(canvas);
                                    } catch (Exception ignored) {
                                    }
                                } finally {
                                    latch.countDown();
                                }
                            };
                            mainHandler.post(pendingMainThreadDraw);
                            try {
                                if (!latch.await(FRAME_INTERVAL_MS * 2, TimeUnit.MILLISECONDS)) {
                                    Log.w(TAG,
                                            "Main thread fallback latch timed out. Possible deadlock or slow UI thread.");
                                }
                            } catch (InterruptedException ie) {
                                break;
                            }
                            pendingMainThreadDraw = null;
                        }
                    } else {
                        // Rendering disabled: post the clear canvas to keep GL textures valid
                        postCanvas(canvas, status);
                    }

                    // Sleep for the remaining frame budget
                    long sleepMs = (render ? FRAME_INTERVAL_MS : (FRAME_INTERVAL_MS * 2))
                            - (System.currentTimeMillis() - frameStart);
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }

                } else {
                    // Not in a renderable state — sleep before rechecking
                    try {
                        Thread.sleep(FRAME_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            Log.d(TAG, "render thread exiting, gen=" + generation);
        });

    }

    private void postCanvas(Canvas canvas, Status status) {
        if (status == Status.RENDER1) {
            surface.unlockCanvasAndPost(canvas);
            renderingStatus = Status.DONE1;
        } else {
            surface2.unlockCanvasAndPost(canvas);
            renderingStatus = Status.DONE2;
        }
    }

    private void stopRender() {
        running = false;

        // Unregister dirty listener before bumping generation
        unregisterDirtyListener(view);

        // Bump generation — any live threads and their queued callbacks will
        // see the mismatch and self-terminate / bail without drawing.
        // Doing this before removing callbacks prevents new ones from starting.
        renderGeneration++;

        // Cancel only our own pending main-thread callback
        if (pendingMainThreadDraw != null) {
            mainHandler.removeCallbacks(pendingMainThreadDraw);
            pendingMainThreadDraw = null;
        }

        if (thread != null) {
            thread.shutdownNow();
            try {
                thread.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }

        renderingStatus = Status.DONE1;
    }
}