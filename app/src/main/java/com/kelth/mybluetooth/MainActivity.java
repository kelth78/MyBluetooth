package com.kelth.mybluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.kelth.mybluetooth.BluetoothLeService.ACTION_DATA_AVAILABLE;
import static com.kelth.mybluetooth.BluetoothLeService.ACTION_GATT_CONNECTED;
import static com.kelth.mybluetooth.BluetoothLeService.ACTION_GATT_DISCONNECTED;
import static com.kelth.mybluetooth.BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED;

public class MainActivity extends Activity implements BluetoothLeService.BluetoothLeListener,
        View.OnClickListener, AdapterView.OnItemClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1; // Request code must be > 0
    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOC = 2;

    private static final int ASYNC_TASK_BLE_SCAN = 1;
    private static final int ASYNC_TASK_BLE_CONNECT = 2;
    private static final int ASYNC_TASK_BLE_DISCONNECT = 3;

    private BLEDeviceListAdapter mBLEDeviceListAdapter;
    private BLEServiceCharacteristicListAdapter mBLEServiceCharacteristicListAdapter;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private ToggleButton mToggleButtonScan;
    private Button mButtonDisconnect;
    private TextView mTextViewStatus;
    private ListView mListViewBLEDevices;
    private ListView mListViewBLEServices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Android M and above requires runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_ACCESS_COARSE_LOC);
        }

        mToggleButtonScan    = findViewById(R.id.button_scan);
        mButtonDisconnect    = findViewById(R.id.button_disconnect);
        mTextViewStatus      = findViewById(R.id.textView_status);
        mListViewBLEDevices  = findViewById(R.id.listView_ble_devices);
        mListViewBLEServices = findViewById(R.id.listView_ble_services);

        mBLEDeviceListAdapter = new BLEDeviceListAdapter();
        mBLEServiceCharacteristicListAdapter = new BLEServiceCharacteristicListAdapter();
        mBluetoothLeService = new BluetoothLeService();

        // Set adapter
        mListViewBLEDevices.setAdapter(mBLEDeviceListAdapter);
        mListViewBLEServices.setAdapter(mBLEServiceCharacteristicListAdapter);

        // click event
        mToggleButtonScan.setOnClickListener(this);
        mButtonDisconnect.setOnClickListener(this);
        mListViewBLEDevices.setOnItemClickListener(this);
        mListViewBLEServices.setOnItemClickListener(this);

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        mBluetoothLeService.init(getApplicationContext(), this);
        if (!mBluetoothLeService.isBluetoothAdapterAvailable()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // TODO Clear content
        registerReceiver(mGattUpdateReceiver, new IntentFilter(ACTION_GATT_CONNECTED));
        registerReceiver(mGattUpdateReceiver, new IntentFilter(ACTION_GATT_DISCONNECTED));
        registerReceiver(mGattUpdateReceiver, new IntentFilter(ACTION_GATT_SERVICES_DISCOVERED));
        registerReceiver(mGattUpdateReceiver, new IntentFilter(ACTION_DATA_AVAILABLE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // TODO If it's scanning, stop it
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothLeService.close();
        mBluetoothLeService.release();
    }

    /**
     * Toggle button click
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_scan:
                new asyncTask().execute(ASYNC_TASK_BLE_SCAN);
                break;
            case R.id.button_disconnect:
                new asyncTask().execute(ASYNC_TASK_BLE_DISCONNECT);
                break;
            default:
                break;
        }
    }

    /**
     * ListView click
     */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        switch (adapterView.getId()) {
            case R.id.listView_ble_devices:
                mBluetoothDevice = (BluetoothDevice) mBLEDeviceListAdapter.getItem(position);
                if (mBluetoothDevice != null) {
                    new asyncTask().execute(ASYNC_TASK_BLE_CONNECT);
                }
                break;
            case R.id.listView_ble_services:
                ServiceCharacteristics svcChar = (ServiceCharacteristics) mBLEServiceCharacteristicListAdapter.getItem(position);
                mBluetoothLeService.readGattCharactertistics(svcChar.btGattCharacteristic);
                break;
            default:
                break;
        }
    }

    /**
     * AsyncTask
     */
    private class asyncTask extends AsyncTask<Integer, Void/*progress*/, Void/*result*/> {
        @Override
        protected Void doInBackground(Integer... integers) {
            switch(integers[0]) {
                case ASYNC_TASK_BLE_SCAN:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTextViewStatus.setText(getResources().getString(R.string.ble_start_scanning));
                        }
                    });
                    mBluetoothLeService.scanLeDevice(mToggleButtonScan.isChecked());
                    break;
                case ASYNC_TASK_BLE_CONNECT:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTextViewStatus.setText(getResources().getString(R.string.ble_connecting));
                        }
                    });
                    mBluetoothLeService.connectGatt(mBluetoothDevice);
                    break;
                case ASYNC_TASK_BLE_DISCONNECT:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTextViewStatus.setText(getResources().getString(R.string.ble_disconnecting));
                        }
                    });
                    mBluetoothLeService.disconnectGatt();
                    break;
            }
            return null;
        }
    }

    /**
     * Permission request result callback
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_ACCESS_COARSE_LOC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // OK.
            } else {
                // User refused to grant permission. BLE scan will not return any results
                Toast.makeText(this, getString(R.string.ble_no_permission), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * BLE
     */
    @Override
    public void onBLEScanningStart() {
        mBLEDeviceListAdapter.clear();
        mBLEServiceCharacteristicListAdapter.clear();
    }

    @Override
    public void onBLEScanningStop() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToggleButtonScan.setChecked(false);
                mTextViewStatus.setText(getResources().getString(R.string.ble_stop_scanning));
            }
        });
    }

    @Override
    public void onBLEScanResult(final BluetoothDevice btDevice) {
        mBLEDeviceListAdapter.addDevice(btDevice);
        mBLEDeviceListAdapter.notifyDataSetChanged();
    }


    /** Handles various events fired by the Service.
     * ACTION_GATT_CONNECTED: connected to a GATT server.
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
     * ACTION_DATA_AVAILABLE: received data from the device. This can be a
     * result of read or notification operations.
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "ACTION_GATT_CONNECTED");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mButtonDisconnect.setEnabled(true);
                        mTextViewStatus.setText(getResources().getString(R.string.ble_connected));
                    }
                });
                // Discover services available
                mBluetoothLeService.discoverGattServices();
            }
            else if (ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "ACTION_GATT_DISCONNECTED");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mButtonDisconnect.setEnabled(false);
                        mTextViewStatus.setText(getResources().getString(R.string.ble_disconnected));
                    }
                });
                mBLEServiceCharacteristicListAdapter.clear();
                mBLEServiceCharacteristicListAdapter.notifyDataSetChanged();
            }
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "ACTION_GATT_SERVICES_DISCOVERED");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Show all the supported services and characteristics on the
                        // user interface.
                        List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();

                        String serviceUUID;
                        // Loops through available GATT Services.
                        for (BluetoothGattService gattService : gattServices) {

                            serviceUUID = gattService.getUuid().toString();
                            Log.d(TAG, "ACTION_GATT_SERVICES_DISCOVERED uuid: " + serviceUUID);
                            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();

                            // Loops through available Characteristics.
                            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                                UUID characteristicsUUID = gattCharacteristic.getUuid();
                                Log.d(TAG, "ACTION_GATT_SERVICES_DISCOVERED characteristic uuid: " +
                                        characteristicsUUID.toString());

                                ServiceCharacteristics svcChar = new ServiceCharacteristics();
                                svcChar.btGattCharacteristic = gattCharacteristic;
                                svcChar.serviceUUID = uuidToFriendlyServiceName(serviceUUID);
                                svcChar.characteristicUUID = uuidToFriendlyCharacteristicName(gattCharacteristic.getUuid().toString());
                                mBLEServiceCharacteristicListAdapter.addSvc(svcChar);

                                // Get characteristics properties
                                int properties = gattCharacteristic.getProperties();

                                if ((properties | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                                    // If there's an active notification. Clear it first
                                    if (mNotifyCharacteristic != null) {
                                        mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
                                        mNotifyCharacteristic = null;
                                    }
                                }

                                if ((properties | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                    mNotifyCharacteristic = gattCharacteristic;
                                    mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, true);
                                }
                            }
                            mBLEServiceCharacteristicListAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.d(TAG, "ACTION_DATA_AVAILABLE");
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, "Data: " + data);
                Toast.makeText(getApplicationContext(), data, Toast.LENGTH_SHORT).show();
            }
        }

        private String uuidToFriendlyServiceName(String uuid) {
            uuid = uuid.toUpperCase();
            if (uuid.contains("1800"))
                return "Generic Access";
            else if (uuid.contains("1801"))
                return "Generic Attribute";
            else if (uuid.contains("180A"))
                return "Device Information";
            else
                return uuid; // unknown
        }

        private String uuidToFriendlyCharacteristicName(String uuid) {
            uuid = uuid.toUpperCase();
            if (uuid.contains("2A00"))
                return "Device Name";
            else if (uuid.contains("2A01"))
                return "Appearance";
            else if (uuid.contains("2A05"))
                return "Service Changed";
            else if (uuid.contains("2A24"))
                return "Model Number";
            else if (uuid.contains("2A29"))
                return "Manufacturer Name";
            else
                return uuid; // unknown
        }
    };


    /**
     * Holder for each list item textView
     */
    static class ViewHolder {
        TextView title;
        TextView desc;
    }

    static class ServiceCharacteristics {
        BluetoothGattCharacteristic btGattCharacteristic;
        String serviceUUID;
        String characteristicUUID;
    }

    /**
     * Adapter for holding services and characteristic of selected device
     */
    private class BLEServiceCharacteristicListAdapter extends BaseAdapter {

        private ArrayList<ServiceCharacteristics> mSvcChars;
        private LayoutInflater mInflator;

        public BLEServiceCharacteristicListAdapter() {
            super();
            mSvcChars = new ArrayList<ServiceCharacteristics>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        public void clear() {
            mSvcChars.clear();
        }

        public void addSvc(ServiceCharacteristics svcChar) {
            if(!mSvcChars.contains(svcChar)) {
                mSvcChars.add(svcChar);
            }
        }

        public ServiceCharacteristics getSvcChar(int position) {
            return mSvcChars.get(position);
        }

        @Override
        public int getCount() {
            return mSvcChars.size();
        }

        @Override
        public Object getItem(int i) {
            return mSvcChars.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.list_item_ble_svc_char, null);
                viewHolder = new ViewHolder();
                viewHolder.desc = (TextView) view.findViewById(R.id.device_characteristic);
                viewHolder.title = (TextView) view.findViewById(R.id.device_service);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            ServiceCharacteristics svcChar = mSvcChars.get(i);

            if (svcChar.serviceUUID != null && svcChar.serviceUUID.length() > 0)
                viewHolder.title.setText(svcChar.serviceUUID);
            else
                viewHolder.title.setText(R.string.ble_unknown_service);
            viewHolder.desc.setText(svcChar.characteristicUUID);

            return view;
        }
    }

    /**
     * Adapter for holding devices found through scanning.
     */
    private class BLEDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public BLEDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = /*DeviceScanActivity*/MainActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.list_item_ble_device, null);
                viewHolder = new ViewHolder();
                viewHolder.desc = (TextView) view.findViewById(R.id.device_address);
                viewHolder.title = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.title.setText(deviceName);
            else
                viewHolder.title.setText(R.string.ble_unknown_device);
            viewHolder.desc.setText(device.getAddress());

            return view;
        }
    }
}