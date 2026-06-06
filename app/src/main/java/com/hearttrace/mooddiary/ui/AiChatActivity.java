package com.hearttrace.mooddiary.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.utils.DoubaoApiClient;
import java.util.ArrayList;
import java.util.List;

public class AiChatActivity extends AppCompatActivity {

    private ListView lvChat;
    private EditText etInput;
    private Button btnSend;
    private ChatAdapter adapter;
    private List<ChatMessage> chatList;
    private final DoubaoApiClient apiClient = new DoubaoApiClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        lvChat = findViewById(R.id.lv_chat);
        etInput = findViewById(R.id.et_input);
        btnSend = findViewById(R.id.btn_send);

        chatList = new ArrayList<>();
        adapter = new ChatAdapter();
        lvChat.setAdapter(adapter);

        // 欢迎语
        chatList.add(new ChatMessage("你好呀！我是你的情绪陪伴AI，随时可以和我聊聊心情~", false));
        adapter.notifyDataSetChanged();

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String userInput = etInput.getText().toString().trim();
        if (userInput.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }

        // 添加用户消息
        chatList.add(new ChatMessage(userInput, true));
        adapter.notifyDataSetChanged();
        etInput.setText("");

        // 子线程执行网络请求
        new Thread(() -> {
            try {
                // 组装对话列表
                List<DoubaoApiClient.Message> msgList = new ArrayList<>();
                // 系统角色设定
                msgList.add(new DoubaoApiClient.Message("system",
                        "你是一个温柔治愈的情绪陪伴助手，用户正在写情绪日记，用温暖共情的语气回应，舒缓用户情绪。"));

                // 拼接历史对话
                for (ChatMessage cm : chatList) {
                    String role = cm.isUser ? "user" : "assistant";
                    msgList.add(new DoubaoApiClient.Message(role, cm.content));
                }

                // 调用接口
                String aiResult = apiClient.sendChatRequest(msgList);

                // 主线程更新UI
                mainHandler.post(() -> {
                    chatList.add(new ChatMessage(aiResult, false));
                    adapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                Log.e("AI_CHAT_ERR", "请求异常", e); // 保留这个，Logcat会输出完整报错栈
                mainHandler.post(() -> {
                    Toast.makeText(AiChatActivity.this, "请求失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // 聊天消息实体
    static class ChatMessage {
        String content;
        boolean isUser;
        ChatMessage(String content, boolean isUser) {
            this.content = content;
            this.isUser = isUser;
        }
    }

    // 列表适配器
    private class ChatAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return chatList.size();
        }
        @Override
        public Object getItem(int position) {
            return chatList.get(position);
        }
        @Override
        public long getItemId(int position) {
            return position;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ChatMessage msg = chatList.get(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(AiChatActivity.this)
                        .inflate(msg.isUser ? R.layout.item_chat_user : R.layout.item_chat_ai, parent, false);
            }
            TextView tvContent = convertView.findViewById(R.id.tv_content);
            tvContent.setText(msg.content);
            return convertView;
        }
    }
}