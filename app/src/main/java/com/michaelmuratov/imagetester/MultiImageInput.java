package com.michaelmuratov.imagetester;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class MultiImageInput extends AppCompatActivity {

    private static final int READ_REQUEST_CODE = 2;
    private static final int REQUEST_READ_EXTERNAL = 1;
    private static final String TAG = "MAIN";
    Activity activity;
    Interpreter tflite;

    LinkedList<float[]> float_bitmaps;

    StringBuilder output_string = new StringBuilder();
    File path;

    int max_index;

    File file;

    TextView tv;
    ImageView image;

    boolean set = false;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.multi_image);
        activity = this;
        tv = findViewById(R.id.textView);
        image = findViewById(R.id.imageView);

        path = getExternalFilesDir(null);
        file = new File(path, "output.txt");
        float_bitmaps = new LinkedList<>();
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (Exception e) {
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
                    Log.d("File Path", directory_path);
                    File directory = new File(directory_path);
                    final File[] files = directory.listFiles();
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Log.d("Files", "" + files[files.length - 1]);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            List fileList = Arrays.asList(files);
                            Collections.sort(fileList);

                            for(Object file : fileList){
                                output_string.append(((File) file).getName() +",");
                            }
                            output_string.append("\n");
                            for (Object object : fileList) {
                                File file = (File) object;
                                if (file.getName().endsWith(".png") || file.getName().endsWith(".jpg")) {
                                    Log.d(TAG, file.toString());
                                    final Bitmap bitmap = BitmapFactory.decodeFile("" + file, options);
                                    final Bitmap scaled_bitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, false);
                                    final String output = doInference(scaled_bitmap);
                                    output_string.append(max_index + "\n");
                                    /*
                                    if(output != null){

                                    }
                                    */
                                    runOnUiThread(new Runnable() {
                                                      @Override
                                                      public void run() {
                                              if(output != null) {
                                                  tv.setText(output);
                                              }
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
                                        Toast.makeText(activity, "Finished writing to file", Toast.LENGTH_SHORT).show();
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
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Thanks!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Enable Storage Permission!", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    public float[] BitmapToArray(Bitmap bitmap) {
        int[] pixels = BitmapHelper.getBitmapPixels(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight());
        float[] rgb_float_pixels = new float[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int[] rgb = BitmapHelper.unPackPixel(pixels[i]);
            float gray = rgb[0] * 299 / 1000 + rgb[1] * 587 / 1000 + rgb[2] * 114 / 1000;
            rgb_float_pixels[i] = gray;
            //Log.d("PIXEL", String.format("r:%d, g:%d, b:%d", rgb[0],rgb[1],rgb[2]));
            //Log.d("PIXEL",""+float_pixels[i]);
        }
        return rgb_float_pixels;
    }

    private String doInference(Bitmap bitmap) {
        int num_saved = 10;
        float_bitmaps.add(BitmapToArray(bitmap));
        //else if(System.currentTimeMillis() - timestamps.getLast() >= 250){
        if(float_bitmaps.size() < num_saved){
            return null;
        }
        if (float_bitmaps.size() > num_saved) {
            float_bitmaps.pop();
        }


        float[][][][] float_pixels = new float[1][50][50][num_saved];
        float[][] outputVal = new float[1][9];

        for (int i = 0; i < 50; i++) {
            for (int j = 0; j < 50; j++) {
                for (int b = 0; b < num_saved; b++) {
                    int value = (int) float_bitmaps.get(b)[i * 50 + j];
                    float_pixels[0][i][j][b] = value;
                }
            }
        }

        //set = true;

        try {
            tflite.run(float_pixels, outputVal);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Can't run interpreter", Toast.LENGTH_SHORT).show();
        }

        float max = 0;
        max_index = -1;

        for (int i = 0; i < outputVal[0].length; i++) {
            if (outputVal[0][i] > max) {
                max_index = i;
                max = outputVal[0][i];
            }
        }

        if (max_index == 4) {
            max_index = 1; //go back instead of stopping
        }

        StringBuilder bins = new StringBuilder("{");

        for (int i = 0; i < outputVal[0].length; i++) {
            if (i == max_index) {
                bins.append("1");
            } else {
                bins.append("0");
            }
            if (i < outputVal[0].length - 1) {
                bins.append(",");
            }
        }
        bins.append("}");
        return bins.toString();
    }

    private ByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("multi_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
