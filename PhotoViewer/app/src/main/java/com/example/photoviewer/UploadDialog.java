package com.example.photoviewer;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

// Post 업로드 입력 UI (dialog_upload.xml) : 제목, 내용, 이미지 선택
public class UploadDialog {
    public interface Listener {
        void onPickImage();                                    // 이미지 선택 버튼
        void onSubmit(String title, String text, Uri image);   // 게시 버튼
    }

    private final Context context;
    private final Listener listener;
    private final EditText editTitle;
    private final EditText editText;
    private final TextView textSelectedImage;
    private final AlertDialog dialog;
    private Uri imageUri;

    public UploadDialog(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_upload, null);
        editTitle = view.findViewById(R.id.edit_title);
        editText = view.findViewById(R.id.edit_text);
        textSelectedImage = view.findViewById(R.id.text_selected_image);
        view.findViewById(R.id.btn_pick_image).setOnClickListener(v -> listener.onPickImage());

        dialog = new AlertDialog.Builder(context)
                .setTitle("새로운 이미지 게시")
                .setView(view)
                .setPositiveButton("게시", null)   // 입력 검증 실패 시 닫히지 않도록 show() 후 재설정
                .setNegativeButton("취소", null)
                .create();
    }

    public void show() {
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String text = editText.getText().toString().trim();
            if (title.isEmpty() || text.isEmpty() || imageUri == null) {
                Toast.makeText(context, "제목, 내용, 이미지를 모두 입력하세요.", Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            listener.onSubmit(title, text, imageUri);
        });
    }

    // 갤러리에서 이미지를 선택한 뒤 호출
    public void setImage(Uri uri, String fileName) {
        imageUri = uri;
        textSelectedImage.setText(fileName);
    }
}
