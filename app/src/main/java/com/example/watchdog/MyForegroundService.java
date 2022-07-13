package com.example.watchdog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Calendar;
import java.util.List;

public class MyForegroundService extends Service {

    private static final String TAG = "ServerTest";

    // 시간 비교를 위한 객체
    Calendar calendar;

    FirebaseDatabase database;
    DatabaseReference connectedRef, myStatus, clientStatus, clientSignal, info;


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.d(TAG, "onStartCommand  : 리시버타고 넘어옴");
        getPackageList("did");
        final String port = "5001";


        final String CHANNEL_ID = "Foreground Service ID";
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_ID,
                NotificationManager.IMPORTANCE_LOW
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getSystemService(NotificationManager.class).createNotificationChannel(channel);
                Notification.Builder notification = new Notification.Builder(this, CHANNEL_ID)
                        .setContentText("Service is running")
                        .setContentTitle("Service enabled")
                        .setSmallIcon(R.drawable.ic_launcher_background);
                startForeground(1001, notification.build());
            }
        }

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

                // 클라이언트에서 뒤로가기버튼을 두번눌러 의도적으로 종료하려는경우 user에 1을 보내 앱을 자동으로 실행해주는것을 방지
                if (String.valueOf(get.status).equals("HOME")) { // 클라이언트에서 홈키를 누르면 status에 "HOME"을 보내 앱을 다시 실행시킴
                    getPackageList("did");
                } else if (String.valueOf(get.user).equals("0")){
                    // 일과시간 보다 일찍 앱이 종료되면 예기치 않은 종료라 판단하고 다시 앱실행.
                    if (workTime() > System.currentTimeMillis()){
                        Log.e(TAG, "일과시간 : " + workTime() + " 현재시간 : " + System.currentTimeMillis() + ". 아직 일과시간입니다. 앱을 다시 실행시킵니다.");
                        getPackageList("did");
                    }
                }  else if(snapshot.getValue().toString().equals("connected")){
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

        return START_STICKY;

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

}