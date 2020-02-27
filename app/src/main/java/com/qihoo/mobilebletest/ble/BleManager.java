package com.qihoo.mobilebletest.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.qihoo.mobilebletest.app.MyApplication;

import java.io.ByteArrayOutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class BleManager {
    private static final String TAG = "BleManager";
    public static final String LPA_USE_WIFI = "lpa_wifi_use";
    private static BleManager INSTANCE = null;
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private UUID UUID_SERVER = UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb");
    private UUID UUID_CHARREAD = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb");
    private UUID UUID_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    private UUID UUID_CHARWRITE = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private BluetoothGattCharacteristic mCharacteristicWrite;
    private AdvertiseSettings mSettings;
    private AdvertiseData mAdvertiseData;
    private AdvertiseCallback mCallback;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    private BluetoothGattCharacteristic mGattCharacteristic;
    private Queue<byte[]> commandQueue;
    private int maxChunkDataSize;
    private ByteArrayOutputStream responseBuffer;
    private static byte[] GET_MORE_DATA = new byte[]{113, -128};
    private Handler mHandler;


    private BleManager() {
        mContext = MyApplication.getInstance().getApplicationContext();
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        maxChunkDataSize = 17;
        commandQueue = new LinkedList();
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    public static BleManager getInstance() {
        if (INSTANCE == null) {
            synchronized (BleManager.class) {
                INSTANCE = new BleManager();
            }
        }
        return INSTANCE;
    }


    /**
     * Return true if Bluetooth is currently enabled and ready for use.
     *
     * @return true if the local adapter is turned on
     */
    public boolean isEnable() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    /**
     * Turn on the local Bluetooth adapter
     *
     * @return true to indicate adapter startup has begun, or false on immediate error
     */
    public boolean openBT() {
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            return mBluetoothAdapter.enable();
        }
        return false;
    }

    /**
     * Turn off the local Bluetooth adapter
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void closeBT() {
        if (null != mBluetoothAdapter && mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        }
        if (null != mBluetoothLeAdvertiser) {
            mBluetoothLeAdvertiser.stopAdvertising(mCallback);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void initGATTServer() {
        mSettings = new AdvertiseSettings.Builder()
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .build();

        mAdvertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        mBluetoothAdapter.setName("BleFastManager");


        mCallback = new AdvertiseCallback() {

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.i(TAG, "BLE advertisement added successfully");
                initServices(mContext);

            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.i(TAG, "Failed to add BLE advertisement, reason: " + errorCode);
            }
        };

        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mBluetoothLeAdvertiser.startAdvertising(mSettings, mAdvertiseData, mCallback);


    }

    private BluetoothGattServer bluetoothGattServer;
    private BluetoothGattCharacteristic characteristicRead;
    private BluetoothDevice currentDevice;

    private void initServices(Context context) {
        bluetoothGattServer = mBluetoothManager.openGattServer(context, bluetoothGattServerCallback);
        BluetoothGattService service = new BluetoothGattService(UUID_SERVER, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        //add a read characteristic.
        characteristicRead = new BluetoothGattCharacteristic(UUID_CHARREAD,
                BluetoothGattCharacteristic.PROPERTY_READ + BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        //add a descriptor
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UUID_DESCRIPTOR, BluetoothGattCharacteristic.PERMISSION_WRITE);
        characteristicRead.addDescriptor(descriptor);
        service.addCharacteristic(characteristicRead);

        //add a write characteristic.
        mCharacteristicWrite = new BluetoothGattCharacteristic(UUID_CHARWRITE,
                BluetoothGattCharacteristic.PROPERTY_WRITE
                        + BluetoothGattCharacteristic.PROPERTY_READ
                        + BluetoothGattCharacteristic.PROPERTY_NOTIFY
                ,
                BluetoothGattCharacteristic.PERMISSION_WRITE
                        + BluetoothGattCharacteristic.PERMISSION_READ
        );
        service.addCharacteristic(mCharacteristicWrite);
        mCharacteristicWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        mCharacteristicWrite.addDescriptor(descriptor);
        bluetoothGattServer.addService(service);
        Log.i(TAG, "2. initServices ok");
    }


    /**
     * 服务事件的回调
     */
    private BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {

        /**
         * 1.连接状态发生变化时
         * @param device
         * @param status
         * @param newState
         */
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.i(TAG, String.format("1.onConnectionStateChange：device name = %s, address = %s, status = %s, newState =%s", device.getName(), device.getAddress(), status, newState));
            super.onConnectionStateChange(device, status, newState);
            currentDevice = device;
            if (newState == 0) {
                mHandler.sendEmptyMessage(5);
            }

        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.i(TAG, String.format("onServiceAdded：status = %s", status));
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            Log.i(TAG, String.format("onCharacteristicReadRequest：device name = %s, address = %s, requestId = %s, offset = %s", device.getName(), device.getAddress(), requestId, offset));
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

        }

        /**
         * 3. onCharacteristicWriteRequest,接收具体的字节
         * @param device
         * @param requestId
         * @param characteristic
         * @param preparedWrite
         * @param responseNeeded
         * @param offset
         * @param requestBytes
         */
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] requestBytes) {
//            Log.i(TAG, String.format("3.onCharacteristicWriteRequest：device name = %s, address = %s", device.getName(), device.getAddress()));
            if (responseNeeded) {
                //发送给client的响应
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            }
            //4.处理响应内容
            onResponseToClient(requestBytes, device, requestId, characteristic);

        }

        /**
         * 2.描述被写入时，在这里执行 bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS...  收，触发 onCharacteristicWriteRequest
         * @param device
         * @param requestId
         * @param descriptor
         * @param preparedWrite
         * @param responseNeeded
         * @param offset
         * @param value
         */
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.i(TAG, String.format("2.onDescriptorWriteRequest：device name = %s, address = %s, value = %s", device.getName(), device.getAddress(), byte2HexStr(value)));

            // now tell the connected device that this was all successfull
            if (responseNeeded) {
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }

        /**
         * 5.特征被读取。当回复响应成功后，客户端会读取然后触发本方法
         * @param device
         * @param requestId
         * @param offset
         * @param descriptor
         */
        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            Log.i(TAG, String.format("onDescriptorReadRequest：device name = %s, address = %s,requestId = %s", device.getName(), device.getAddress(), requestId));
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
//            Log.i(TAG, String.format("5.onNotificationSent：device name = %s, address = %s, status = %s", device.getName(), device.getAddress(), status));
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
//            Log.i(TAG, String.format("onMtuChanged：mtu = %s", mtu));
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
//            Log.i(TAG, String.format("onExecuteWrite：requestId = %s", requestId));
        }
    };

    /**
     * 4.处理响应内容
     *
     * @param reqeustBytes
     * @param device
     * @param requestId
     * @param characteristic
     */
    private void onResponseToClient(byte[] reqeustBytes, BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic) {
//        Log.i(TAG, String.format("4.onResponseToClient：device name = %s, address = %s, requestId = %s", device.getName(), device.getAddress(), requestId));
        currentDevice = device;
        mGattCharacteristic = characteristic;
        //processResponse(reqeustBytes);
        Log.e("xulinchao", "message = " + byte2HexStr(reqeustBytes));
    }


    private void handleCommandResponse(byte command, byte[] message) {


    }

    public String byte2HexStr(byte[] value) {
        char[] chars = "0123456789ABCDEF".toCharArray();
        StringBuilder sb = new StringBuilder("");
        int bit;

        for (int i = 0; i < value.length; i++) {
            bit = (value[i] & 0x0F0) >> 4;
            sb.append(chars[bit]);
            bit = value[i] & 0x0F;
            sb.append(chars[bit]);
            if (i != value.length - 1) {
                sb.append('-');
            }

        }
        return "(0x) " + sb.toString().trim();
    }


    private void prepareCommands(byte commandId, byte[] cmdData) {
        Log.d(TAG, "prepareCommands()");
        if (commandQueue == null) {
            commandQueue = new LinkedList();
        } else {
            commandQueue.clear();
        }

        if (cmdData != null && cmdData.length != 0) {
            int bytesLeft = cmdData.length;
            byte cmdOrder = 0;

            for (int i = 0; bytesLeft > 0; ++i) {
                cmdOrder = cmdOrder == -128 ? 0 : cmdOrder;
                byte[] chunk;
                if (bytesLeft > maxChunkDataSize) {
                    chunk = new byte[2 + maxChunkDataSize];
                } else {
                    cmdOrder = (byte) (cmdOrder ^ 128);
                    chunk = new byte[2 + bytesLeft];
                }

                chunk[0] = commandId;
                chunk[1] = cmdOrder++;
                System.arraycopy(cmdData, i * maxChunkDataSize, chunk, 2, chunk.length - 2);
                commandQueue.add(chunk);
                bytesLeft -= chunk.length - 2;
            }

        } else {
            commandQueue.add(new byte[]{commandId, -128});
        }
    }

    public void processResponse(byte[] response) {
        Log.d(TAG, "processResponse()");
        if (response.length < 2) {
            handleError("Device response is too short");
        } else {
            if (commandQueue.isEmpty()) {
                responseBuffer.write(response, 2, response.length - 2);
                if ((response[1] >> 7 & 1) != 1) {
                    //发送 get more data
                    GET_MORE_DATA[0] = response[0];
                    mGattCharacteristic.setValue(GET_MORE_DATA);
                    bluetoothGattServer.notifyCharacteristicChanged(currentDevice, mGattCharacteristic, false);
                } else {
                    byte[] responseData = responseBuffer.toByteArray();
                    responseBuffer.reset();
                    handleCommandResponse(response[0], responseData);
                }
            } else {
                sendNextCommandChunk();
            }

        }
    }

    private void sendNextCommandChunk() {
        Log.d(TAG, "sendNextCommandChunk()");
        byte[] cmdPayload = commandQueue.poll();
        if (cmdPayload != null) {
            //write cmd
            mGattCharacteristic.setValue(cmdPayload);
            bluetoothGattServer.notifyCharacteristicChanged(currentDevice, mGattCharacteristic, false);
        } else {
            Log.d(TAG, "sendNextCommandChunk: no commands left in the queue");
        }

    }

    private void handleError(String message) {
        Log.d(TAG, "handleError: message = " + message);
    }

}
