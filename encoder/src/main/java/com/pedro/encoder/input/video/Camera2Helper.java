package com.pedro.encoder.input.video;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2Helper {

    @Nullable
    public static String getCameraIdForFacing(
            CameraManager cameraManager,
            CameraHelper.Facing facing
    ) throws CameraAccessException {
        int selectedFacing = getFacing(facing);
        for (String cameraId : cameraManager.getCameraIdList()) {
            Integer cameraFacing = cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING);
            if (cameraFacing != null && cameraFacing == selectedFacing) {
                return cameraId;
            }
        }
        return null;
    }

    @Nullable
    public static CameraCharacteristics getCharacteristicsForFacing(
            CameraManager cameraManager,
            CameraHelper.Facing facing
    ) throws CameraAccessException {
        String cameraId = getCameraIdForFacing(cameraManager, facing);
        return cameraId != null ? cameraManager.getCameraCharacteristics(cameraId) : null;
    }

    private static int getFacing(CameraHelper.Facing facing) {
        return facing == CameraHelper.Facing.BACK ? CameraMetadata.LENS_FACING_BACK
                : CameraMetadata.LENS_FACING_FRONT;
    }
}
