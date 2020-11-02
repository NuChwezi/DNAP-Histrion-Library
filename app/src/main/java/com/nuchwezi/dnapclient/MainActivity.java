package com.nuchwezi.dnapclient;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.nuchwezi.dnaphistrion.HistrionMainActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private void switchToDNAP_Persona(String personaUUID, String autoInstallChannel) {
        Intent _intent = new Intent(MainActivity.this, com.nuchwezi.dnaphistrion.HistrionMainActivity.class);

        if(personaUUID != null){
            _intent.putExtra(HistrionMainActivity.PERSONA_REFERENCES.KEY_PERSONA_UUID, personaUUID);
        }

        if(autoInstallChannel != null){
            _intent.putExtra(HistrionMainActivity.PERSONA_REFERENCES.AUTO_INSTALL_CHANNEL, autoInstallChannel);
        }

        startActivity(_intent);
    }

    public void launchDNAPPersona(View view) {
        try {
            switchToDNAP_Persona("bb765c31-6959-49d0-b192-6c83bdab5cb4", "NUCHWEZI2");
        }catch (Exception e){
            e.printStackTrace();
            Log.e("DNAP:Client", e.getMessage());
        }
    }

    public void launchDNAPPersonaManager(View view) {
        try {
            switchToDNAP_PersonaManager("NUCHWEZI2");
        }catch (Exception e){
            e.printStackTrace();
            Log.e("DNAP:Client", e.getMessage());
        }
    }

    private void switchToDNAP_PersonaManager(String channelSpecification) {
        Intent _intent = new Intent(MainActivity.this, com.nuchwezi.dnaphistrion.ManagePersonaActivity.class);

        if(channelSpecification != null){
            _intent.putExtra(HistrionMainActivity.PERSONA_REFERENCES.AUTO_INSTALL_CHANNEL, channelSpecification);
        }

        startActivity(_intent);
    }
}
