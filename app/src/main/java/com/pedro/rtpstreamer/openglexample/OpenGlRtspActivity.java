package com.pedro.rtpstreamer.openglexample;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.pedro.encoder.input.gl.render.filters.AndroidViewFilterRender;
import com.pedro.encoder.input.gl.render.filters.BasicDeformationFilterRender;
import com.pedro.encoder.input.gl.render.filters.BeautyFilterRender;
import com.pedro.encoder.input.gl.render.filters.BlurFilterRender;
import com.pedro.encoder.input.gl.render.filters.BrightnessFilterRender;
import com.pedro.encoder.input.gl.render.filters.CartoonFilterRender;
import com.pedro.encoder.input.gl.render.filters.ColorFilterRender;
import com.pedro.encoder.input.gl.render.filters.ContrastFilterRender;
import com.pedro.encoder.input.gl.render.filters.DuotoneFilterRender;
import com.pedro.encoder.input.gl.render.filters.EarlyBirdFilterRender;
import com.pedro.encoder.input.gl.render.filters.EdgeDetectionFilterRender;
import com.pedro.encoder.input.gl.render.filters.ExposureFilterRender;
import com.pedro.encoder.input.gl.render.filters.FireFilterRender;
import com.pedro.encoder.input.gl.render.filters.GammaFilterRender;
import com.pedro.encoder.input.gl.render.filters.GreyScaleFilterRender;
import com.pedro.encoder.input.gl.render.filters.HalftoneLinesFilterRender;
import com.pedro.encoder.input.gl.render.filters.Image70sFilterRender;
import com.pedro.encoder.input.gl.render.filters.LamoishFilterRender;
import com.pedro.encoder.input.gl.render.filters.MoneyFilterRender;
import com.pedro.encoder.input.gl.render.filters.NegativeFilterRender;
import com.pedro.encoder.input.gl.render.filters.NoFilterRender;
import com.pedro.encoder.input.gl.render.filters.PixelatedFilterRender;
import com.pedro.encoder.input.gl.render.filters.PolygonizationFilterRender;
import com.pedro.encoder.input.gl.render.filters.RainbowFilterRender;
import com.pedro.encoder.input.gl.render.filters.RippleFilterRender;
import com.pedro.encoder.input.gl.render.filters.SaturationFilterRender;
import com.pedro.encoder.input.gl.render.filters.SepiaFilterRender;
import com.pedro.encoder.input.gl.render.filters.SharpnessFilterRender;
import com.pedro.encoder.input.gl.render.filters.TemperatureFilterRender;
import com.pedro.encoder.input.gl.render.filters.ZebraFilterRender;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.utils.gl.GifStreamObject;
import com.pedro.encoder.utils.gl.ImageStreamObject;
import com.pedro.encoder.utils.gl.TextStreamObject;
import com.pedro.rtplibrary.rtsp.RtspCamera1;
import com.pedro.rtplibrary.view.OpenGlView;
import com.pedro.rtpstreamer.R;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * More documentation see:
 * {@link com.pedro.rtplibrary.base.Camera1Base}
 * {@link com.pedro.rtplibrary.rtmp.RtmpCamera1}
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class OpenGlRtspActivity extends AppCompatActivity
    implements ConnectCheckerRtsp, View.OnClickListener, SurfaceHolder.Callback {

  private RtspCamera1 rtspCamera1;
  private Button button;
  private Button bRecord;
  private EditText etUrl;

  private String currentDateAndTime = "";
  private File folder = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
      + "/rtmp-rtsp-stream-client-java");

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_open_gl);
    OpenGlView openGlView = findViewById(R.id.surfaceView);
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
    //openGlView.setKeepAspectRatio(true);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.gl_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.e_d_fxaa:
        Toast.makeText(this, "FXAA " + (rtspCamera1.isAAEnabled() ? " enabled" : "disabled"),
            Toast.LENGTH_SHORT).show();
        rtspCamera1.enableAA(!rtspCamera1.isAAEnabled());
        return true;
      //stream object
      case R.id.text:
        setTextToStream();
        return true;
      case R.id.image:
        setImageToStream();
        return true;
      case R.id.gif:
        setGifToStream();
        return true;
      case R.id.clear:
        rtspCamera1.clearStreamObject();
        return true;
      //filters. NOTE: You can change filter values on fly without re set the filter.
      // Example:
      // ColorFilterRender color = new ColorFilterRender()
      // rtmpCamera1.setFilter(color);
      // color.setRGBColor(255, 0, 0); //red tint
      case R.id.no_filter:
        rtspCamera1.setFilter(new NoFilterRender());
        return true;
      case R.id.android_view:
        AndroidViewFilterRender androidViewFilterRender = new AndroidViewFilterRender();
        androidViewFilterRender.setView(findViewById(R.id.activity_example_rtmp));
        rtspCamera1.setFilter(androidViewFilterRender);
        return true;
      case R.id.basic_deformation:
        rtspCamera1.setFilter(new BasicDeformationFilterRender());
        return true;
      case R.id.beauty:
        rtspCamera1.setFilter(new BeautyFilterRender());
        return true;
      case R.id.blur:
        rtspCamera1.setFilter(new BlurFilterRender());
        return true;
      case R.id.brightness:
        rtspCamera1.setFilter(new BrightnessFilterRender());
        return true;
      case R.id.cartoon:
        rtspCamera1.setFilter(new CartoonFilterRender());
        return true;
      case R.id.color:
        rtspCamera1.setFilter(new ColorFilterRender());
        return true;
      case R.id.contrast:
        rtspCamera1.setFilter(new ContrastFilterRender());
        return true;
      case R.id.duotone:
        rtspCamera1.setFilter(new DuotoneFilterRender());
        return true;
      case R.id.early_bird:
        rtspCamera1.setFilter(new EarlyBirdFilterRender());
        return true;
      case R.id.edge_detection:
        rtspCamera1.setFilter(new EdgeDetectionFilterRender());
        return true;
      case R.id.exposure:
        rtspCamera1.setFilter(new ExposureFilterRender());
        return true;
      case R.id.fire:
        rtspCamera1.setFilter(new FireFilterRender());
        return true;
      case R.id.gamma:
        rtspCamera1.setFilter(new GammaFilterRender());
        return true;
      case R.id.grey_scale:
        rtspCamera1.setFilter(new GreyScaleFilterRender());
        return true;
      case R.id.halftone_lines:
        rtspCamera1.setFilter(new HalftoneLinesFilterRender());
        return true;
      case R.id.image_70s:
        rtspCamera1.setFilter(new Image70sFilterRender());
        return true;
      case R.id.lamoish:
        rtspCamera1.setFilter(new LamoishFilterRender());
        return true;
      case R.id.money:
        rtspCamera1.setFilter(new MoneyFilterRender());
        return true;
      case R.id.negative:
        rtspCamera1.setFilter(new NegativeFilterRender());
        return true;
      case R.id.pixelated:
        rtspCamera1.setFilter(new PixelatedFilterRender());
        return true;
      case R.id.polygonization:
        rtspCamera1.setFilter(new PolygonizationFilterRender());
        return true;
      case R.id.rainbow:
        rtspCamera1.setFilter(new RainbowFilterRender());
        return true;
      case R.id.ripple:
        rtspCamera1.setFilter(new RippleFilterRender());
        return true;
      case R.id.saturation:
        rtspCamera1.setFilter(new SaturationFilterRender());
        return true;
      case R.id.sepia:
        rtspCamera1.setFilter(new SepiaFilterRender());
        return true;
      case R.id.sharpness:
        rtspCamera1.setFilter(new SharpnessFilterRender());
        return true;
      case R.id.temperature:
        rtspCamera1.setFilter(new TemperatureFilterRender());
        return true;
      case R.id.zebra:
        rtspCamera1.setFilter(new ZebraFilterRender());
        return true;
      default:
        return false;
    }
  }

  private void setTextToStream() {
    try {
      TextStreamObject textStreamObject = new TextStreamObject();
      textStreamObject.load("Hello world", 22, Color.RED);
      rtspCamera1.setTextStreamObject(textStreamObject);
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void setImageToStream() {
    try {
      ImageStreamObject imageStreamObject = new ImageStreamObject();
      imageStreamObject.load(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
      rtspCamera1.setImageStreamObject(imageStreamObject);
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void setGifToStream() {
    try {
      GifStreamObject gifStreamObject = new GifStreamObject();
      gifStreamObject.load(getResources().openRawResource(R.raw.banana));
      rtspCamera1.setGifStreamObject(gifStreamObject);
    } catch (IOException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onConnectionSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Connection success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onConnectionFailedRtsp(final String reason) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Connection failed. " + reason, Toast.LENGTH_SHORT)
            .show();
        rtspCamera1.stopStream();
        button.setText(R.string.start_button);
      }
    });
  }

  @Override
  public void onDisconnectRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthErrorRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Auth error", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onAuthSuccessRtsp() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(OpenGlRtspActivity.this, "Auth success", Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.b_start_stop:
        if (!rtspCamera1.isStreaming()) {
          if (rtspCamera1.prepareAudio() && rtspCamera1.prepareVideo()) {
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
        break;
      case R.id.switch_camera:
        try {
          rtspCamera1.switchCamera();
        } catch (CameraOpenException e) {
          Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        break;
      case R.id.b_record:
        if (!rtspCamera1.isRecording()) {
          try {
            if (!folder.exists()) {
              folder.mkdir();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            currentDateAndTime = sdf.format(new Date());
            rtspCamera1.startRecord(folder.getAbsolutePath() + "/" + currentDateAndTime + ".mp4");
            bRecord.setText(R.string.stop_record);
            Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show();
          } catch (IOException e) {
            rtspCamera1.stopRecord();
            bRecord.setText(R.string.start_record);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
          }
        } else {
          rtspCamera1.stopRecord();
          bRecord.setText(R.string.start_record);
          Toast.makeText(this,
              "file " + currentDateAndTime + ".mp4 saved in " + folder.getAbsolutePath(),
              Toast.LENGTH_SHORT).show();
          currentDateAndTime = "";
        }
        break;
      default:
        break;
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
}
