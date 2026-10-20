package com.example.photoviewer;

import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    ImageView imgView;
    TextView textView;
    String site_url = "https://eunseokchoi.pythonanywhere.com";
    JSONObject post_json;
    String imageUrl = null;
    Bitmap bmImg = null;

    CloadImage taskDownload;
    PutPost taskUpload;

    // 업로드 UI(Post 입력 폼)에서 사용하는 값
    UploadDialog uploadDialog;

    // 로그인 정보 (로그인 성공 시 SharedPreferences 에 저장)
    SharedPreferences auth;
    TextView textUser;
    LoginDialog loginDialog;
    LoginTask taskLogin;
    SwipeRefreshLayout swipeRefresh;

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
        textUser = (TextView) findViewById(R.id.text_user);

        // 아래로 당기면 목록 새로고침
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> {
            if (getToken().isEmpty()) {
                swipeRefresh.setRefreshing(false);   // 로그인 전에는 새로고침 안 함
            } else {
                onClickDownload(null);
            }
        });

        auth = getSharedPreferences("auth", MODE_PRIVATE);
        if (getToken().isEmpty()) {
            showLogin();
        } else {
            textUser.setText(auth.getString("username", "") + " 님");
        }
    }

    private String getToken() {
        return auth.getString("token", "");
    }

    // 로그인 창 표시
    private void showLogin() {
        textUser.setText("로그인 필요");
        loginDialog = new LoginDialog(this, (username, password) -> {
            if (taskLogin != null && taskLogin.getStatus() == AsyncTask.Status.RUNNING) return;
            taskLogin = new LoginTask(username, password);
            taskLogin.execute(site_url + "/api-token-auth/");
        });
        loginDialog.show();
    }

    public void onClickLogout(View v) {
        auth.edit().clear().apply();
        textView.setText("...");
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setAdapter(null);
        Toast.makeText(getApplicationContext(), "로그아웃", Toast.LENGTH_LONG).show();
        showLogin();
    }

    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }

        taskDownload = new CloadImage();
        swipeRefresh.setRefreshing(true);

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
                String token = getToken();
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
            swipeRefresh.setRefreshing(false);
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

    // 로그인 : POST /api-token-auth/ (username, password) -> token, user_id, username
    private class LoginTask extends AsyncTask<String, Void, JSONObject> {
        private final String username;
        private final String password;

        LoginTask(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        protected JSONObject doInBackground(String... urls) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urls[0]).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) result.append(line);
                    reader.close();
                    return new JSONObject(result.toString());
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject json) {
            if (json == null) {
                Toast.makeText(getApplicationContext(), "로그인 실패", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                auth.edit()
                        .putString("token", json.getString("token"))
                        .putInt("user_id", json.optInt("user_id", 1))   // 서버가 user_id 를 주지 않으면 1 사용
                        .putString("username", json.optString("username", username))
                        .apply();
                textUser.setText(json.optString("username", username) + " 님");
                loginDialog.dismiss();
                Toast.makeText(getApplicationContext(), "로그인 성공", Toast.LENGTH_LONG).show();
                onClickDownload(null);   // 로그인 후 목록 동기화
            } catch (JSONException e) {
                e.printStackTrace();
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
                conn.setRequestProperty("Authorization", "Token " + getToken());
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(10000);

                try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                    writeField(out, boundary, "author", String.valueOf(auth.getInt("user_id", 0)));
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
