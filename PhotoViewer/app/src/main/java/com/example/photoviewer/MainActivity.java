package com.example.photoviewer;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    ImageView imgView;
    TextView textView;
    String site_url = "http://10.0.2.2:8000";
    JSONObject post_json;
    String imageUrl = null;
    Bitmap bmImg = null;

    CloadImage taskDownload;
    PutPost taskUpload;

    // 업로드 UI(Post 입력 폼)에서 사용하는 값
    private static final int AUTHOR_ID = 1;   // Django User pk (Post.author)
    private static final String TOKEN = "231d6fe0479443af1427c040a4fb058f008a894b";
    UploadDialog uploadDialog;

    // 갤러리에서 이미지 선택 결과 수신
    private final ActivityResultLauncher<Intent> imagePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && uploadDialog != null) {
                    Uri uri = result.getData().getData();
                    uploadDialog.setImage(uri, getFileName(uri));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //imgView = (ImageView) findViewById(R.id.imgView);
        textView = (TextView) findViewById(R.id.textView);
    }

    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }

        taskDownload = new CloadImage();

        taskDownload.execute(site_url + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    public void onClickUpload(View v) {
        uploadDialog = new UploadDialog(this, new UploadDialog.Listener() {
            @Override
            public void onPickImage() {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                imagePicker.launch(intent);
            }

            @Override
            public void onSubmit(String title, String text, Uri image) {
                if (taskUpload != null && taskUpload.getStatus() == AsyncTask.Status.RUNNING) {
                    taskUpload.cancel(true);
                }
                taskUpload = new PutPost(title, text, image);
                taskUpload.execute(site_url + "/api_root/Post/");
                Toast.makeText(getApplicationContext(), "Upload", Toast.LENGTH_LONG).show();
            }
        });
        uploadDialog.show();
    }

    private String getFileName(Uri uri) {
        String name = "upload.jpg";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return name;
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmapList = new ArrayList<>();

            try {
                String apiUrl = urls[0];
                String token = "231d6fe0479443af1427c040a4fb058f008a894b";
                URL urlAPI = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    is.close();

                    String strJson = result.toString();
                    JSONArray aryJson = new JSONArray(strJson);

                    // 배열 내 모든 이미지 다운로드
                    for (int i = 0; i < aryJson.length(); i++) {
                        post_json = (JSONObject) aryJson.get(i);
                        imageUrl = post_json.getString("image");

                        if (!imageUrl.equals("")) {
                            URL myImageUrl = new URL(imageUrl);
                            conn = (HttpURLConnection) myImageUrl.openConnection();
                            InputStream imgStream = conn.getInputStream();

                            Bitmap imageBitmap = BitmapFactory.decodeStream(imgStream);
                            bitmapList.add(imageBitmap); // 이미지 리스트에 추가
                            imgStream.close();
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }

            return bitmapList;
        }

        @Override
        protected void onPostExecute(List<Bitmap> images) {
            if (images.isEmpty()) {
                textView.setText("불러올 이미지가 없습니다.");
            } else {
                textView.setText("이미지 로드 성공!");
                RecyclerView recyclerView = findViewById(R.id.recyclerView);
                ImageAdapter adapter = new ImageAdapter(images);
                recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                recyclerView.setAdapter(adapter);
            }
        }
    }

    // Post 업로드 : multipart/form-data 로 POST /api_root/Post/
    private class PutPost extends AsyncTask<String, Void, Boolean> {
        private final String title;
        private final String text;
        private final Uri imageUri;

        PutPost(String title, String text, Uri imageUri) {
            this.title = title;
            this.text = text;
            this.imageUri = imageUri;
        }

        private void writeField(DataOutputStream out, String boundary, String name, String value)
                throws IOException {
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
            out.write(value.getBytes("UTF-8"));
            out.writeBytes("\r\n");
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            String boundary = "----PhotoViewer" + System.currentTimeMillis();
            try {
                // 선택한 이미지 읽기
                ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(imageUri)) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) imageBytes.write(buf, 0, n);
                }
                String mime = getContentResolver().getType(imageUri);
                if (mime == null) mime = "image/jpeg";

                HttpURLConnection conn = (HttpURLConnection) new URL(urls[0]).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Token " + TOKEN);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(10000);

                try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                    writeField(out, boundary, "author", String.valueOf(AUTHOR_ID));
                    writeField(out, boundary, "title", title);
                    writeField(out, boundary, "text", text);

                    out.writeBytes("--" + boundary + "\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\""
                            + getFileName(imageUri) + "\"\r\n");
                    out.writeBytes("Content-Type: " + mime + "\r\n\r\n");
                    out.write(imageBytes.toByteArray());
                    out.writeBytes("\r\n--" + boundary + "--\r\n");
                    out.flush();
                }

                int code = conn.getResponseCode();
                conn.disconnect();
                return code == HttpURLConnection.HTTP_CREATED || code == HttpURLConnection.HTTP_OK;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Toast.makeText(getApplicationContext(), "업로드 성공", Toast.LENGTH_LONG).show();
                onClickDownload(null);   // 업로드 후 목록 동기화
            } else {
                Toast.makeText(getApplicationContext(), "업로드 실패", Toast.LENGTH_LONG).show();
            }
        }
    }
}
