package com.pedro.encoder.input.video;

import android.annotation.TargetApi;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.support.annotation.IntDef;

/**
 * Created by pedro on 7/10/17.
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@IntDef({ CameraMetadata.LENS_FACING_BACK, CameraMetadata.LENS_FACING_FRONT })
public @interface Camera2Facing {
}
