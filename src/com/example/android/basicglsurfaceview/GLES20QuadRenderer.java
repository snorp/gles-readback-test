/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.example.android.basicglsurfaceview;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import java.util.Arrays;
import android.util.Log;

import android.os.Handler;
import android.widget.Toast;

class GLES20QuadRenderer implements GLSurfaceView.Renderer {

    private int mWidth;
    private int mHeight;

    private int mTexWidth;
    private int mTexHeight;

    private ByteBuffer mPixels;

    private Handler mHandler;

    public GLES20QuadRenderer(Context context) {
        mContext = context;
        mHandler = new Handler();
        mQuadVertices = ByteBuffer.allocateDirect(mQuadVerticesData.length
                * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mQuadVertices.put(mQuadVerticesData).position(0);
    }

    private float getCoord(int val, int max) {
        return val * (2.0f / (float)max) - 1.0f;
    }

    private void updateQuadVertices() {
        mQuadVerticesData[1] = getCoord(mTexHeight, mHeight);
        mQuadVerticesData[10] = getCoord(mTexWidth, mWidth);
        mQuadVerticesData[11] = getCoord(mTexHeight, mHeight);
        mQuadVerticesData[15] = getCoord(mTexWidth, mWidth);

        mQuadVertices = ByteBuffer.allocateDirect(mQuadVerticesData.length
                * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mQuadVertices.put(mQuadVerticesData).position(0);
    }

    private static ByteBuffer flipY(ByteBuffer pixels, int width, int height, int stride) {
        ByteBuffer flipped = ByteBuffer.allocateDirect(stride * height);
        byte[] buf = new byte[stride];
        for (int row = height - 1; row >= 0; row--) {
            pixels.position(stride * row);
            pixels.get(buf, 0, stride);
            flipped.put(buf, 0, stride);
        }
        return flipped;
    }

    private int getPixel(ByteBuffer buf, int index) {
        return buf.get(index) & 0xFF;
    }

    public void onDrawFrame(GL10 glUnused) {        
        // Ignore the passed-in GL10 interface, and use the GLES20
        // class's static methods instead.
        GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mProgram);
        checkGlError("glUseProgram");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);

        mQuadVertices.position(QUAD_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                QUAD_VERTICES_DATA_STRIDE_BYTES, mQuadVertices);
        checkGlError("glVertexAttribPointer maPosition");
        mQuadVertices.position(QUAD_VERTICES_DATA_UV_OFFSET);
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                QUAD_VERTICES_DATA_STRIDE_BYTES, mQuadVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");

        GLES20.glFinish();

        ByteBuffer glPixelsBuffer = ByteBuffer.allocateDirect(mTexWidth * mTexHeight * 4);
        glPixelsBuffer.clear();

        GLES20.glReadPixels(0, 0, mTexWidth, mTexHeight,
                            GLES20.GL_RGBA,
                            GLES20.GL_UNSIGNED_BYTE,
                            glPixelsBuffer);
        GLES20.glFinish();
        checkGlError("glReadPixels");

        ByteBuffer flipped = flipY(glPixelsBuffer, mTexWidth, mTexHeight, mTexWidth * 4);

        glPixelsBuffer = flipped;
        glPixelsBuffer.position(0);

        int numSame = 0;
        int numDiff = 0;
        int maxDiff = 0;

        int stride1 = mTexWidth * 3;
        int stride2 = mTexWidth * 4;

        for (int row = 0; row < mTexHeight; row++) {
            int rowStart1 = row * stride1;
            int rowStart2 = row * stride2;

            for (int col = 0; col < mTexWidth; col++) {
                int r1 = getPixel(mPixels, rowStart1 + (col*3));
                int g1 = getPixel(mPixels, rowStart1 + (col*3) + 1);
                int b1 = getPixel(mPixels, rowStart1 + (col*3) + 2);

                int r2 = getPixel(glPixelsBuffer, rowStart2 + (col*4));
                int g2 = getPixel(glPixelsBuffer, rowStart2 + (col*4) + 1);
                int b2 = getPixel(glPixelsBuffer, rowStart2 + (col*4) + 2);

                if (r1 == r2 && g1 == g2 && b1 == b2) {
                    numSame++;
                } else {
                    numDiff++;

                    int dr = Math.abs(r2 - r1);
                    int dg = Math.abs(g2 - g1);
                    int db = Math.abs(b2 - b1);

                    if (dr > maxDiff) {
                        maxDiff = dr;
                    }

                    if (dg > maxDiff) {
                        maxDiff = dg;
                    }

                    if (db > maxDiff) {
                        maxDiff = db;
                    }
                }
            }
        }

        Log.i(TAG, "SNORP: Got " + numSame + " matching, " + numDiff + " different, max difference " + maxDiff);

        final int diffs = numDiff;
        final int diffDelta = maxDiff;
        mHandler.post(new Runnable() {
            public void run() {
                Toast toast = Toast.makeText(mContext, diffs + " differences, max delta " + diffDelta, Toast.LENGTH_SHORT);
                toast.show();
            }
        });

        glPixelsBuffer.position(0);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                            mTexWidth, mTexHeight, 0,
                            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, glPixelsBuffer);
        GLES20.glFinish();
        checkGlError("glTexImage2D");
    }

    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        // Ignore the passed-in GL10 interface, and use the GLES20
        // class's static methods instead.
        GLES20.glViewport(0, 0, width, height);

        mWidth = width;
        mHeight = height;

        Log.i(TAG, "SNORP: size is " + mWidth + "x" + mHeight);

        updateQuadVertices();
    }

    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        // Ignore the passed-in GL10 interface, and use the GLES20
        // class's static methods instead.
        mProgram = createProgram(mVertexShader, mFragmentShader);
        if (mProgram == 0) {
            return;
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }

        /*
         * Create our texture. This has to be done each time the
         * surface is created.
         */

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        mTextureID = textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);

        InputStream is = mContext.getResources()
            .openRawResource(R.raw.gradient);
        mTexWidth = mTexHeight = 512;
        int length = mTexWidth * mTexHeight * 3;
        mPixels = ByteBuffer.allocateDirect(length);
        try {
            byte[] buf = mPixels.array();
            int read = 0;
            while (read < length) {
                read += is.read(buf, read, length - read);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read texture");
        } finally {
            try {
                is.close();
            } catch(IOException e) {
                // Ignore.
            }
        }

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB,
                            mTexWidth, mTexHeight, 0,
                            GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, mPixels);

        //GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmap, 0);
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }

        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int QUAD_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int QUAD_VERTICES_DATA_POS_OFFSET = 0;
    private static final int QUAD_VERTICES_DATA_UV_OFFSET = 3;

    private final float[] mQuadVerticesData ={
        -1f, 1f, 0.0f, // Position 0
        0.0f, 0.0f, // TexCoord 0

        -1f, -1f, 0.0f, // Position 1
        0.0f, 1.0f, // TexCoord 1

        1f, 1f, 0.0f, // Position 2
        1.0f, 0.0f, // TexCoord 2

        1f, -1f, 0.0f, // Position 3
        1.0f, 1.0f // TexCoord 3
    };

    private FloatBuffer mQuadVertices;

    private final String mVertexShader =
        "attribute vec4 aPosition;\n" +
        "attribute vec2 aTextureCoord;\n" +
        "varying vec2 vTextureCoord;\n" +
        "void main() {\n" +
        "  gl_Position = aPosition;\n" +
        "  vTextureCoord = aTextureCoord;\n" +
        "}\n";

    private final String mFragmentShader =
        "precision mediump float;\n" +
        "varying vec2 vTextureCoord;\n" +
        "uniform sampler2D sTexture;\n" +
        "void main() {\n" +
        "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
        "}\n";


    private int mProgram;
    private int mTextureID;
    private int maPositionHandle;
    private int maTextureHandle;

    private Context mContext;
    private static String TAG = "GLES20QuadRenderer";
}
