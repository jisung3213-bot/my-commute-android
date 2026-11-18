package com.example.mycommute.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mycommute.R;
import com.example.mycommute.data.FirestoreSync;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.model.Routine;
import com.google.firebase.auth.FirebaseAuth;
import java.util.Calendar;

public class LoginActivity extends AppCompatActivity {
    private EditText email,password;
    private TextView status;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(Session.signedIn(this)) {syncAndOpen();return;}
        setContentView(R.layout.activity_login);
        email=findViewById(R.id.inputEmail);password=findViewById(R.id.inputPassword);
        status=findViewById(R.id.textAuthStatus);
        if(!Session.firebaseConfigured(this)) {
            UiNotice.show(status,UiNotice.State.UNAVAILABLE,"로그인 서비스 이용 불가");
            findViewById(R.id.btnLogin).setEnabled(false);
            findViewById(R.id.btnRegister).setEnabled(false);
        } else UiNotice.show(status,UiNotice.State.IDLE,"");
        findViewById(R.id.btnLogin).setOnClickListener(v -> authenticate(false));
        findViewById(R.id.btnRegister).setOnClickListener(v -> authenticate(true));
    }
    private void authenticate(boolean register) {
        String e=email.getText().toString().trim(),p=password.getText().toString();
        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() || p.length()<6) {
            Toast.makeText(this,"이메일·비밀번호 확인",Toast.LENGTH_SHORT).show();return;
        }
        findViewById(R.id.btnLogin).setEnabled(false);findViewById(R.id.btnRegister).setEnabled(false);
        UiNotice.show(status,UiNotice.State.LOADING,register?"가입 처리 중…":"로그인 중…");
        com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult> task = register
                ? FirebaseAuth.getInstance().createUserWithEmailAndPassword(e,p)
                : FirebaseAuth.getInstance().signInWithEmailAndPassword(e,p);
        task.addOnCompleteListener(this,result -> {
            if(result.isSuccessful()) {
                syncAndOpen();
            } else {
                findViewById(R.id.btnLogin).setEnabled(true);findViewById(R.id.btnRegister).setEnabled(true);
                String why=result.getException()==null?"":result.getException().getLocalizedMessage();
                UiNotice.show(status,UiNotice.classify(why),"로그인 실패 · "+
                        (UiNotice.error(why).equals("정보를 불러오지 못했습니다")
                                ?"계정 정보 확인":UiNotice.error(why)));
            }
        });
    }
    private void syncAndOpen(){
        FirestoreSync.fetchAll(new FirestoreSync.ProfilesResult(){
            @Override public void onLoaded(java.util.List<Routine> all){
                for(Routine r:all)RoutineStore.saveLocal(LoginActivity.this,r);
                readDefaults();
            }
            @Override public void onFailure(String why){readDefaults();}
            private void readDefaults(){
                FirestoreSync.fetchPreferences(new FirestoreSync.SettingsResult(){
                    @Override public void onLoaded(org.json.JSONObject settings){
                        RoutineStore.importPreferences(LoginActivity.this,settings);
                        openApp();
                    }
                    @Override public void onFailure(String why){openApp();}
                });
            }
        });
    }
    private void openApp() {
        // Keep each direction's default selected at login.
        RoutineStore.restoreDefaultsOnLogin(this);
        Routine routine=RoutineStore.read(this);
        com.example.mycommute.alarms.ReminderScheduler.scheduleCurrent(this);
        Intent intent=new Intent(this,MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);finish();
    }
}
