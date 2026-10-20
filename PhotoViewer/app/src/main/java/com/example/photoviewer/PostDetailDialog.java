package com.example.photoviewer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

// 선택한 게시물 확인 UI (dialog_post_detail.xml) : 이미지(확대), 제목, 내용
public class PostDetailDialog {
    private final AlertDialog dialog;

    public PostDetailDialog(Context context, PostItem post) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_post_detail, null);
        ((ImageView) view.findViewById(R.id.image_detail)).setImageBitmap(post.image);
        ((TextView) view.findViewById(R.id.text_detail_title)).setText(post.title);
        ((TextView) view.findViewById(R.id.text_detail_text)).setText(post.text);

        dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setPositiveButton("닫기", null)
                .create();
    }

    public void show() {
        dialog.show();
    }
}
