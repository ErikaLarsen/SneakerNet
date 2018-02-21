package com.erika.networkgame;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

/**
 * Created by erikalarsen on 2/15/18.
 */

public class BluetoothConnection {
    private static final String TAG = "BluetoothConnection";
    private MainActivity activity;
    private BluetoothAdapter mBluetoothAdapter;
    private String targetName = "raspberrypi";
    private UUID rp_uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private boolean raspberryPiFound = false;
    private BluetoothSocket mSocket;
    private ConnectedThread mConnectedThread;
    private boolean connected;

    public BluetoothConnection(MainActivity activity){
        this.activity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, MainActivity.REQUEST_ENABLE_BT);
        }
        connected = false;
    }

    public void connect(){
        raspberryPiFound = false;
        // Register for broadcasts when a device is discovered.
        IntentFilter f0 = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter f1 = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        activity.registerReceiver(mReceiver,f0);
        activity.registerReceiver(mReceiver, f1);
        mBluetoothAdapter.startDiscovery();
        Log.d(TAG, "connect: ");
    }

    public void disconnect(){
        connected = false;
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch(IOException e){
                e.printStackTrace();
                Log.e(TAG, "Could not close the client socket", e);
                activity.fail("Could not close the client socket");
            }
        }
    }

    public void write(String writeData){
        if(mConnectedThread != null) {
            mConnectedThread.write(writeData.getBytes());
        }
        else{
            activity.fail("Unable to send request. No connection.");
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver =     new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            if(!raspberryPiFound) {
                String action = intent.getAction();
                if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                    activity.fail("Device not found.");
                }

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.

                    final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.d(TAG, "receiver -------------> " + device.getName() + "   " + device.getAddress());

                    if (device.getName() != null && device.getName().equals(targetName)) {
                        mBluetoothAdapter.cancelDiscovery();
                        raspberryPiFound = true;
                        Log.d(TAG, "onReceive: " + device.getName());
                        new ConnectThread(device, activity).start();

                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.found(device.getAddress());
                            }
                        });
                    }
                }
            }
        }
    };

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device, Activity activity) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createInsecureRfcommSocketToServiceRecord(rp_uuid);
                //tmp = device.createRfcommSocketToServiceRecord(rp_uuid);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
                fail("Unable to create socket.");
            }
            mmSocket = tmp;
            Log.d(TAG, tmp.toString());
        }

        public void run() {

            activity.unregisterReceiver(mReceiver);
            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                //Log.d(TAG, "trying to connect");
                mmSocket.connect();
            }

            //FAIL******************/
            catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                Log.d(TAG, connectException.toString());
                fail("Socket connection failed.");
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                /***********************  DEUG   *****************************************************/

                return;
            }
            //SUCCEED  ******************/
            Log.d(TAG, "connected to rp socket");
            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            mSocket = mmSocket;
            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
                fail("Socket close fail.");
            }
        }
    }



    public class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream
        public String message;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
                fail("Input stream fail.");
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
                fail("Failed to create input stream.");

            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

        }

        public void run() {
            connectionEstablished();

            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    Log.d(TAG, "Beginning loop");
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    Log.d(TAG, "reading");
                    byte[] mess = Arrays.copyOfRange(mmBuffer, 0, numBytes);
                    message = new String(mess);
                    Log.d(TAG, "read buffer  --->  "+ message);
                    // Send the obtained bytes to the UI activity.
                    dataArrived(message);



                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    if(connected){
                        connected = false;
                        fail("Input stream disconnected.");
                        disconnected("Input stream was disconnected.");
                    }
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                Log.d(TAG, "outstream write: "+new String(bytes));

            } catch (IOException e) {
                Log.e(TAG, "Error occurred when requesting data", e);
                /**************DEBUG  *************************************************************/
                activity.fail("Error occurred when sending request for data.\nRequest not sent.");
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
                fail("Socket close fail.");
            }
        }
    }

    private void connectionEstablished(){
        connected = true;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.connected();
            }
        });
    }

    private void dataArrived(final String data){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.dataArrived(data);
            }
        });
    }

    private void fail(final String failMessage){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.fail(failMessage);
            }
        });
    }

    private void disconnected(final String message){
        connected = false;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.disconnected("Disconnected.");
            }
        });
    }
}
