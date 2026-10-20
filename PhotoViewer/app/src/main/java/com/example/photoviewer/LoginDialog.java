package com.example.photoviewer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

// 로그인 입력 UI (dialog_login.xml) : 아이디, 비밀번호
public class LoginDialog {
    public interface Listener {
        void onLogin(String username, String password);
    }

    private final Context context;
    private final Listener listener;
    private final EditText editUsername;
    private final EditText editPassword;
    private final AlertDialog dialog;

    public LoginDialog(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_login, null);
        editUsername = view.findViewById(R.id.edit_username);
        editPassword = view.findViewById(R.id.edit_password);

        dialog = new AlertDialog.Builder(context)
                .setTitle("로그인")
                .setView(view)
                .setCancelable(false)               // 로그인 전에는 닫을 수 없음
                .setPositiveButton("로그인", null)  // 검증 실패 시 닫히지 않도록 show() 후 재설정
                .create();
    }

    public void show() {
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String username = editUsername.getText().toString().trim();
            String password = editPassword.getText().toString();
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "아이디와 비밀번호를 입력하세요.", Toast.LENGTH_LONG).show();
                return;
            }
            listener.onLogin(username, password);
        });
    }

    public void dismiss() {
        dialog.dismiss();
    }
}
