package com.qihoo.mobilebletest;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.qihoo.mobilebletest.ble.BleManager;

public class MainActivity extends AppCompatActivity {

    private BleManager mBleManager;
    private TextView mTip;
    private RelativeLayout mBluetootTipLayout;
    private int mCount = 0;
    private static final String TAG = "EsimTestActivity";
    @SuppressLint("NewApi")
    Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            mTip.setTextColor(Color.BLACK);
            switch (msg.what) {
                case 1:
                    if (mBleManager.isEnable()) {
                        mCount = 0;
                        Log.d(TAG, "handleMessage: Bluetooth already open");
                        mHandler.removeMessages(1);
                        mBleManager.initGATTServer();
                        //隐藏加载框
                        mBluetootTipLayout.setVisibility(View.GONE);
                    } else {
                        if (mCount < 10) {
                            mCount++;
                            Log.d(TAG, "handleMessage: delay.... ");
                            mHandler.sendEmptyMessageDelayed(1, 1000);
                        } else {
                            mCount = 0;
                            mHandler.removeMessages(1);
                            //reopen Bluetooth
                            Log.d(TAG, "reopen bluetooth .....");
                            openBluetooth();

                        }
                    }
                    break;
                case 5:
                    mTip.setText("蓝牙失去连接");
                    break;
                default:
                    break;
            }
            return false;
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mTip = findViewById(R.id.test_tip);
        mBluetootTipLayout = findViewById(R.id.open_bluetooth);
        mBleManager = BleManager.getInstance();
        mBleManager.setHandler(mHandler);
        openBluetooth();

    }

    /**
     * open Bluetooth
     */
    private void openBluetooth() {
        if (!mBleManager.isEnable()) {
            mBleManager.openBT();
            mHandler.sendEmptyMessage(1);
        } else {
            Log.e(TAG, "openBluetooth: bluetooth is open ");
            mBluetootTipLayout.setVisibility(View.GONE);
            mHandler.sendEmptyMessage(1);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
        mHandler.removeMessages(1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBleManager.closeBT();
        }
    }
}
