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
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.R;
import com.pedro.encoder.input.gl.Sprite;
import com.pedro.encoder.input.gl.TextureLoader;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.StreamObjectBase;
import com.pedro.encoder.utils.gl.TranslateTo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ChromaFilterRender extends BaseFilterRender {

    //rotation matrix
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
    private int aTextureObjectHandle = -1;
    private int uMVPMatrixHandle = -1;
    private int uSTMatrixHandle = -1;
    private int uSamplerHandle = -1;
    private int uObjectHandle = -1;
    private int uSensitiveHandle = -1;

    private FloatBuffer squareVertexObject;

    protected int[] streamObjectTextureId = new int[] { -1 };
    protected TextureLoader textureLoader = new TextureLoader();
    protected StreamObjectBase streamObject;
    private Sprite sprite;
    protected boolean shouldLoad = false;
    private float sensitive = 0.8f;

    public ChromaFilterRender() {
        streamObject = new ImageStreamObject();
        squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.length * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        squareVertex.put(squareVertexDataFilter).position(0);
        sprite = new Sprite();
        float[] vertices = sprite.getTransformedVertices();
        squareVertexObject = ByteBuffer.allocateDirect(vertices.length * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        squareVertexObject.put(vertices).position(0);
        Matrix.setIdentityM(MVPMatrix, 0);
        Matrix.setIdentityM(STMatrix, 0);
    }

    @Override
    protected void initGlFilter(Context context) {
        String vertexShader = GlUtil.getStringFromRaw(context, R.raw.object_vertex);
        String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.chroma_fragment);

        program = GlUtil.createProgram(vertexShader, fragmentShader);
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
        aTextureObjectHandle = GLES20.glGetAttribLocation(program, "aTextureObjectCoord");
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
        uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler");
        uObjectHandle = GLES20.glGetUniformLocation(program, "uObject");
        uSensitiveHandle = GLES20.glGetUniformLocation(program, "uSensitive");
    }

    @Override
    protected void drawFilter() {
        if (shouldLoad) {
            releaseTexture();
            streamObjectTextureId = textureLoader.load(streamObject.getBitmaps());
            shouldLoad = false;
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

        squareVertexObject.position(SQUARE_VERTEX_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(aTextureObjectHandle, 2, GLES20.GL_FLOAT, false,
            2 * FLOAT_SIZE_BYTES, squareVertexObject);
        GLES20.glEnableVertexAttribArray(aTextureObjectHandle);

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);
        //Sampler
        GLES20.glUniform1i(uSamplerHandle, 4);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId);
        //Object
        GLES20.glUniform1i(uObjectHandle, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, streamObjectTextureId[0]);
        GLES20.glUniform1f(uSensitiveHandle, sensitive);
    }

    @Override
    public void release() {
        GLES20.glDeleteProgram(program);
        releaseTexture();
        sprite.reset();
    }

    private void releaseTexture() {
        GLES20.glDeleteTextures(streamObjectTextureId.length, streamObjectTextureId, 0);
        streamObjectTextureId = new int[] { -1 };
    }

    private void setScale(float scaleX, float scaleY) {
        sprite.scale(scaleX, scaleY);
        squareVertexObject.put(sprite.getTransformedVertices()).position(0);
    }

    private void setPosition(TranslateTo positionTo) {
        sprite.translate(positionTo);
        squareVertexObject.put(sprite.getTransformedVertices()).position(0);
    }

    public void setImage(Bitmap bitmap) {
        ((ImageStreamObject) streamObject).load(bitmap);
        shouldLoad = true;
        setScale(100f, 100f);
        setPosition(TranslateTo.CENTER);
    }

    public void setSensitive(float sensitive) {
        this.sensitive = sensitive;
    }
}
