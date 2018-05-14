package com.kelth.mybluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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

public class MainActivity extends Activity implements BluetoothLeService.BluetoothLeListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1; // Request code must be > 0
    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOC = 2;

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private LeSvcCharListAdapter mLeSvcCharListAdapter;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        mLeSvcCharListAdapter = new LeSvcCharListAdapter();

        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Android M and above requires user to manually grant it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_ACCESS_COARSE_LOC);
        }

        // Set adapter for ble devices list view
        ListView lv = findViewById(R.id.listView_ble_devices);
        lv.setAdapter(mLeDeviceListAdapter);

        ListView lv2 = findViewById(R.id.listView_ble_services);
        lv2.setAdapter(mLeSvcCharListAdapter);

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        mBluetoothLeService = new BluetoothLeService();
        mBluetoothLeService.init(getApplicationContext(), this);
        if (!mBluetoothLeService.isBluetoothAdapterAvailable()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Scan Button click event
        ToggleButton btnScan = findViewById(R.id.button_scan);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBluetoothLeService.scanLeDevice(((ToggleButton)view).isChecked());
            }
        });

        // Connection Button click event
        Button btnConn = findViewById(R.id.button_connection);
        btnConn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (((ToggleButton)view).isChecked()) {
                    if (mBluetoothDevice != null) {
                        mBluetoothLeService.connectGatt(mBluetoothDevice);
                    }
                    else {
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.ble_unknown_device),
                                Toast.LENGTH_LONG).show();
                        ((ToggleButton) view).setChecked(false); // Restore
                    }
                }
                else {
                    mBluetoothLeService.disconnectGatt();
                }
            }
        });

        // ListView Device click event
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                mBluetoothDevice = (BluetoothDevice)mLeDeviceListAdapter.getItem(position);
                mBluetoothLeService.connectGatt(mBluetoothDevice);
            }
        });

        // ListView Device's service-characteristic click event
        lv2.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                ServiceCharacteristics svcChar = (ServiceCharacteristics)mLeSvcCharListAdapter.getItem(position);
                mBluetoothLeService.readGattCharactertistics(svcChar.btGattCharacteristic);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, new IntentFilter(ACTION_GATT_CONNECTED));
        registerReceiver(mGattUpdateReceiver, new IntentFilter(ACTION_GATT_DISCONNECTED));
        registerReceiver(mGattUpdateReceiver, new IntentFilter(ACTION_GATT_SERVICES_DISCOVERED));
        registerReceiver(mGattUpdateReceiver, new IntentFilter(ACTION_DATA_AVAILABLE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothLeService.close();
        mBluetoothLeService.release();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_ACCESS_COARSE_LOC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //doLocationAccessRelatedJob();
            } else {
                // User refused to grant permission. You can add AlertDialog here
                Toast.makeText(this, getString(R.string.ble_no_permission), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBLEScanningStart() {
        mLeDeviceListAdapter.clear();
        mLeSvcCharListAdapter.clear();
    }

    @Override
    public void onBLEScanningStop() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToggleButton btn = findViewById(R.id.button_scan);
                btn.setChecked(false);
            }
        });
    }

    @Override
    public void onBLEScanResult(final BluetoothDevice btDevice) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLeDeviceListAdapter.addDevice(btDevice);
                mLeDeviceListAdapter.notifyDataSetChanged();
            }
        });
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
                        ((ToggleButton)findViewById(R.id.button_connection)).setChecked(true);
                    }
                });

                // Device connected. Discover services available
                mBluetoothLeService.discoverGattServices();
            }
            else if (ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "ACTION_GATT_DISCONNECTED");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((ToggleButton)findViewById(R.id.button_connection)).setChecked(false);
                        mLeSvcCharListAdapter.clear();
                        mLeSvcCharListAdapter.notifyDataSetChanged();
                    }
                });
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
                                mLeSvcCharListAdapter.addSvc(svcChar);

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
                            mLeSvcCharListAdapter.notifyDataSetChanged();
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
    private class LeSvcCharListAdapter extends BaseAdapter {

        private ArrayList<ServiceCharacteristics> mSvcChars;
        private LayoutInflater mInflator;

        public LeSvcCharListAdapter() {
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
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
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