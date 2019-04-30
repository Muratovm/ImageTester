package com.michaelmuratov.imagetester;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class SingleImageInput extends AppCompatActivity {

    private static final int READ_REQUEST_CODE = 2;
    private static final int REQUEST_READ_EXTERNAL = 1;
    private static final String TAG = "MAIN";
    Activity activity;
    Interpreter tflite;

    StringBuilder output_string = new StringBuilder();
    File path;

    int max_index;

    Bitmap scaled_bitmap;

    File file;

    TextView tv;
    ImageView image;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.single_image);
        activity = this;
        tv = findViewById(R.id.textView);
        image = findViewById(R.id.imageView);

        path  = getExternalFilesDir(null);
        file = new File(path, "output.txt");
        try{
            tflite = new Interpreter(loadModelFile());
        }catch (Exception e){
            e.printStackTrace();
        }
        Permissions.requestExternalRead(this);
        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Permissions.requestExternalRead(activity);
                performFileSearch();
            }
        });

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (!Permissions.READ_EXTERNAL_GRANTED) {
                Permissions.requestExternalRead(this);
            } else {
                Uri uri = null;
                if (resultData != null) {
                    uri = resultData.getData();
                    assert uri != null;
                    String uri_path = uri.getLastPathSegment();
                    Log.d("URI Path", uri.getLastPathSegment());
                    Log.d("External Path", Environment.getExternalStorageDirectory().getPath());
                    String directory_path = Environment.getExternalStorageDirectory().getPath()+"/"+ uri_path.substring(uri_path.indexOf(":")+1);
                    Log.d("File Path", directory_path);File directory = new File(directory_path);
                    final File[] files = directory.listFiles();

                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Log.d("Files", "" + files[files.length - 1]);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            for(File file: files){
                                if(file.getName().endsWith(".png") || file.getName().endsWith(".jpg")) {
                                    Log.d(TAG,file.toString());
                                    final Bitmap bitmap = BitmapFactory.decodeFile("" + file, options);
                                    scaled_bitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, false);

                                    int[] pixels = BitmapHelper.getBitmapPixels(scaled_bitmap, 0, 0, scaled_bitmap.getWidth(), scaled_bitmap.getHeight());

                                    float[] float_pixels = new float[pixels.length];

                                    float min = 255;
                                    float max = 0;

                                    //output_string.append("-------------------------\n");
                                    for (int i = 0; i < pixels.length; i++) {
                                        int[] rgb = BitmapHelper.unPackPixel(pixels[i]);
                                        float gray = rgb[0] * 299 / 1000 + rgb[1] * 587 / 1000 + rgb[2] * 114 / 1000;
                                        /*
                                        if(i!=0 && i%50 ==0){
                                            output_string.append("\n");
                                        }
                                        output_string.append((int)gray);
                                        output_string.append(",");
                                        for(int s = String.valueOf((int)gray).length(); s < 4; s++){
                                            output_string.append(" ");
                                        }
                                        */
                                        if(gray<min){
                                            min = gray;
                                        }
                                        if(gray>max){
                                            max = gray;
                                        }

                                        float_pixels[i] = gray;
                                    }
                                    for(int i = 0; i < pixels.length; i++){
                                        float_pixels[i] =  (float_pixels[i]-min)/(max-min);
                                    }
                                    //output_string.append("\n-------------------------\n");
                                    final String output = doInference(float_pixels);

                                    output_string.append(file.getName() + " "+max_index+"\n");
                                    runOnUiThread(new Runnable() {
                                          @Override
                                          public void run() {
                                              tv.setText(output);
                                              final BitmapDrawable drawable = new BitmapDrawable(getResources(), scaled_bitmap);
                                              drawable.getPaint().setFilterBitmap(false);
                                              image.setImageDrawable(drawable);
                                          }
                                      }
                                    );
                                }
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    FileOutputStream stream = null;
                                    try {
                                        stream = new FileOutputStream(file);
                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        stream.write(output_string.toString().getBytes());
                                        Toast.makeText(activity,"Finished writing to file",Toast.LENGTH_SHORT).show();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    } finally {
                                        try {
                                            stream.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            });
                        }
                    }).start();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_READ_EXTERNAL: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this, "Thanks!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Enable Storage Permission!", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    private String doInference(float[] pixels) {

        float[][] outputVal = new float[1][9];
        float[][][][] float_pixels = new float[1][50][50][1];


        for(int i = 0; i < 50; i++){
            for(int j = 0; j < 50; j++){
                float_pixels[0][i][j][0] = pixels[i*50+j];
            }
        }

        try{
            tflite.run(float_pixels,outputVal);
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(this,"Can't run interpreter", Toast.LENGTH_SHORT).show();
        }

        float max = 0;
        max_index = -1;

        for(int i = 0; i < outputVal[0].length; i++){
            if(outputVal[0][i] > max){
                max_index = i;
                max = outputVal[0][i];
            }
        }

        StringBuilder bins = new StringBuilder("{");
        for(int i =0; i < outputVal[0].length; i++){
            if(i == max_index){
                bins.append("1");
            }
            else{
                bins.append("0");
            }
            if(i<outputVal[0].length-1){
                bins.append(",");
            }
        }
        bins.append("}");
        return bins.toString();
    }

    private ByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
