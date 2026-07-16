package com.hamraj37.somechat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.adapters.MessageAdapter;
import com.hamraj37.somechat.models.Message;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AIChatActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private MessageAdapter adapter;
    private final List<Message> messageList = new ArrayList<>();
    private EditText messageInput;
    private LinearProgressIndicator typingIndicator;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnSend;
    private boolean isAiProcessing = false;
    private Call currentCall;
    private String myUid;
    private DatabaseReference chatRef;
    private ValueEventListener chatListener;
    private Message streamingMsg;
    private static final String AI_ID = "somechat_ai";
    private final OkHttpClient client = new OkHttpClient();
    private String currentModelId = "tencent/hy3:free";
    private static final String OPENROUTER_API_KEY = BuildConfig.OPENROUTER_API_KEY;

    private static final String PREFS_NAME = "AIChatPrefs";
    private static final String KEY_MODEL_ID = "selected_model_id";
    private static final String KEY_MODEL_SHORT_NAME = "selected_model_short_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);
        
        setContentView(R.layout.activity_ai_chat);

        myUid = FirebaseAuth.getInstance().getUid();
        chatRef = FirebaseDatabase.getInstance().getReference("ai_chats").child(myUid);

        // Load saved model
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentModelId = prefs.getString(KEY_MODEL_ID, "tencent/hy3:free");
        String savedShortName = prefs.getString(KEY_MODEL_SHORT_NAME, "Hunyuan 3");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        recyclerView = findViewById(R.id.ai_chat_recycler);
        messageInput = findViewById(R.id.ai_message_input);
        typingIndicator = findViewById(R.id.ai_typing_indicator);
        android.widget.TextView aiStatus = findViewById(R.id.ai_status);
        aiStatus.setText(getString(R.string.ai_online, savedShortName));

        findViewById(R.id.ai_header_container).setOnClickListener(this::showModelSelectionMenu);
        findViewById(R.id.btn_ai_about).setOnClickListener(v -> showAboutDialog());
        findViewById(R.id.btn_ai_new_chat).setOnClickListener(v -> startNewChat());

        setupSuggestions();

        adapter = new MessageAdapter(messageList, new MessageAdapter.OnMessageClickListener() {
            @Override public void onReplyClick(String messageId) {}
            @Override public void onMessageLongClick(Message message, View view) {}
            @Override public void onMessageClick(Message message) {}
            @Override public void onSelectionChanged(int count) {}
            @Override public void onReactionClick(Message message, String emoji) {}
        });
        adapter.setAi(true);
        adapter.setShowHeader(false);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom) {
                recyclerView.postDelayed(() -> {
                    if (!messageList.isEmpty()) {
                        recyclerView.scrollToPosition(messageList.size() - 1);
                    }
                }, 100);
            }
        });

        btnSend = findViewById(R.id.btn_ai_send);
        btnSend.setOnClickListener(v -> {
            if (isAiProcessing) {
                stopAiResponse();
            } else {
                sendMessage();
            }
        });

        messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                findViewById(R.id.ai_suggestions_scroll).setVisibility(s.length() > 0 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        loadMessages();
    }

    private void loadMessages() {
        chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messageList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Message message = ds.getValue(Message.class);
                    if (message != null) {
                        messageList.add(message);
                    }
                }

                // Maintain streaming message if it's active
                if (streamingMsg != null) {
                    boolean alreadyInList = false;
                    for (Message m : messageList) {
                        if (m.getMessageId() != null && m.getMessageId().equals(streamingMsg.getMessageId())) {
                            alreadyInList = true;
                            break;
                        }
                    }
                    if (!alreadyInList) {
                        messageList.add(streamingMsg);
                        // Re-sort if we added streaming message manually to keep it at the end
                        messageList.sort((m1, m2) -> Long.compare(m1.getTimestamp(), m2.getTimestamp()));
                    }
                }

                if (messageList.isEmpty()) {
                    addAiMessage(getString(R.string.ai_greeting));
                } else {
                    adapter.notifyDataSetChanged();
                    recyclerView.scrollToPosition(messageList.size() - 1);

                    // Update suggestions based on the last message if it's from AI and we aren't waiting for a new response
                    Message lastMsg = messageList.get(messageList.size() - 1);
                    if (AI_ID.equals(lastMsg.getSenderId()) && !isAiProcessing) {
                        if (messageList.size() == 1 && lastMsg.getText().equals(getString(R.string.ai_greeting))) {
                            showDefaultSuggestions();
                        } else {
                            showFollowUpSuggestions(lastMsg.getText());
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        chatRef.orderByChild("timestamp").addValueEventListener(chatListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatRef != null && chatListener != null) {
            chatRef.removeEventListener(chatListener);
        }
        stopAiResponse();
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        messageInput.setText("");
        
        String msgId = chatRef.push().getKey();
        if (msgId == null) msgId = UUID.randomUUID().toString();
        
        Message userMsg = new Message(msgId, myUid, AI_ID, text, System.currentTimeMillis());
        if (chatRef != null) {
            chatRef.child(userMsg.getMessageId()).setValue(userMsg);
        }

        getAiResponse(text);
    }

    private void getAiResponse(String userText) {
        if (OPENROUTER_API_KEY == null || OPENROUTER_API_KEY.isEmpty() || OPENROUTER_API_KEY.equals("YOUR_API_KEY_HERE")) {
            Toast.makeText(this, "AI Error: API Key not configured", Toast.LENGTH_LONG).show();
            return;
        }

        setAiProcessing(true);

        try {
            JSONObject root = new JSONObject();
            root.put("model", currentModelId);
            
            JSONArray messages = new JSONArray();
            
            // Add system prompt
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are SomeChat AI, a helpful and friendly assistant integrated into the SomeChat messaging app.");
            messages.put(systemMsg);

            // Add conversation history (last 10 messages from list)
            int historyStart = Math.max(0, messageList.size() - 10);
            boolean currentMsgInHistory = false;
            for (int i = historyStart; i < messageList.size(); i++) {
                Message msg = messageList.get(i);
                if (msg == null || msg.getText() == null || msg.getText().isEmpty()) continue;
                
                JSONObject histMsg = new JSONObject();
                histMsg.put("role", AI_ID.equals(msg.getSenderId()) ? "assistant" : "user");
                histMsg.put("content", msg.getText());
                messages.put(histMsg);
                
                if (userText.equals(msg.getText())) {
                    currentMsgInHistory = true;
                }
            }

            // If the message hasn't synced to the list yet, add it manually
            if (!currentMsgInHistory) {
                JSONObject currentMsg = new JSONObject();
                currentMsg.put("role", "user");
                currentMsg.put("content", userText);
                messages.put(currentMsg);
            }
            
            root.put("messages", messages);
            root.put("stream", true);

            RequestBody body = RequestBody.create(root.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .post(body)
                    .addHeader("Authorization", "Bearer " + OPENROUTER_API_KEY)
                    .addHeader("HTTP-Referer", "https://github.com/hamraj37/SomeChat")
                    .addHeader("X-Title", "SomeChat")
                    .build();

            // Use a specific AI message for streaming
            String aiMsgId = chatRef.push().getKey();
            if (aiMsgId == null) aiMsgId = UUID.randomUUID().toString();
            
            streamingMsg = new Message(aiMsgId, AI_ID, myUid, "", System.currentTimeMillis());
            final StringBuilder aiContent = new StringBuilder();

            currentCall = client.newCall(request);
            currentCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (call.isCanceled()) return;
                    runOnUiThread(() -> {
                        setAiProcessing(false);
                        Toast.makeText(AIChatActivity.this, "AI Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        runOnUiThread(() -> {
                            setAiProcessing(false);
                            Toast.makeText(AIChatActivity.this, "AI Error: " + response.code(), Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    if (response.body() == null) {
                        setAiProcessing(false);
                        return;
                    }

                    okio.BufferedSource source = response.body().source();
                    while (!source.exhausted()) {
                        String line = source.readUtf8Line();
                        if (line != null && line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if (data.equals("[DONE]")) break;

                            try {
                                JSONObject chunk = new JSONObject(data);
                                if (chunk.has("choices")) {
                                    JSONObject delta = chunk.getJSONArray("choices").getJSONObject(0).getJSONObject("delta");
                                    if (delta.has("content")) {
                                        String content = delta.getString("content");
                                        aiContent.append(content);

                                        runOnUiThread(() -> {
                                            int existingIndex = messageList.indexOf(streamingMsg);
                                            if (existingIndex == -1) {
                                                typingIndicator.setVisibility(View.GONE);
                                                messageList.add(streamingMsg);
                                                adapter.notifyItemInserted(messageList.size() - 1);
                                            }
                                            streamingMsg.setText(aiContent.toString());
                                            int currentIndex = messageList.indexOf(streamingMsg);
                                            if (currentIndex != -1) {
                                                adapter.notifyItemChanged(currentIndex);
                                                recyclerView.scrollToPosition(currentIndex);
                                            }
                                        });
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    runOnUiThread(() -> {
                        setAiProcessing(false);
                        if (streamingMsg != null && chatRef != null) {
                            chatRef.child(streamingMsg.getMessageId()).setValue(streamingMsg);
                            showFollowUpSuggestions(streamingMsg.getText());
                            streamingMsg = null;
                        }
                    });
                }
            });

        } catch (Exception e) {
            setAiProcessing(false);
            e.printStackTrace();
        }
    }

    private void setAiProcessing(boolean processing) {
        isAiProcessing = processing;
        runOnUiThread(() -> {
            typingIndicator.setVisibility(processing ? View.VISIBLE : View.GONE);
            if (processing) {
                findViewById(R.id.ai_suggestions_scroll).setVisibility(View.GONE);
            } else if (messageInput.getText().length() == 0) {
                findViewById(R.id.ai_suggestions_scroll).setVisibility(View.VISIBLE);
            }
            messageInput.setEnabled(!processing);
            btnSend.setImageResource(processing 
                    ? android.R.drawable.ic_menu_close_clear_cancel 
                    : android.R.drawable.ic_menu_send);
        });
    }

    private void stopAiResponse() {
        if (currentCall != null) {
            currentCall.cancel();
        }
        setAiProcessing(false);
    }

    private void addAiMessage(String text) {
        String msgId = chatRef.push().getKey();
        if (msgId == null) msgId = UUID.randomUUID().toString();
        
        Message aiMsg = new Message(msgId, AI_ID, myUid, text, System.currentTimeMillis());
        if (chatRef != null) {
            chatRef.child(aiMsg.getMessageId()).setValue(aiMsg);
        }
    }

    private void setupSuggestions() {
        showDefaultSuggestions();
    }

    private void showDefaultSuggestions() {
        updateSuggestions(new String[]{
                getString(R.string.ai_suggest_joke),
                getString(R.string.ai_suggest_poem),
                getString(R.string.ai_suggest_code),
                getString(R.string.ai_suggest_email),
                getString(R.string.ai_suggest_translate),
                getString(R.string.ai_suggest_story)
        });
    }

    private void showFollowUpSuggestions(String lastAiResponse) {
        List<String> followUps = new ArrayList<>();
        
        // Try to parse bullet points from the AI response as dynamic suggestions
        if (lastAiResponse != null) {
            String[] lines = lastAiResponse.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if ((trimmed.startsWith("- ") || trimmed.startsWith("* ")) && trimmed.length() > 2 && trimmed.length() < 50) {
                    String suggestion = trimmed.substring(2).trim();
                    // Remove trailing punctuation
                    if (suggestion.endsWith(".") || suggestion.endsWith("!") || suggestion.endsWith(",")) {
                        suggestion = suggestion.substring(0, suggestion.length() - 1);
                    }
                    if (!suggestion.isEmpty() && !followUps.contains(suggestion)) {
                        followUps.add(suggestion);
                    }
                }
                if (followUps.size() >= 6) break;
            }
        }

        if (followUps.isEmpty()) {
            followUps.add(getString(R.string.ai_suggest_more));
            followUps.add(getString(R.string.ai_suggest_explain));
        }
        
        if (lastAiResponse != null && (lastAiResponse.toLowerCase().contains("code") || lastAiResponse.contains("```"))) {
            if (!followUps.contains(getString(R.string.ai_suggest_code_explain))) {
                followUps.add(getString(R.string.ai_suggest_code_explain));
            }
        }
        
        if (lastAiResponse != null && lastAiResponse.length() > 500) {
            if (!followUps.contains(getString(R.string.ai_suggest_summarize))) {
                followUps.add(getString(R.string.ai_suggest_summarize));
            }
        }
        
        if (!followUps.contains(getString(R.string.ai_suggest_example))) {
            followUps.add(getString(R.string.ai_suggest_example));
        }

        updateSuggestions(followUps.toArray(new String[0]));
    }

    private void updateSuggestions(String[] suggestions) {
        ChipGroup suggestionsGroup = findViewById(R.id.ai_suggestions_group);
        if (suggestionsGroup == null) return;
        
        suggestionsGroup.removeAllViews();
        if (suggestions == null) return;

        for (String suggestion : suggestions) {
            Chip chip = new Chip(this);
            chip.setText(suggestion);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setChipMinHeight(40f);
            chip.setTextSize(12f);
            chip.setOnClickListener(v -> {
                messageInput.setText(suggestion);
                sendMessage();
            });
            suggestionsGroup.addView(chip);
        }
    }

    private void startNewChat() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_chat)
                .setMessage(R.string.clear_chat_confirmation)
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.new_chat, (dialog, which) -> {
                    if (chatRef != null) {
                        chatRef.removeValue();
                        messageList.clear();
                        adapter.notifyDataSetChanged();
                        showDefaultSuggestions();
                    }
                })
                .show();
    }

    private void showModelSelectionMenu(View v) {
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(this, v);
        String[] models = {
                "google/gemma-4-26b-a4b-it:free",
                "tencent/hy3:free",
                "poolside/laguna-xs-2.1:free",
                "nvidia/nemotron-3-super-120b-a12b:free",
                "qwen/qwen3-coder:free"
        };
        String[] modelNames = {
                "Gemma 4 26B (Google)",
                "Hunyuan 3 (Tencent)",
                "Laguna XS 2.1 (Poolside)",
                "Nemotron 3 Super 120B (NVIDIA)",
                "Qwen 3 Coder (Alibaba)"
        };
        String[] shortNames = {
                "Gemma 4",
                "Hunyuan 3",
                "Laguna XS",
                "Nemotron 3",
                "Qwen 3"
        };

        for (int i = 0; i < models.length; i++) {
            popupMenu.getMenu().add(0, i, i, modelNames[i]);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int index = item.getItemId();
            currentModelId = models[index];
            String shortName = shortNames[index];
            
            android.widget.TextView aiStatus = findViewById(R.id.ai_status);
            aiStatus.setText(getString(R.string.ai_online, shortName));

            // Save selection
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_MODEL_ID, currentModelId)
                    .putString(KEY_MODEL_SHORT_NAME, shortName)
                    .apply();

            Toast.makeText(this, "Model changed to: " + modelNames[index], Toast.LENGTH_SHORT).show();
            return true;
        });
        popupMenu.show();
    }

    private void showAboutDialog() {
        String title = "";
        String message = "";
        
        if (currentModelId.contains("gemma-4")) {
            title = "About Gemma 4";
            message = "Developed by Google. A state-of-the-art model with 26B parameters, designed for high-quality reasoning, creative writing, and complex problem-solving.";
        } else if (currentModelId.contains("hy3")) {
            title = "About Hunyuan 3";
            message = "Developed by Tencent. A highly intelligent and versatile model with expertise in SEO, Programming, Science, Technology, and multi-language translation.";
        } else if (currentModelId.contains("laguna")) {
            title = "About Laguna XS";
            message = "Developed by Poolside. An ultra-fast, lightweight model optimized for lightning-quick responses and efficient software engineering assistance.";
        } else if (currentModelId.contains("nemotron")) {
            title = "About Nemotron 3";
            message = "Developed by NVIDIA. A massive 120B parameter model that excels at high-performance tasks, complex reasoning, and providing detailed, accurate information.";
        } else if (currentModelId.contains("qwen3")) {
            title = "About Qwen 3 Coder";
            message = "Developed by Alibaba. A specialized model optimized for programming and technical tasks, offering high-level code generation and understanding.";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }
}
