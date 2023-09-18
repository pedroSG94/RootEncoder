/*
 * Copyright (C) 2021 pedroSG94.
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

package com.pedro.streamer.openglexample;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.pedro.encoder.input.gl.SpriteGestureController;
import com.pedro.encoder.input.gl.render.filters.AnalogTVFilterRender;
import com.pedro.encoder.input.gl.render.filters.AndroidViewFilterRender;
import com.pedro.encoder.input.gl.render.filters.BasicDeformationFilterRender;
import com.pedro.encoder.input.gl.render.filters.BeautyFilterRender;
import com.pedro.encoder.input.gl.render.filters.BlackFilterRender;
import com.pedro.encoder.input.gl.render.filters.BlurFilterRender;
import com.pedro.encoder.input.gl.render.filters.BrightnessFilterRender;
import com.pedro.encoder.input.gl.render.filters.CartoonFilterRender;
import com.pedro.encoder.input.gl.render.filters.ChromaFilterRender;
import com.pedro.encoder.input.gl.render.filters.CircleFilterRender;
import com.pedro.encoder.input.gl.render.filters.ColorFilterRender;
import com.pedro.encoder.input.gl.render.filters.ContrastFilterRender;
import com.pedro.encoder.input.gl.render.filters.DuotoneFilterRender;
import com.pedro.encoder.input.gl.render.filters.EarlyBirdFilterRender;
import com.pedro.encoder.input.gl.render.filters.EdgeDetectionFilterRender;
import com.pedro.encoder.input.gl.render.filters.ExposureFilterRender;
import com.pedro.encoder.input.gl.render.filters.FireFilterRender;
import com.pedro.encoder.input.gl.render.filters.GammaFilterRender;
import com.pedro.encoder.input.gl.render.filters.GlitchFilterRender;
import com.pedro.encoder.input.gl.render.filters.GreyScaleFilterRender;
import com.pedro.encoder.input.gl.render.filters.HalftoneLinesFilterRender;
import com.pedro.encoder.input.gl.render.filters.Image70sFilterRender;
import com.pedro.encoder.input.gl.render.filters.LamoishFilterRender;
import com.pedro.encoder.input.gl.render.filters.MoneyFilterRender;
import com.pedro.encoder.input.gl.render.filters.NegativeFilterRender;
import com.pedro.encoder.input.gl.render.filters.NoFilterRender;
import com.pedro.encoder.input.gl.render.filters.PixelatedFilterRender;
import com.pedro.encoder.input.gl.render.filters.PolygonizationFilterRender;
import com.pedro.encoder.input.gl.render.filters.RGBSaturationFilterRender;
import com.pedro.encoder.input.gl.render.filters.RainbowFilterRender;
import com.pedro.encoder.input.gl.render.filters.RippleFilterRender;
import com.pedro.encoder.input.gl.render.filters.RotationFilterRender;
import com.pedro.encoder.input.gl.render.filters.SaturationFilterRender;
import com.pedro.encoder.input.gl.render.filters.SepiaFilterRender;
import com.pedro.encoder.input.gl.render.filters.SharpnessFilterRender;
import com.pedro.encoder.input.gl.render.filters.SnowFilterRender;
import com.pedro.encoder.input.gl.render.filters.SwirlFilterRender;
import com.pedro.encoder.input.gl.render.filters.TemperatureFilterRender;
import com.pedro.encoder.input.gl.render.filters.ZebraFilterRender;
import com.pedro.encoder.input.gl.render.filters.object.GifObjectFilterRender;
import com.pedro.encoder.input.gl.render.filters.object.ImageObjectFilterRender;
import com.pedro.encoder.input.gl.render.filters.object.SurfaceFilterRender;
import com.pedro.encoder.input.gl.render.filters.object.TextObjectFilterRender;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.utils.gl.TranslateTo;
import com.pedro.library.rtsp.RtspCamera1;
import com.pedro.library.view.OpenGlView;
import com.pedro.streamer.R;
import com.pedro.streamer.utils.PathUtils;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.jetbrains.annotations.NotNull;

/**
 * More documentation see:
 * {@link com.pedro.library.base.Camera1Base}
 * {@link com.pedro.library.rtmp.RtmpCamera1}
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlRtspActivity extends AppCompatActivity
    implements ConnectCheckerRtsp, View.OnClickListener, SurfaceHolder.Callback,
    View.OnTouchListener {

  private RtspCamera1 rtspCamera1;
  private Button button;
  private Button bRecord;
  private EditText etUrl;

  private String currentDateAndTime = "";
  private File folder;
  private OpenGlView openGlView;
  private SpriteGestureController spriteGestureController = new SpriteGestureController();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_open_gl);
    folder = PathUtils.getRecordPath();
    openGlView = findViewById(R.id.surfaceView);
    button = findViewById(R.id.b_start_stop);
    button.setOnClickListener(this);
    bRecord = findViewById(R.id.b_record);
    bRecord.setOnClickListener(this);
    Button switchCamera = findViewById(R.id.switch_camera);
    switchCamera.setOnClickListener(this);
    etUrl = findViewById(R.id.et_rtp_url);
    etUrl.setHint(R.string.hint_rtsp);
    rtspCamera1 = new RtspCamera1(openGlView, this);
    openGlView.getHolder().addCallback(this);
    openGlView.setOnTouchListener(this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.gl_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    //Stop listener for image, text and gif stream objects.
    spriteGestureController.stopListener();
    int itemId = item.getItemId();
    if (itemId == R.id.e_d_fxaa) {
      rtspCamera1.getGlInterface().enableAA(!rtspCamera1.getGlInterface().isAAEnabled());
      Toast.makeText(this,
              "FXAA " + (rtspCamera1.getGlInterface().isAAEnabled() ? "enabled" : "disabled"),
              Toast.LENGTH_SHORT).show();
      return true;
      //filters. NOTE: You can change filter values on fly without reset the filter.
      // Example:
      // ColorFilterRender color = new ColorFilterRender()
      // rtmpCamera1.setFilter(color);
      // color.setRGBColor(255, 0, 0); //red tint
    } else if (itemId == R.id.no_filter) {
      rtspCamera1.getGlInterface().setFilter(new NoFilterRender());
      return true;
    } else if (itemId == R.id.analog_tv) {
      rtspCamera1.getGlInterface().setFilter(new AnalogTVFilterRender());
      return true;
    } else if (itemId == R.id.android_view) {
      AndroidViewFilterRender androidViewFilterRender = new AndroidViewFilterRender();
      androidViewFilterRender.setView(findViewById(R.id.switch_camera));
      rtspCamera1.getGlInterface().setFilter(androidViewFilterRender);
      return true;
    } else if (itemId == R.id.basic_deformation) {
      rtspCamera1.getGlInterface().setFilter(new BasicDeformationFilterRender());
      return true;
    } else if (itemId == R.id.beauty) {
      rtspCamera1.getGlInterface().setFilter(new BeautyFilterRender());
      return true;
    } else if (itemId == R.id.black) {
      rtspCamera1.getGlInterface().setFilter(new BlackFilterRender());
      return true;
    } else if (itemId == R.id.blur) {
      rtspCamera1.getGlInterface().setFilter(new BlurFilterRender());
      return true;
    } else if (itemId == R.id.brightness) {
      rtspCamera1.getGlInterface().setFilter(new BrightnessFilterRender());
      return true;
    } else if (itemId == R.id.cartoon) {
      rtspCamera1.getGlInterface().setFilter(new CartoonFilterRender());
      return true;
    } else if (itemId == R.id.chroma) {
      ChromaFilterRender chromaFilterRender = new ChromaFilterRender();
      rtspCamera1.getGlInterface().setFilter(chromaFilterRender);
      chromaFilterRender.setImage(BitmapFactory.decodeResource(getResources(), R.drawable.bg_chroma));
      return true;
    } else if (itemId == R.id.circle) {
      rtspCamera1.getGlInterface().setFilter(new CircleFilterRender());
      return true;
    } else if (itemId == R.id.color) {
      rtspCamera1.getGlInterface().setFilter(new ColorFilterRender());
      return true;
    } else if (itemId == R.id.contrast) {
      rtspCamera1.getGlInterface().setFilter(new ContrastFilterRender());
      return true;
    } else if (itemId == R.id.duotone) {
      rtspCamera1.getGlInterface().setFilter(new DuotoneFilterRender());
      return true;
    } else if (itemId == R.id.early_bird) {
      rtspCamera1.getGlInterface().setFilter(new EarlyBirdFilterRender());
      return true;
    } else if (itemId == R.id.edge_detection) {
      rtspCamera1.getGlInterface().setFilter(new EdgeDetectionFilterRender());
      return true;
    } else if (itemId == R.id.exposure) {
      rtspCamera1.getGlInterface().setFilter(new ExposureFilterRender());
      return true;
    } else if (itemId == R.id.fire) {
      rtspCamera1.getGlInterface().setFilter(new FireFilterRender());
      return true;
    } else if (itemId == R.id.gamma) {
      rtspCamera1.getGlInterface().setFilter(new GammaFilterRender());
      return true;
    } else if (itemId == R.id.glitch) {
      rtspCamera1.getGlInterface().setFilter(new GlitchFilterRender());
      return true;
    } else if (itemId == R.id.gif) {
      setGifToStream();
      return true;
    } else if (itemId == R.id.grey_scale) {
      rtspCamera1.getGlInterface().setFilter(new GreyScaleFilterRender());
      return true;
    } else if (itemId == R.id.halftone_lines) {
      rtspCamera1.getGlInterface().setFilter(new HalftoneLinesFilterRender());
      return true;
    } else if (itemId == R.id.image) {
      setImageToStream();
      return true;
    } else if (itemId == R.id.image_70s) {
      rtspCamera1.getGlInterface().setFilter(new Image70sFilterRender());
      return true;
    } else if (itemId == R.id.lamoish) {
      rtspCamera1.getGlInterface().setFilter(new LamoishFilterRender());
      return true;
    } else if (itemId == R.id.money) {
      rtspCamera1.getGlInterface().setFilter(new MoneyFilterRender());
      return true;
    } else if (itemId == R.id.negative) {
      rtspCamera1.getGlInterface().setFilter(new NegativeFilterRender());
      return true;
    } else if (itemId == R.id.pixelated) {
      rtspCamera1.getGlInterface().setFilter(new PixelatedFilterRender());
      return true;
    } else if (itemId == R.id.polygonization) {
      rtspCamera1.getGlInterface().setFilter(new PolygonizationFilterRender());
      return true;
    } else if (itemId == R.id.rainbow) {
      rtspCamera1.getGlInterface().setFilter(new RainbowFilterRender());
      return true;
    } else if (itemId == R.id.rgb_saturate) {
      RGBSaturationFilterRender rgbSaturationFilterRender = new RGBSaturationFilterRender();
      rtspCamera1.getGlInterface().setFilter(rgbSaturationFilterRender);
      //Reduce green and blue colors 20%. Red will predominate.
      rgbSaturationFilterRender.setRGBSaturation(1f, 0.8f, 0.8f);
      return true;
    } else if (itemId == R.id.ripple) {
      rtspCamera1.getGlInterface().setFilter(new RippleFilterRender());
      return true;
    } else if (itemId == R.id.rotation) {
      RotationFilterRender rotationFilterRender = new RotationFilterRender();
      rtspCamera1.getGlInterface().setFilter(rotationFilterRender);
      rotationFilterRender.setRotation(90);
      return true;
    } else if (itemId == R.id.saturation) {
      rtspCamera1.getGlInterface().setFilter(new SaturationFilterRender());
      return true;
    } else if (itemId == R.id.sepia) {
      rtspCamera1.getGlInterface().setFilter(new SepiaFilterRender());
      return true;
    } else if (itemId == R.id.sharpness) {
      rtspCamera1.getGlInterface().setFilter(new SharpnessFilterRender());
      return true;
    } else if (itemId == R.id.snow) {
      rtspCamera1.getGlInterface().setFilter(new SnowFilterRender());
      return true;
    } else if (itemId == R.id.swirl) {
      rtspCamera1.getGlInterface().setFilter(new SwirlFilterRender());
      return true;
    } else if (itemId == R.id.surface_filter) {
      SurfaceFilterRender surfaceFilterRender =
              new SurfaceFilterRender(new SurfaceFilterRender.SurfaceReadyCallback() {
                @Override
                public void surfaceReady(SurfaceTexture surfaceTexture) {
                  //You can render this filter with other api that draw in a surface. for example you can use VLC
                  MediaPlayer mediaPlayer =
                          MediaPlayer.create(OpenGlRtspActivity.this, R.raw.big_bunny_240p);
                  mediaPlayer.setSurface(new Surface(surfaceTexture));
                  mediaPlayer.start();
                }
              });
      rtspCamera1.getGlInterface().setFilter(surfaceFilterRender);
      MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.big_bunny_240p);
      mediaPlayer.setSurface(surfaceFilterRender.getSurface());
      mediaPlayer.start();
      //Video is 360x240 so select a percent to keep aspect ratio (50% x 33.3% screen)
      surfaceFilterRender.setScale(50f, 33.3f);
      spriteGestureController.setBaseObjectFilterRender(surfaceFilterRender); //Optional
      return true;
    } else if (itemId == R.id.temperature) {
      rtspCamera1.getGlInterface().setFilter(new TemperatureFilterRender());
      return true;
    } else if (itemId == R.id.text) {
      setTextToStream();
      return true;
    } else if (itemId == R.id.zebra) {
      rtspCamera1.getGlInterface().setFilter(new ZebraFilterRender());
      return true;
    }
    return false;
  }

  private void setTextToStream() {
    TextObjectFilterRender textObjectFilterRender = new TextObjectFilterRender();
    rtspCamera1.getGlInterface().setFilter(textObjectFilterRender);
    textObjectFilterRender.setText("Hello world", 22, Color.RED);
    textObjectFilterRender.setDefaultScale(rtspCamera1.getStreamWidth(),
        rtspCamera1.getStreamHeight());
    textObjectFilterRender.setPosition(TranslateTo.CENTER);
    spriteGestureController.setBaseObjectFilterRender(textObjectFilterRender); //Optional
  }

  private void setImageToStream() {
    ImageObjectFilterRender imageObjectFilterRender = new ImageObjectFilterRender();
    rtspCamera1.getGlInterface().setFilter(imageObjectFilterRender);
    imageObjectFilterRender.setImage(
        BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
    imageObjectFilterRender.setDefaultScale(rtspCamera1.getStreamWidth(),
        rtspCamera1.getStreamHeight());
    imageObjectFilterRender.setPosition(TranslateTo.RIGHT);
    spriteGestureController.setBaseObjectFilterRender(imageObjectFilterRender); //Optional
    spriteGestureController.setPreventMoveOutside(false); //Optional
  }

  private void setGifToStream() {
    try {
      GifObjectFilterRender gifObjectFilterRender = new GifObjectFilterRender();
      rtspCamera1.getGlInterface().setFilter(gifObjectFilterRender);
      gifObjectFilterRender.setGif(getResources().openRawResource(R.raw.banana));
      gifObjectFilterRender.setDefaultScale(rtspCamera1.getStreamWidth(),
          rtspCamera1.getStreamHeight());
      gifObjectFilterRender.setPosition(TranslateTo.BOTTOM);
      spriteGestureController.setBaseObjectFilterRender(gifObjectFilterRender); //Optional
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onConnectionStartedRtsp(@NotNull String rtspUrl) {
  }

  @Override
  public void onConnectionSuccessRtsp() {
    Toast.makeText(OpenGlRtspActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onConnectionFailedRtsp(final String reason) {
    Toast.makeText(OpenGlRtspActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
        .show();
    rtspCamera1.stopStream();
    button.setText(R.string.start_button);
  }

  @Override
  public void onNewBitrateRtsp(long bitrate) {

  }

  @Override
  public void onDisconnectRtsp() {
    Toast.makeText(OpenGlRtspActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAuthErrorRtsp() {
    Toast.makeText(OpenGlRtspActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAuthSuccessRtsp() {
    Toast.makeText(OpenGlRtspActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onClick(View view) {
    int id = view.getId();
    if (id == R.id.b_start_stop) {
      if (!rtspCamera1.isStreaming()) {
        if (rtspCamera1.isRecording()
                || rtspCamera1.prepareAudio() && rtspCamera1.prepareVideo()) {
          button.setText(R.string.stop_button);
          rtspCamera1.startStream(etUrl.getText().toString());
        } else {
          Toast.makeText(this, "Error preparing stream, This device cant do it",
                  Toast.LENGTH_SHORT).show();
        }
      } else {
        button.setText(R.string.start_button);
        rtspCamera1.stopStream();
      }
    } else if (id == R.id.switch_camera) {
      try {
        rtspCamera1.switchCamera();
      } catch (CameraOpenException e) {
        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
      }
    } else if (id == R.id.b_record) {
      if (!rtspCamera1.isRecording()) {
        try {
          if (!folder.exists()) {
            folder.mkdir();
          }
          SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
          currentDateAndTime = sdf.format(new Date());
          if (!rtspCamera1.isStreaming()) {
            if (rtspCamera1.prepareAudio() && rtspCamera1.prepareVideo()) {
              rtspCamera1.startRecord(
                      folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
              bRecord.setText(R.string.stop_record);
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
            } else {
              Toast.makeText(this, "Error preparing stream, This device cant do it",
                      Toast.LENGTH_SHORT).show();
            }
          } else {
            rtspCamera1.startRecord(folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
            bRecord.setText(R.string.stop_record);
            Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
          }
        } catch (IOException e) {
          rtspCamera1.stopRecord();
          PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
          bRecord.setText(R.string.start_record);
          Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
      } else {
        rtspCamera1.stopRecord();
        PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
        bRecord.setText(R.string.start_record);
        Toast.makeText(this,
                "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
                Toast.LENGTH_SHORT).show();
        currentDateAndTime = "";
      }
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder surfaceHolder) {

  }

  @Override
  public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    rtspCamera1.startPreview();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    if (rtspCamera1.isRecording()) {
      rtspCamera1.stopRecord();
      PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
      bRecord.setText(R.string.start_record);
      Toast.makeText(this,
          "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
          Toast.LENGTH_SHORT).show();
      currentDateAndTime = "";
    }
    if (rtspCamera1.isStreaming()) {
      rtspCamera1.stopStream();
      button.setText(getResources().getString(R.string.start_button));
    }
    rtspCamera1.stopPreview();
  }

  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
    if (spriteGestureController.spriteTouched(view, motionEvent)) {
      spriteGestureController.moveSprite(view, motionEvent);
      spriteGestureController.scaleSprite(motionEvent);
      return true;
    }
    return false;
  }
}
