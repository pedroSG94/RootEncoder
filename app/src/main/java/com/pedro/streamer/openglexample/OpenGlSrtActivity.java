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

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

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
import com.pedro.library.srt.SrtCamera1;
import com.pedro.library.view.OpenGlView;
import com.pedro.streamer.R;
import com.pedro.streamer.utils.PathUtils;
import com.pedro.srt.utils.ConnectCheckerSrt;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * More documentation see:
 * {@link com.pedro.library.base.Camera1Base}
 * {@link com.pedro.library.srt.SrtCamera1}
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlSrtActivity extends AppCompatActivity
    implements ConnectCheckerSrt, View.OnClickListener, SurfaceHolder.Callback,
    View.OnTouchListener {

  private SrtCamera1 srtCamera1;
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
    etUrl.setHint(R.string.hint_srt);
    srtCamera1 = new SrtCamera1(openGlView, this);
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
      srtCamera1.getGlInterface().enableAA(!srtCamera1.getGlInterface().isAAEnabled());
      Toast.makeText(this,
              "FXAA " + (srtCamera1.getGlInterface().isAAEnabled() ? "enabled" : "disabled"),
              Toast.LENGTH_SHORT).show();
      return true;
      //filters. NOTE: You can change filter values on fly without reset the filter.
      // Example:
      // ColorFilterRender color = new ColorFilterRender()
      // srtCamera1.setFilter(color);
      // color.setRGBColor(255, 0, 0); //red tint
    } else if (itemId == R.id.no_filter) {
      srtCamera1.getGlInterface().setFilter(new NoFilterRender());
      return true;
    } else if (itemId == R.id.analog_tv) {
      srtCamera1.getGlInterface().setFilter(new AnalogTVFilterRender());
      return true;
    } else if (itemId == R.id.android_view) {
      AndroidViewFilterRender androidViewFilterRender = new AndroidViewFilterRender();
      androidViewFilterRender.setView(findViewById(R.id.switch_camera));
      srtCamera1.getGlInterface().setFilter(androidViewFilterRender);
      return true;
    } else if (itemId == R.id.basic_deformation) {
      srtCamera1.getGlInterface().setFilter(new BasicDeformationFilterRender());
      return true;
    } else if (itemId == R.id.beauty) {
      srtCamera1.getGlInterface().setFilter(new BeautyFilterRender());
      return true;
    } else if (itemId == R.id.black) {
      srtCamera1.getGlInterface().setFilter(new BlackFilterRender());
      return true;
    } else if (itemId == R.id.blur) {
      srtCamera1.getGlInterface().setFilter(new BlurFilterRender());
      return true;
    } else if (itemId == R.id.brightness) {
      srtCamera1.getGlInterface().setFilter(new BrightnessFilterRender());
      return true;
    } else if (itemId == R.id.cartoon) {
      srtCamera1.getGlInterface().setFilter(new CartoonFilterRender());
      return true;
    } else if (itemId == R.id.chroma) {
      ChromaFilterRender chromaFilterRender = new ChromaFilterRender();
      srtCamera1.getGlInterface().setFilter(chromaFilterRender);
      chromaFilterRender.setImage(BitmapFactory.decodeResource(getResources(), R.drawable.bg_chroma));
      return true;
    } else if (itemId == R.id.circle) {
      srtCamera1.getGlInterface().setFilter(new CircleFilterRender());
      return true;
    } else if (itemId == R.id.color) {
      srtCamera1.getGlInterface().setFilter(new ColorFilterRender());
      return true;
    } else if (itemId == R.id.contrast) {
      srtCamera1.getGlInterface().setFilter(new ContrastFilterRender());
      return true;
    } else if (itemId == R.id.duotone) {
      srtCamera1.getGlInterface().setFilter(new DuotoneFilterRender());
      return true;
    } else if (itemId == R.id.early_bird) {
      srtCamera1.getGlInterface().setFilter(new EarlyBirdFilterRender());
      return true;
    } else if (itemId == R.id.edge_detection) {
      srtCamera1.getGlInterface().setFilter(new EdgeDetectionFilterRender());
      return true;
    } else if (itemId == R.id.exposure) {
      srtCamera1.getGlInterface().setFilter(new ExposureFilterRender());
      return true;
    } else if (itemId == R.id.fire) {
      srtCamera1.getGlInterface().setFilter(new FireFilterRender());
      return true;
    } else if (itemId == R.id.gamma) {
      srtCamera1.getGlInterface().setFilter(new GammaFilterRender());
      return true;
    } else if (itemId == R.id.glitch) {
      srtCamera1.getGlInterface().setFilter(new GlitchFilterRender());
      return true;
    } else if (itemId == R.id.gif) {
      setGifToStream();
      return true;
    } else if (itemId == R.id.grey_scale) {
      srtCamera1.getGlInterface().setFilter(new GreyScaleFilterRender());
      return true;
    } else if (itemId == R.id.halftone_lines) {
      srtCamera1.getGlInterface().setFilter(new HalftoneLinesFilterRender());
      return true;
    } else if (itemId == R.id.image) {
      setImageToStream();
      return true;
    } else if (itemId == R.id.image_70s) {
      srtCamera1.getGlInterface().setFilter(new Image70sFilterRender());
      return true;
    } else if (itemId == R.id.lamoish) {
      srtCamera1.getGlInterface().setFilter(new LamoishFilterRender());
      return true;
    } else if (itemId == R.id.money) {
      srtCamera1.getGlInterface().setFilter(new MoneyFilterRender());
      return true;
    } else if (itemId == R.id.negative) {
      srtCamera1.getGlInterface().setFilter(new NegativeFilterRender());
      return true;
    } else if (itemId == R.id.pixelated) {
      srtCamera1.getGlInterface().setFilter(new PixelatedFilterRender());
      return true;
    } else if (itemId == R.id.polygonization) {
      srtCamera1.getGlInterface().setFilter(new PolygonizationFilterRender());
      return true;
    } else if (itemId == R.id.rainbow) {
      srtCamera1.getGlInterface().setFilter(new RainbowFilterRender());
      return true;
    } else if (itemId == R.id.rgb_saturate) {
      RGBSaturationFilterRender rgbSaturationFilterRender = new RGBSaturationFilterRender();
      srtCamera1.getGlInterface().setFilter(rgbSaturationFilterRender);
      //Reduce green and blue colors 20%. Red will predominate.
      rgbSaturationFilterRender.setRGBSaturation(1f, 0.8f, 0.8f);
      return true;
    } else if (itemId == R.id.ripple) {
      srtCamera1.getGlInterface().setFilter(new RippleFilterRender());
      return true;
    } else if (itemId == R.id.rotation) {
      RotationFilterRender rotationFilterRender = new RotationFilterRender();
      srtCamera1.getGlInterface().setFilter(rotationFilterRender);
      rotationFilterRender.setRotation(90);
      return true;
    } else if (itemId == R.id.saturation) {
      srtCamera1.getGlInterface().setFilter(new SaturationFilterRender());
      return true;
    } else if (itemId == R.id.sepia) {
      srtCamera1.getGlInterface().setFilter(new SepiaFilterRender());
      return true;
    } else if (itemId == R.id.sharpness) {
      srtCamera1.getGlInterface().setFilter(new SharpnessFilterRender());
      return true;
    } else if (itemId == R.id.snow) {
      srtCamera1.getGlInterface().setFilter(new SnowFilterRender());
      return true;
    } else if (itemId == R.id.swirl) {
      srtCamera1.getGlInterface().setFilter(new SwirlFilterRender());
      return true;
    } else if (itemId == R.id.surface_filter) {
      SurfaceFilterRender surfaceFilterRender =
              new SurfaceFilterRender(new SurfaceFilterRender.SurfaceReadyCallback() {
                @Override
                public void surfaceReady(SurfaceTexture surfaceTexture) {
                  //You can render this filter with other api that draw in a surface. for example you can use VLC
                  MediaPlayer mediaPlayer =
                          MediaPlayer.create(OpenGlSrtActivity.this, R.raw.big_bunny_240p);
                  mediaPlayer.setSurface(new Surface(surfaceTexture));
                  mediaPlayer.start();
                }
              });
      srtCamera1.getGlInterface().setFilter(surfaceFilterRender);
      MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.big_bunny_240p);
      mediaPlayer.setSurface(surfaceFilterRender.getSurface());
      mediaPlayer.start();
      //Video is 360x240 so select a percent to keep aspect ratio (50% x 33.3% screen)
      surfaceFilterRender.setScale(50f, 33.3f);
      spriteGestureController.setBaseObjectFilterRender(surfaceFilterRender); //Optional
      return true;
    } else if (itemId == R.id.temperature) {
      srtCamera1.getGlInterface().setFilter(new TemperatureFilterRender());
      return true;
    } else if (itemId == R.id.text) {
      setTextToStream();
      return true;
    } else if (itemId == R.id.zebra) {
      srtCamera1.getGlInterface().setFilter(new ZebraFilterRender());
      return true;
    }
    return false;
  }

  private void setTextToStream() {
    TextObjectFilterRender textObjectFilterRender = new TextObjectFilterRender();
    srtCamera1.getGlInterface().setFilter(textObjectFilterRender);
    textObjectFilterRender.setText("Hello world", 22, Color.RED);
    textObjectFilterRender.setDefaultScale(srtCamera1.getStreamWidth(),
        srtCamera1.getStreamHeight());
    textObjectFilterRender.setPosition(TranslateTo.CENTER);
    spriteGestureController.setBaseObjectFilterRender(textObjectFilterRender); //Optional
  }

  private void setImageToStream() {
    ImageObjectFilterRender imageObjectFilterRender = new ImageObjectFilterRender();
    srtCamera1.getGlInterface().setFilter(imageObjectFilterRender);
    imageObjectFilterRender.setImage(
        BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
    imageObjectFilterRender.setDefaultScale(srtCamera1.getStreamWidth(),
        srtCamera1.getStreamHeight());
    imageObjectFilterRender.setPosition(TranslateTo.RIGHT);
    spriteGestureController.setBaseObjectFilterRender(imageObjectFilterRender); //Optional
    spriteGestureController.setPreventMoveOutside(false); //Optional
  }

  private void setGifToStream() {
    try {
      GifObjectFilterRender gifObjectFilterRender = new GifObjectFilterRender();
      srtCamera1.getGlInterface().setFilter(gifObjectFilterRender);
      gifObjectFilterRender.setGif(getResources().openRawResource(R.raw.banana));
      gifObjectFilterRender.setDefaultScale(srtCamera1.getStreamWidth(),
          srtCamera1.getStreamHeight());
      gifObjectFilterRender.setPosition(TranslateTo.BOTTOM);
      spriteGestureController.setBaseObjectFilterRender(gifObjectFilterRender); //Optional
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onConnectionStartedSrt(@NotNull String srtUrl) {
  }

  @Override
  public void onConnectionSuccessSrt() {
    Toast.makeText(OpenGlSrtActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onConnectionFailedSrt(final String reason) {
    Toast.makeText(OpenGlSrtActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
        .show();
    srtCamera1.stopStream();
    button.setText(R.string.start_button);
  }

  @Override
  public void onNewBitrateSrt(long bitrate) {

  }

  @Override
  public void onDisconnectSrt() {
    Toast.makeText(OpenGlSrtActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAuthErrorSrt() {
    Toast.makeText(OpenGlSrtActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAuthSuccessSrt() {
    Toast.makeText(OpenGlSrtActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onClick(View view) {
    int id = view.getId();
    if (id == R.id.b_start_stop) {
      if (!srtCamera1.isStreaming()) {
        if (srtCamera1.isRecording()
                || srtCamera1.prepareAudio() && srtCamera1.prepareVideo()) {
          button.setText(R.string.stop_button);
          srtCamera1.startStream(etUrl.getText().toString());
        } else {
          Toast.makeText(this, "Error preparing stream, This device cant do it",
                  Toast.LENGTH_SHORT).show();
        }
      } else {
        button.setText(R.string.start_button);
        srtCamera1.stopStream();
      }
    } else if (id == R.id.switch_camera) {
      try {
        srtCamera1.switchCamera();
      } catch (CameraOpenException e) {
        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
      }
    } else if (id == R.id.b_record) {
      if (!srtCamera1.isRecording()) {
        try {
          if (!folder.exists()) {
            folder.mkdir();
          }
          SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
          currentDateAndTime = sdf.format(new Date());
          if (!srtCamera1.isStreaming()) {
            if (srtCamera1.prepareAudio() && srtCamera1.prepareVideo()) {
              srtCamera1.startRecord(
                      folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
              bRecord.setText(R.string.stop_record);
              Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
            } else {
              Toast.makeText(this, "Error preparing stream, This device cant do it",
                      Toast.LENGTH_SHORT).show();
            }
          } else {
            srtCamera1.startRecord(folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
            bRecord.setText(R.string.stop_record);
            Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
          }
        } catch (IOException e) {
          srtCamera1.stopRecord();
          PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
          bRecord.setText(R.string.start_record);
          Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
      } else {
        srtCamera1.stopRecord();
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
    srtCamera1.startPreview();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    if (srtCamera1.isRecording()) {
      srtCamera1.stopRecord();
      PathUtils.updateGallery(this, folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
      bRecord.setText(R.string.start_record);
      Toast.makeText(this,
          "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
          Toast.LENGTH_SHORT).show();
      currentDateAndTime = "";
    }
    if (srtCamera1.isStreaming()) {
      srtCamera1.stopStream();
      button.setText(getResources().getString(R.string.start_button));
    }
    srtCamera1.stopPreview();
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
