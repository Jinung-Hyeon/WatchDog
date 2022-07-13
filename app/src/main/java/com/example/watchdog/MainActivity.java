package com.example.watchdog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "osslog";
    FirebaseDatabase database;
    DatabaseReference connectedRef, myStatus, clientStatus, clientSignal, info;

    // 시간 비교를 위한 객체
    Calendar calendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = FirebaseDatabase.getInstance();
        connectedRef = database.getReference(".info/connected");
        myStatus = database.getReference("STATUS_Server");
        clientStatus = database.getReference("STATUS_Client");
        clientSignal = database.getReference("Client_Signal");
        info = database.getReference("INFO");


        // 앱이 데이터베이스와 연결이 끊겼을시 파이어베이스 STATUS_Server 노드에 값 저장
        myStatus.onDisconnect().setValue("disconnected");

        // 일과 시간 메소드
        workTime();

        info.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.e(TAG, snapshot.toString() );
                Log.e(TAG, snapshot.getValue().toString() );
                Log.e(TAG, snapshot.getKey() );
                info get = snapshot.getValue(info.class);
                Log.e(TAG, String.valueOf(get.user));
                Log.e(TAG, String.valueOf(get.status));
                if (String.valueOf(get.user).equals("0")){
                    // 일과시간 보다 일찍 앱이 종료되면 예기치 않은 종료라 판단하고 다시 앱실행.
                    if (workTime() > System.currentTimeMillis()){
                        Log.e(TAG, "일과시간 : " + workTime() + " 현재시간 : " + System.currentTimeMillis() + ". 아직 일과시간입니다. 앱을 다시 실행시킵니다.");
                        getPackageList("did");
                    }
                } else else if(snapshot.getValue().toString().equals("connected")){
                    Log.e(TAG, "WatchDog앱이 다시 연결되었습니다.");
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });


        // 클라이언트의 STATUS가 변경될때 동작하는 이벤트 리스너
        clientStatus.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.e(TAG, snapshot.toString() );
                Log.e(TAG, snapshot.getValue().toString() );
                Log.e(TAG, snapshot.getKey() );
                // DID앱이 꺼지면 다시실행

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });



        // 이 앱이 다시 연결되었을때 반응하는 이벤트 리스너
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class);
                if (connected) {
                    Log.e(TAG, "connected!");
                    myStatus.setValue("connected");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "onCancelled!! " + error);
            }
        });
    }

    //다른 앱을 실행시켜주는 메소드
    public void getPackageList(String packageName) {
        //SDK30이상은 Manifest권한 추가가 필요 출처:https://inpro.tistory.com/214
        PackageManager pkgMgr = getPackageManager();
        List<ResolveInfo> mApps;
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mApps = pkgMgr.queryIntentActivities(mainIntent, 0);

        try {
            for (int i = 0; i < mApps.size(); i++) {
                if(mApps.get(i).activityInfo.packageName.startsWith("com.example." + packageName)){
                    Log.d(TAG, "실행시킴");
                    break;
                }
            }
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.example." + packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 일과 시간 지정하는 메소드
    public long workTime(){
        calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 15);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}