package com.example.iotassignmentone;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

import java.io.UnsupportedEncodingException;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    //globals
    final private String TAG = "CONNECTION";
    final private String TAG1 = "SUBSCRIPTION";
    final private String TAG2 = "ISSUE";
    private int msg1 = 0;
    private int msg2 = 0;
    private int msg3 = 0;
    private int msg4 = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //setting up buttons / text
        final ToggleButton tglBtnTemnp = (ToggleButton) findViewById(R.id.tgbtnTemp);
        final ToggleButton tglBtnLed = (ToggleButton) findViewById(R.id.tgbtnLED);
        final ToggleButton tglBtnMotSen = (ToggleButton) findViewById(R.id.tglBtnMotSen);
        final ToggleButton tglBtnLCD = (ToggleButton) findViewById(R.id.tglBtnLCD);
        final EditText edTxtSmpTemp = findViewById(R.id.edTxtSmpTemp);
        final EditText edTxtSmpLed = findViewById(R.id.edTxtSmpLed);
        final EditText edTxtSmpMotsen = findViewById(R.id.edTxtSmpMotsen);
        final EditText editTextSendText = findViewById(R.id.editTextSendText);

        //mqtt instruction topics
        final String topInstTemp = "sensors/instruction/temp";
        final String topInstLed = "sensors/instruction/led";
        final String topInstMotSen = "sensors/instruction/motsen";
        final String topInstLCD = "sensors/instruction/lcd";


        //assigning variables for server connection
        final String SERVER = "";
        final String USER = "c";
        final String PW = "";
        final String CLIENTID = MqttClient.generateClientId();

        //creating Mqtt android client
        final MqttAndroidClient myClient = new MqttAndroidClient(this.getApplicationContext(), SERVER
                , CLIENTID);

        //adding connection info into connection options
        MqttConnectOptions connOptions = new MqttConnectOptions();
        connOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
        connOptions.setCleanSession(false);
        connOptions.setUserName(USER);
        connOptions.setPassword(PW.toCharArray());

        //Connecting to MQTTCloud services
        try {
            IMqttToken token = myClient.connect(connOptions);
            startMqtt(myClient, token, tglBtnLCD, editTextSendText);
        } catch (MqttException e) {
            e.printStackTrace();
        }

        //Button listensers
        tglBtnTemnp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manageBtnState(tglBtnTemnp, edTxtSmpTemp, myClient, topInstTemp);

            }
        });

        tglBtnLed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manageBtnState(tglBtnLed, edTxtSmpLed, myClient, topInstLed);
            }
        });

        tglBtnMotSen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manageBtnState(tglBtnMotSen, edTxtSmpMotsen, myClient, topInstMotSen);

            }
        });
        //LCD button has its own logic as it awaits message from PI to turn it into "unchecked" state
        tglBtnLCD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                boolean on = tglBtnLCD.isChecked();
                int x = new Random().nextInt(3);
                String rand = String.valueOf(x);
                if(on){
                    String message = editTextSendText.getText().toString();
                    if(message.length() > 0){
                        publish(myClient,message, topInstLCD);
                        tglBtnLCD.setEnabled(false);
                        disableEditText(editTextSendText);
                    }else{
                        Toast.makeText(getApplicationContext(), "Please enter text into the field", Toast.LENGTH_SHORT).show();
                        tglBtnLCD.toggle();
                    }
                }
            }
        });


    }

    //managing button state and disabling input area when button is in "checked" state
    private void manageBtnState(ToggleButton toggleButton, EditText editText, MqttAndroidClient myClient, String topic) {
        boolean on = toggleButton.isChecked();
        if (on) {
            try {
                int sampleRate = Integer.valueOf(editText.getText().toString());
                if (validateSmpRate(sampleRate)) {
                    //testing
                    publish(myClient, editText.getText().toString(), topic);
                    disableEditText(editText);

                } else {
                    warnUser();
                    toggleButton.toggle();
                }

            } catch (Exception e) {
                warnUser();
                toggleButton.toggle();
            }
        } else {

            enableEditText(editText);
            publish(myClient, "0", topic);

        }

    }

      public void warnUser(){
        Toast.makeText(getApplicationContext(),"Please enter a positive integer",Toast.LENGTH_SHORT).show();
    }

    private boolean validateSmpRate(int rate){
        return rate > 0 ? true : false ;
    }

    private void disableEditText(EditText editText) {
        editText.setKeyListener(null);
        editText.setTextColor(Color.LTGRAY);
    }

    private void enableEditText(EditText editText) {
        editText.setKeyListener(new EditText(getApplicationContext()).getKeyListener());
        editText.setTextColor(Color.BLACK);
    }



    //MQTT FUNCTIONS
    //CONNECTION AND MESSAGE RECEPTION
    public void startMqtt(final MqttAndroidClient mqttClient, IMqttToken token, final ToggleButton button, final EditText text){

        //connection stuff

            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "CONNECTION SUCCESSFUL");
                    Toast.makeText(MainActivity.this, "Successfully connected to server!", Toast.LENGTH_SHORT).show();

                    //subscribe to revelant channels
                    subscribe(mqttClient,"sensors/value/temp");
                    subscribe(mqttClient,"sensors/value/hum");
                    subscribe(mqttClient,"sensors/value/led");
                    subscribe(mqttClient,"sensors/value/motsen");
                    subscribe(mqttClient,"sensors/value/lcd");
                    mqttClient.setCallback(new MqttCallback() {
                        @Override
                        public void connectionLost(Throwable cause) {
                            Log.d(TAG, "CONNECTION LOST: ");
                        }
                        @Override
                        public void messageArrived(String topic, MqttMessage message) {

                            TextView txtTemp = findViewById(R.id.temp);
                            TextView txtHum = findViewById(R.id.hum);
                            TextView txtLED = findViewById(R.id.led);
                            TextView txtMotSen = findViewById(R.id.motsen);
                            TextView txtMsgTotal = findViewById(R.id.msg6);
                            TextView txtMsgDht = findViewById(R.id.msg1);
                            TextView txtMsgLed = findViewById(R.id.msg2);
                            TextView txtMsgMotSen = findViewById(R.id.msg3);
                            TextView txtMsgLCD = findViewById(R.id.msg4);
                            //putting messages away
                            String strMessage = message.toString();
                            String result = "";
                                     switch (topic){
                                case "sensors/value/temp":
                                    txtTemp.setText(strMessage + " C");
                                    msg1++;
                                    txtMsgDht.setText( String.valueOf(msg1));
                                    break;
                                case "sensors/value/hum":
                                    txtHum.setText(strMessage + " %");
                                    msg1++;
                                    txtMsgDht.setText(String.valueOf(msg1));
                                    break;
                                case "sensors/value/led":
                                    result = strMessage.equalsIgnoreCase("1") ? "ON" : "OFF";
                                    txtLED.setText(result);
                                    msg2++;
                                    txtMsgLed.setText(String.valueOf(msg2));
                                    break;
                                case "sensors/value/motsen":
                                    result = strMessage.equalsIgnoreCase("0") ? "OFF" : strMessage + " cm" ;
                                    txtMotSen.setText(result);
                                    msg3++;
                                    txtMsgMotSen.setText(String.valueOf(msg3));
                                    break;
                                case "sensors/value/lcd":
                                    enableEditText(text);
                                    text.setText("");
                                    msg4++;
                                    txtMsgLCD.setText(String.valueOf(msg4));
                                    button.setEnabled(true);
                                    button.setChecked(false);
                                    break;
                                default:
                                    Log.d(TAG2, "UNKNOWN MESSAGE RECEIVED");
                            }

                            txtMsgTotal.setText(String.valueOf(msg1 + msg2 + msg3 + msg4));
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken token) {
                            Log.d(TAG, "DELIVERY RECEIVED");
                        }
                    });
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "DELIVERY FAILED", exception);
                }
            });
        } {

        }




    //SUBSCRIBING TO TOPICS IN MQTTCLOUD SERVICE
    public void subscribe(MqttAndroidClient mqttAndroidClient, final String topic){
        int qos = 1;
        try{
            IMqttToken iMqttToken = mqttAndroidClient.subscribe(topic,qos);
            iMqttToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG1, "SUCCESSFULLY SUBSCRIBED TO TOPIC: " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG1, "SUBSCRIPTION UNSUCCESSFUL: " + topic);
                }
            });

        } catch (MqttSecurityException e) {
            e.printStackTrace();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    //PUBLISH A MESSAGE TO MQTTCLOUD
    public void publish(MqttAndroidClient mqttAndroidClient, String instruction, String topic){

        byte[] bytes = new byte[0];

        try {
            bytes = instruction.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(bytes);
            mqttAndroidClient.publish(topic, message);

        } catch (MqttException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

}
