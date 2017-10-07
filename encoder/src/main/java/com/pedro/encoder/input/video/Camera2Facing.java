package com.pedro.encoder.input.video;

import android.support.annotation.IntDef;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;

/**
 * Created by pedro on 7/10/17.
 */

@IntDef({ LENS_FACING_BACK, LENS_FACING_FRONT /*, LENS_FACING_EXTERNAL*/ })
public @interface Camera2Facing {
}
