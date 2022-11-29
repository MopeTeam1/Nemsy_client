package com.example.nemsy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public class BillDetailActivity extends AppCompatActivity {
    private ImageButton back_button, send_button;
    private TextView bill_name, propose, all_propose, age, propose_date, status, bill_content;
    private EditText comment;
    private ScrollView scrollView;
    String billId;
    CommentAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_detail);

        back_button = (ImageButton) findViewById(R.id.back_button);
        bill_name = (TextView) findViewById(R.id.bill_name);
        propose = (TextView) findViewById(R.id.propose);
        all_propose = (TextView) findViewById(R.id.all_propose);
        age = (TextView) findViewById(R.id.age);
        propose_date = (TextView) findViewById(R.id.propose_date);
        status = (TextView) findViewById(R.id.status);
        bill_content = (TextView) findViewById(R.id.bill_content);
        comment = (EditText) findViewById(R.id.comment);
        send_button = (ImageButton) findViewById(R.id.send_button);
        RecyclerView recyclerView = findViewById(R.id.comments_recyclerView);
        scrollView = findViewById(R.id.scrollView);

        Intent inIntent = getIntent();
        billId = inIntent.getStringExtra("BILL_ID");
        propose.setText(inIntent.getStringExtra("RST_PROPOSER"));
        all_propose.setText(inIntent.getStringExtra("PUBL_PROPOSER"));
        age.setText(inIntent.getStringExtra("AGE"));
        propose_date.setText(inIntent.getStringExtra("PROPOSE_DT"));
        status.setText(inIntent.getStringExtra("PROC_RESULT"));
        if(inIntent.getStringExtra("PROC_RESULT") == null) {
            status.setText("접수");
        }

        // 서버 통신
        if(AppHelper.requestQueue == null) {
            AppHelper.requestQueue = Volley.newRequestQueue(getApplicationContext());
        }
        getRequest();

        // 액션바 제거
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        // 웹 크롤링
        String detailLink = inIntent.getStringExtra("DETAIL_LINK");
        final Bundle bundle = new Bundle();
        new Thread() {
            @Override
            public void run() {
                Document doc = null;
                try {
                    doc = Jsoup.connect(detailLink).get();
                    Elements title = doc.select(".titCont");
                    int idxNo = title.text().indexOf("]");
                    int idxproposer = title.text().indexOf("(");
                    String billName = title.text().substring(idxNo+1, idxproposer);
                    bundle.putString("title", billName);

                    Elements content = doc.select("#summaryContentDiv");
                    String contents = content.html().replace("<br> ", "");
                    bundle.putString("content", contents);

                    Message msg = handler.obtainMessage();
                    msg.setData(bundle);
                    handler.sendMessage(msg);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();

        // recyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new CommentAdapter();
        recyclerView.setAdapter(adapter);

        // 뒤로가기 버튼
        back_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        // 댓글 전송 버튼
        send_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(() -> {
                    postRequest(comment.getText().toString());
                }).start();
                comment.getText().clear();
            }
        });
    }

    // 크롤링한 데이터 set
    Handler handler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(@NonNull Message msg) {
            Bundle bundle = msg.getData();
            bill_name.setText(bundle.getString("title"));
            bill_content.setText(bundle.getString("content"));
        }
    };

    // 댓글 가져오기
    public void getRequest() {
        String url = "http://54.250.154.173:8080/api/bill/"+billId+"/comments";
        Log.d("발의법률안 id", billId);
        Log.d("발의법률안 url", url);

        StringRequest request = new StringRequest(
                Request.Method.GET,
                url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("응답", response);
                        processResponse(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("에러", error.getMessage());
                    }
                }
        ) {
            @Override // response를 UTF8로 변경 (한글 깨짐 해결)
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                try {
                    String utf8String = new String(response.data, "UTF-8");
                    return Response.success(utf8String, HttpHeaderParser.parseCacheHeaders(response));
                } catch (UnsupportedEncodingException e) {
                    return Response.error(new ParseError(e));
                } catch (Exception e) {
                    return Response.error(new ParseError(e));
                }
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                return params;
            }
        };
        request.setShouldCache(false);
        AppHelper.requestQueue.add(request);
    }

    // 댓글 작성
    public void postRequest(String content) {
        try {
            OkHttpClient client = new OkHttpClient();

            SharedPreferences prefs = getSharedPreferences("person_info", 0);
            String authorId = prefs.getString("currUID", "");

            String url = "http://54.250.154.173:8080/api/bill/"+billId+"/"+authorId+"/comments";
            String strBody = String.format("{\"content\" : \"%s\"}", content);
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), strBody);

            okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(url).post(requestBody);
            builder.addHeader("Content-type", "application/json");
            okhttp3.Request request = builder.build();
            okhttp3.Response response = client.newCall(request).execute();
            if(response.isSuccessful()) {
                getRequest();
                scrollView.post(new Runnable() {  // 스크롤 맨 밑으로 내리기
                    @Override
                    public void run() {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // JSON -> 객체 변환
    public void processResponse(String response) {
        response = "{\"commentList\":"+response+"}";

        Gson gson = new Gson();
        CommentResult commentResult = gson.fromJson(response, CommentResult.class);

        adapter.clear();
        for(int i=0; i<commentResult.commentList.size(); i++) {
            Comment comment = commentResult.commentList.get(i);
            adapter.addItem(comment);
        }
        adapter.notifyDataSetChanged();
    }
}