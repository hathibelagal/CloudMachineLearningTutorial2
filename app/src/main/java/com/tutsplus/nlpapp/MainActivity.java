package com.tutsplus.nlpapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.rtp.AudioStream;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.language.v1beta2.CloudNaturalLanguage;
import com.google.api.services.language.v1beta2.CloudNaturalLanguageRequestInitializer;
import com.google.api.services.language.v1beta2.model.AnnotateTextRequest;
import com.google.api.services.language.v1beta2.model.AnnotateTextResponse;
import com.google.api.services.language.v1beta2.model.Document;
import com.google.api.services.language.v1beta2.model.Entity;
import com.google.api.services.language.v1beta2.model.Features;
import com.google.api.services.speech.v1beta1.Speech;
import com.google.api.services.speech.v1beta1.SpeechRequestInitializer;
import com.google.api.services.speech.v1beta1.model.RecognitionAudio;
import com.google.api.services.speech.v1beta1.model.RecognitionConfig;
import com.google.api.services.speech.v1beta1.model.SpeechRecognitionResult;
import com.google.api.services.speech.v1beta1.model.SyncRecognizeRequest;
import com.google.api.services.speech.v1beta1.model.SyncRecognizeResponse;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String CLOUD_API_KEY = "YOUR_API_KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button browseButton = (Button)findViewById(R.id.browse_button);
        browseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent filePicker = new Intent(Intent.ACTION_GET_CONTENT);
                filePicker.setType("audio/flac");
                startActivityForResult(filePicker, 1);
            }
        });

        Button analyzeButton = (Button)findViewById(R.id.analyze_button);
        analyzeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final CloudNaturalLanguage naturalLanguageService = new CloudNaturalLanguage.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        new AndroidJsonFactory(),
                        null
                ).setCloudNaturalLanguageRequestInitializer(
                        new CloudNaturalLanguageRequestInitializer(CLOUD_API_KEY)
                ).build();

                String transcript = ((TextView)findViewById(R.id.speech_to_text_result))
                                                                            .getText().toString();

                Document document = new Document();
                document.setType("PLAIN_TEXT");
                document.setLanguage("en-US");
                document.setContent(transcript);

                Features features = new Features();
                features.setExtractEntities(true);
                features.setExtractDocumentSentiment(true);

                final AnnotateTextRequest request = new AnnotateTextRequest();
                request.setDocument(document);
                request.setFeatures(features);

                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AnnotateTextResponse response = naturalLanguageService.documents()
                                                                .annotateText(request).execute();
                            final List<Entity> entityList = response.getEntities();
                            final float sentiment = response.getDocumentSentiment().getScore();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String entities = "";
                                    for(Entity entity:entityList) {
                                        entities += "\n" + entity.getName().toUpperCase();
                                    }
                                    AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("Sentiment: " + sentiment)
                                            .setMessage("This audio file talks about : " + entities)
                                            .setNeutralButton("Okay", null)
                                            .create();
                                    dialog.show();
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK) {
            final Uri soundUri = data.getData();
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream stream = getContentResolver().openInputStream(soundUri);
                        byte[] audioData = IOUtils.toByteArray(stream);
                        stream.close();
                        String base64EncodedData = Base64.encodeBase64String(audioData);

                        // Play sound
                        MediaPlayer player = new MediaPlayer();
                        player.setDataSource(MainActivity.this, soundUri);
                        player.prepare();
                        player.start();

                        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mediaPlayer) {
                                mediaPlayer.release();
                            }
                        });

                        processSpeech(base64EncodedData);
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
            });            
        }
    }

    private void processSpeech(String data) throws IOException {
        Speech speechService = new Speech.Builder(
                AndroidHttp.newCompatibleTransport(),
                new AndroidJsonFactory(),
                null
        ).setSpeechRequestInitializer(new SpeechRequestInitializer(CLOUD_API_KEY))
                .build();

        RecognitionConfig recognitionConfig = new RecognitionConfig();
        recognitionConfig.setLanguageCode("en-US");

        RecognitionAudio recognitionAudio = new RecognitionAudio();
        recognitionAudio.setContent(data);
        SyncRecognizeRequest request = new SyncRecognizeRequest();
        request.setConfig(recognitionConfig);
        request.setAudio(recognitionAudio);

        SyncRecognizeResponse response = speechService.speech()
                                    .syncrecognize(request).execute();
        SpeechRecognitionResult result = response.getResults().get(0);

        final String transcript = result.getAlternatives().get(0).getTranscript();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView speechToTextResult = (TextView)findViewById(R.id.speech_to_text_result);
                speechToTextResult.setText(transcript);
            }
        });

    }
}
