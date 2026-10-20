package com.example.photoviewer;

import android.graphics.Bitmap;

// 서버에서 받은 Post 1개 (id, 제목, 내용, 이미지)
public class PostItem {
    public final int id;
    public final String title;
    public final String text;
    public final Bitmap image;

    public PostItem(int id, String title, String text, Bitmap image) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.image = image;
    }
}
