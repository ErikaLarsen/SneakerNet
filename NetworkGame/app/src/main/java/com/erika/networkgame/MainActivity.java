package com.erika.networkgame;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.TestLooperManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Time;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = "MainActivity";
    public enum GameState
    {
        WELCOME, INIT_NOT_STORED, INIT_STORED, CONNECTED_NOT_STORED, CONNECTED_STORED, DATA_RECEIVED, DROP_SUCCESS, STORE_SUCCESS, DISCONNECTED
    }

    public static final int REQUEST_ENABLE_BT = 1;


    private BluetoothConnection mBluetoothConnection;

    private final String targetName = "raspberrypi";

    private SharedPreferences mPrefs;
    private SharedPreferences mSettingsPref;

    private GameState mGameState;
    private String stored_data;
    private String mSensorData = "temp data";
    private JSONObject mDataObj;

    private ImageView mNotStoredIcon;
    private ImageView mStoredIcon;

    private TextView mStoreStatus;
    private TextView mStatusMessage;
    private TextView mConnectionMessage;
    private TextView mFailMessage;
    private TextView mSuccess0;
    private TextView mSuccess1;
    private Button mCommandButton;
    private ViewGroup mMainLayout;
    private ViewGroup mFlashLayout;
    private int primaryColor;
    private int backgroundColor;

    private String mTeamName = "Blue";
    private String latency;
    private int destination = 0;
    private String getData;
    private boolean failed;
    private Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @RequiresApi(api = Build.VERSION_CODES.M)
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                    }
                });
                builder.show();
            }
        }

        mBluetoothConnection = new BluetoothConnection(this);
        failed = false;

        initializeViews();
        mGameState = GameState.WELCOME;


        ///Checking for stored data
        mSettingsPref = PreferenceManager.getDefaultSharedPreferences(this);
        mSettingsPref.edit().putBoolean("new_game_checkbox",false).commit();
        String team = mSettingsPref.getString("teamname","xxx");
        Log.d(TAG, "onCreate: teamname is "+team);
        getData = "no packet info";
        timer = new Timer();


        mPrefs = getSharedPreferences("stored_data",0);
        stored_data = mPrefs.getString("data_0","no data");
        Log.d(TAG, "onCreate: stored data: "+stored_data);
        if(stored_data.equals("no data") || stored_data == null){
            setState(GameState.INIT_NOT_STORED);
        }
        else{
            try {
                mDataObj = new JSONObject(stored_data);
                Log.d(TAG, "onCreate: JSON obj: "+mDataObj.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            setState(GameState.INIT_STORED);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: ");
        String team = mSettingsPref.getString("teamname","Blue");
        mTeamName = team;
        Log.d(TAG, "onResume: teamname is "+mTeamName);
        boolean newGame = mSettingsPref.getBoolean("new_game_checkbox",false);
        Log.d(TAG, "onCreate: newGame -->"+newGame);
        if(newGame){
            mBluetoothConnection.disconnect();
            mPrefs.edit().clear().commit();
            stored_data = null;
            mSensorData = null;
            mDataObj = null;
            setState(GameState.INIT_NOT_STORED);
            mSettingsPref.edit().putBoolean("new_game_checkbox",false).commit();
        }





    }

    private void initializeViews(){
        mStoredIcon = (ImageView) findViewById(R.id.stored_icon);
        mNotStoredIcon = (ImageView) findViewById(R.id.not_stored_icon);
        mStoreStatus = (TextView) findViewById(R.id.stored_status);
        mConnectionMessage = (TextView) findViewById(R.id.connection_message);
        mFailMessage = (TextView) findViewById(R.id.fail_message);
        mStatusMessage = (TextView) findViewById(R.id.status_message);
        mStatusMessage.setMovementMethod(new ScrollingMovementMethod());
        mCommandButton = (Button) findViewById(R.id.commandButton);
        mCommandButton.setOnClickListener(this);
        mMainLayout = (ViewGroup) findViewById(R.id.main_layout);
        mFlashLayout = (ViewGroup) findViewById(R.id.flash_layout);
        mSuccess0 = (TextView) findViewById(R.id.successText0);
        mSuccess1 = (TextView) findViewById(R.id.successText1);
        primaryColor = ContextCompat.getColor(this,R.color.colorPrimary);
        backgroundColor = ContextCompat.getColor(this,R.color.colorBackground);
    }

    public void connected(){
        Log.d(TAG, "connected: "+mGameState.toString());
        switch (mGameState){
            case INIT_NOT_STORED:
                setState(GameState.CONNECTED_NOT_STORED);
                mBluetoothConnection.write("packet");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mBluetoothConnection.write(mTeamName);
                break;
            case INIT_STORED:
                setState(GameState.CONNECTED_STORED);
                break;

            default:
                break;
        }
    }

    public void dataArrived(String data){
        Log.d(TAG, "dataArrived: "+data);
        switch (mGameState){
            case INIT_NOT_STORED:
                break;

            case CONNECTED_NOT_STORED:
                try{
                    JSONObject obj = new JSONObject(data);
                    if(obj.has("packet_present")){
                        Integer packet = obj.optInt("packet_present");
                        if(packet == 0){
                            mStatusMessage.setText("Pick up fresh data by clicking the button.");
                            getData = "data";
                        }
                        else{
                            mStatusMessage.setText("There is a packet of data waiting for you to pick up.");
                            mCommandButton.setText("get packet");
                            getData = "get_packet";
                        }
                    }
                    else{
                        mSensorData = data;
                        Log.d(TAG, "dataArrived: Not a number.   "+mSensorData);
                        setState(GameState.DATA_RECEIVED);
                        Thread.sleep(100);
                        disconnect();
                    }
                }
                catch (JSONException ex){
                    ex.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;

            case CONNECTED_STORED:
                try {
                    Log.d(TAG, "dataArrived: "+data);
                    JSONObject obj = new JSONObject(data);
                    destination = obj.getInt("Destination");
                    latency = obj.getString("Latency");
                    setState(GameState.DROP_SUCCESS);
                    disconnect();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;

            default:
                break;

        }
    }

    public void fail(String error){
        failed = true;
        Log.d(TAG, "fail: "+error+"  "+mGameState.toString());
        mFailMessage.setText(error);
        setState(mGameState);
    }

    public void disconnected(String message){
        mConnectionMessage.setText(message);
        switch (mGameState){
            case CONNECTED_NOT_STORED:
//                failed = true;
                setState(GameState.INIT_NOT_STORED);
                break;

            case CONNECTED_STORED:
                failed = true;
                setState(GameState.INIT_STORED);
                break;

            default:
                break;
        }
    }

    /*************   These methods set the UI  *******************/


    private void setStoredStatus(boolean isStored){
        if(isStored){
           // mStoredIcon.setImageDrawable(getResources().getDrawable(R.drawable.stored));
            mNotStoredIcon.setVisibility(View.GONE);
            mStoredIcon.setVisibility(View.VISIBLE);
            mStoreStatus.setText("Data stored.");
        }

        else{
            mNotStoredIcon.setVisibility(View.VISIBLE);
            mStoredIcon.setVisibility(View.GONE);
            mStoreStatus.setText("No data stored.");
        }
    }


    private void setState(GameState newGameState){
        mGameState = newGameState;
        mCommandButton.setEnabled(true);
        switch (mGameState){
            case INIT_NOT_STORED:
                Log.d(TAG, "initializeNotStored: ");
                setStoredStatus(false);
                setStatusMessage(R.string.welcome_message, R.string.connect_fail_message);
                mConnectionMessage.setText("Not connected.");
                mCommandButton.setText("Connect to device");
                mCommandButton.setEnabled(true);
                mCommandButton.setVisibility(View.VISIBLE);
                break;

            case INIT_STORED:
                Log.d(TAG, "initializeStored: ");
                setStoredStatus(true);
                setStatusMessage(R.string.init_stored_message,R.string.connect_fail_message);
                mConnectionMessage.setText("Not connected");
                mCommandButton.setText("Connect to device");
                mCommandButton.setEnabled(true);
                mCommandButton.setVisibility(View.VISIBLE);
                break;

            case CONNECTED_NOT_STORED:
                Log.d(TAG, "connected_not_stored: "+mGameState.toString());
                //setStatusMessage(R.string.connected_not_stored_message, R.string.write_fail_message);
                mCommandButton.setText("get data");
                mConnectionMessage.setText("Connected.");
                break;

            case CONNECTED_STORED:
                Log.d(TAG, "connected_stored: ");
                setStatusMessage(R.string.connected_stored_message, R.string.write_fail_message);
                mCommandButton.setText("drop data");
                mConnectionMessage.setText("Connected.");
                break;

            case DATA_RECEIVED:
                Log.d(TAG, "dataReceived: "+mGameState.toString());
                setStatusMessage(R.string.data_received_message, R.string.weird_error_message);
                mCommandButton.setText("store data");
                mConnectionMessage.setText("Disconnected.");
                parseJSON(mSensorData);
                break;

            case STORE_SUCCESS:
                Log.d(TAG, "storeSuccess: ");
                setStoredStatus(true);
                setStatusMessage(R.string.store_success_message, R.string.weird_error_message);
                mCommandButton.setText("next hop");
                mConnectionMessage.setText("Disconnected.");
                flashSuccess("Store");
                break;

            case DROP_SUCCESS:
                Log.d(TAG, "dropSuccess: ");
                mPrefs.edit().clear().commit();
                setStoredStatus(false);
                setStatusMessage(R.string.drop_success_message, R.string.weird_error_message);
                if(destination == 1){
                    Log.d(TAG, "setState: drop success-------------->"+destination);
                    mStatusMessage.append("\nThe message has reached its destination. Good work!");
                }
                if(latency != null){
                    mStatusMessage.append("\nNetwork latency: "+latency+" milliseconds");
                }
                else{
                    Log.d(TAG, "setState: no valid laptime");
                    mStatusMessage.append("\nError. No valid laptime");
                }
                mCommandButton.setVisibility(View.GONE);
                mConnectionMessage.setText("Disconnected.");
                flashSuccess("Drop");
                break;
        }

    }

    private void setStatusMessage(int successMessage, int failMessage) {
        if(failed){
            mStatusMessage.setText(failMessage);
            failed = false;
        }
        else{
            mStatusMessage.setText(successMessage);
        }
        mStatusMessage.scrollTo(0,0);
    }

    private void parseJSON(String sensorData){
        try {
            JSONObject obj = new JSONObject(sensorData);
            Integer humidity = obj.getInt("hum");
            mStatusMessage.append("humidity: "+humidity+"\n");
            Double temperature = obj.getDouble("temp");
            mStatusMessage.append("temperature: "+temperature+"\n");
            Integer light = obj.getInt("light");
            mStatusMessage.append("light: "+light+"\n");
            Integer sound = obj.getInt("sound");
            mStatusMessage.append("sound: "+sound+"\n");
            Integer gas_MQ5 = obj.getInt("gas_MQ5");
            mStatusMessage.append("gas MQ5: "+gas_MQ5+"\n");
            Integer gas_MQ3 = obj.getInt("gas_MQ3");
            mStatusMessage.append("gas MQ3: "+gas_MQ3+"\n");
            Integer gas_MQ2 = obj.getInt("gas_MQ2");
            mStatusMessage.append("gas MQ2: "+gas_MQ2+"\n");
            Integer gas_MQ9 = obj.getInt("gas_MQ9");
            mStatusMessage.append("gas MQ9: "+gas_MQ9+"\n");
            Integer hopCount = obj.getInt("hop_counter");
            mStatusMessage.append("hop count: "+hopCount+"\n");
            mStatusMessage.append("\nClick the button to store this data.");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            vibrate(100);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


/************************************************************************************/

    @Override
    public void onClick(View view) {
        mFailMessage.setText("");
        switch (mGameState) {
            case INIT_NOT_STORED:
                connect();
                break;

            case INIT_STORED:
                connect();
                break;

            case CONNECTED_NOT_STORED:
                getData();
                break;

            case DATA_RECEIVED:
                storeData(mSensorData);
                break;

            case CONNECTED_STORED:
                dropData();
                break;

            case STORE_SUCCESS:
                nextHop();
                break;

            case DROP_SUCCESS:
                setState(GameState.INIT_NOT_STORED);
                break;

            default:
                break;

        }

    }

    public void connect(){
        Log.d(TAG, "connect: connecting");
        mConnectionMessage.setText("Trying to connect...");
        mStatusMessage.setText("Trying to connect...");
        mStatusMessage.scrollTo(0,0);
        mCommandButton.setEnabled(false);

        new ConnectTask().doInBackground();
    }

    private class ConnectTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            mBluetoothConnection.connect();
            return null;
        }
    }

    private void getData(){
        mBluetoothConnection.write(getData);
        if(getData.equals("get_packet")){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mBluetoothConnection.write(mTeamName);
        }
    }

    public void storeData(String data){
        if(data != null){

            Log.d(TAG, "dataStored: "+mPrefs.getString("data_0","xxx"));
            try{
                mDataObj = new JSONObject(mSensorData);
                mDataObj.put("team",mTeamName);
            }
            catch(JSONException ex){
                ex.printStackTrace();
                fail("Unable to store data. JSON error.");
            }
            Log.d(TAG, "storeData: storing data");
            data = mDataObj.toString();
            mPrefs.edit().putString("data_0",data).commit();
            setState(GameState.STORE_SUCCESS);
        }
        else{
            Log.d(TAG, "storeData: fail - no data to store");
            fail("No data to store data.");
        }
    }

    private void dropData(){
        Log.d(TAG, "dropData: dropping data");
        stored_data =  mPrefs.getString("data_0", "xxx");
        mBluetoothConnection.write("drop");
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String outData = mDataObj.toString();
        mBluetoothConnection.write(outData);
    }

    private void nextHop(){
        setState(GameState.INIT_STORED);
    }

    private void disconnect(){
        Log.d(TAG, "disconnect: disconnecting");
        mBluetoothConnection.disconnect();
        disconnected("Disconnected.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothConnection.disconnect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_settings:
                startSettingsActivity();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startSettingsActivity(){
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }


    private void vibrate(int duration){
        if (Build.VERSION.SDK_INT >= 26) {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(duration);
        }
    }

    private void flashSuccess(String message){
        vibrate(1500);
        timer = new Timer();
        timer.schedule(new TimerTask() {
            int count = 0;
            boolean toggle = true;
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(count == 0){
                            mFlashLayout.setVisibility(View.VISIBLE);
                            mMainLayout.setVisibility(View.GONE);
                            if(mGameState == GameState.STORE_SUCCESS){
                                mSuccess0.setText("STORE");
                            }
                            else{
                                mSuccess0.setText("DROP");
                            }

                        }
                        if(toggle){
                            mFlashLayout.setBackgroundColor(backgroundColor);
                            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(primaryColor));
                            mSuccess0.setTextColor(primaryColor);
                            mSuccess1.setTextColor(primaryColor);
                            toggle = false;
                        }
                        else{
                            mFlashLayout.setBackgroundColor(primaryColor);
                            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(backgroundColor));
                            mSuccess0.setTextColor(backgroundColor);
                            mSuccess1.setTextColor(backgroundColor);
                            toggle = true;
                        }
                        count++;
                        if(count>5){
                            count=0;
                            mFlashLayout.setVisibility(View.GONE);
                            mMainLayout.setVisibility(View.VISIBLE);
                            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(primaryColor));
                            timer.cancel();
                            timer.purge();
                            timer = null;
                        }
                    }
                });
            }
        },0,300);
    }


    public void found(String address){
        mStatusMessage.append("\n\nDevice found:  "+address);
    }


}
