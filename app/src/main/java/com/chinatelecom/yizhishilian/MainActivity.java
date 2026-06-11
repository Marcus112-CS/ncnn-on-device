// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.chinatelecom.yizhishilian;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class MainActivity extends Activity implements SurfaceHolder.Callback
{
    public static final int REQUEST_CAMERA = 100;

    private YOLOv8Ncnn yolov8ncnn = new YOLOv8Ncnn();
    private int facing = 1; // 1 = rear/back camera (0 = front)

    private Spinner spinnerTask;
    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_task = 0;
    private int current_model = 0;
    private int current_cpugpu = 0;

    private static final int TASK_HELMET = 3; // 安监 scene
    // 安监 size spinner [n-640,s-640,n-480,s-480,n-320,s-320] -> modelid (size letter + target res)
    private static final int[] HELMET_MODELID = { 6, 7, 3, 4, 0, 1 };
    private ArrayAdapter<CharSequence> fullSizeAdapter;
    private ArrayAdapter<CharSequence> helmetSizeAdapter;
    private boolean sizeIsHelmet = false;

    private SurfaceView cameraView;
    private FrameLayout cameraContainer;
    private View controlPanel;
    private TextView textFps;

    private final Handler fpsHandler = new Handler(Looper.getMainLooper());
    private final Runnable fpsUpdater = new Runnable() {
        @Override
        public void run() {
            textFps.setText(String.format("FPS %.1f", yolov8ncnn.getFps()));
            fpsHandler.postDelayed(this, 500);
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraView = (SurfaceView) findViewById(R.id.cameraview);
        cameraContainer = (FrameLayout) findViewById(R.id.cameraContainer);
        controlPanel = findViewById(R.id.controlPanel);

        textFps = (TextView) findViewById(R.id.textFps);

        ImageView logo = (ImageView) findViewById(R.id.logo);
        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);

        Button buttonSwitchCamera = (Button) findViewById(R.id.buttonSwitchCamera);
        buttonSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

                int new_facing = 1 - facing;

                yolov8ncnn.closeCamera();

                yolov8ncnn.openCamera(new_facing);

                facing = new_facing;
            }
        });

        spinnerTask = (Spinner) findViewById(R.id.spinnerTask);
        spinnerTask.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_task)
                {
                    current_task = position;

                    // 安监 only ships n/s @640; swap the size spinner options accordingly
                    boolean wantHelmet = (position == TASK_HELMET);
                    if (wantHelmet != sizeIsHelmet)
                    {
                        sizeIsHelmet = wantHelmet;
                        current_model = 0;
                        // setAdapter resets selection to 0 and fires the size listener with
                        // position 0; current_model is already 0 so that fires no extra reload.
                        spinnerModel.setAdapter(wantHelmet ? helmetSizeAdapter : fullSizeAdapter);
                    }

                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
        fullSizeAdapter = ArrayAdapter.createFromResource(this, R.array.model_array, android.R.layout.simple_spinner_item);
        fullSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        helmetSizeAdapter = ArrayAdapter.createFromResource(this, R.array.model_array_helmet, android.R.layout.simple_spinner_item);
        helmetSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(fullSizeAdapter);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_model)
                {
                    current_model = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_cpugpu)
                {
                    current_cpugpu = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        reload();
    }

    private void reload()
    {
        // 安监 size spinner positions map to modelid via HELMET_MODELID (n/s x 640/480/320).
        // Other scenes: the size spinner position is the modelid directly (0-8).
        int modelid = (current_task == TASK_HELMET) ? HELMET_MODELID[current_model] : current_model;

        boolean ret_init = yolov8ncnn.loadModel(getAssets(), current_task, modelid, current_cpugpu);
        if (!ret_init)
        {
            Log.e("MainActivity", "yolov8ncnn loadModel failed");
        }
    }

    private void applySettings()
    {
        SharedPreferences sp = Prefs.get(this);

        boolean fullscreen = sp.getBoolean(Prefs.KEY_FULLSCREEN, false);
        controlPanel.setVisibility(fullscreen ? View.GONE : View.VISIBLE);

        applyAspect(sp.getString(Prefs.KEY_ASPECT, Prefs.ASPECT_FILL));
    }

    private void applyAspect(final String aspect)
    {
        final int cw = cameraContainer.getWidth();
        final int ch = cameraContainer.getHeight();
        if (cw == 0 || ch == 0)
        {
            // not laid out yet; retry once measured
            cameraContainer.post(new Runnable() {
                @Override
                public void run() { applyAspect(aspect); }
            });
            return;
        }

        int w = cw;
        int h = ch;
        if (Prefs.ASPECT_169.equals(aspect) || Prefs.ASPECT_43.equals(aspect))
        {
            // ratio = height / width (portrait preview rectangle), fit inside the container
            float r = Prefs.ASPECT_43.equals(aspect) ? (4f / 3f) : (16f / 9f);
            w = cw;
            h = Math.round(cw * r);
            if (h > ch) { h = ch; w = Math.round(ch / r); }
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cameraView.getLayoutParams();
        lp.width = w;
        lp.height = h;
        lp.gravity = Gravity.CENTER;
        cameraView.setLayoutParams(lp);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        yolov8ncnn.setOutputWindow(holder.getSurface());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }

        yolov8ncnn.openCamera(facing);

        fpsHandler.post(fpsUpdater);

        applySettings();
    }

    @Override
    public void onPause()
    {
        super.onPause();

        fpsHandler.removeCallbacks(fpsUpdater);

        yolov8ncnn.closeCamera();
    }
}
