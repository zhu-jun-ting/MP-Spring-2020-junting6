package edu.illinois.cs.cs125.spring2020.mp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;

/**
 * LaunchActivity.
 */
public class LaunchActivity extends AppCompatActivity {

    /**
     * request code for sign-in.
     */
    public static final int RC_SIGN_IN = 123;

    /**
     * On create.
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) { // see below discussion

            // launch MainActivity

            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);

            finish();

        } else {

            // start login activity for result - see below discussion
            List<AuthUI.IdpConfig> providers = Arrays.asList(
                    new AuthUI.IdpConfig.EmailBuilder().build());

            // Create and launch sign-in intent
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(),
                    RC_SIGN_IN);

        }

        TextView goLogin = findViewById(R.id.goLogin);

        goLogin.setOnClickListener(v -> {
            //

            // start login activity for result - see below discussion
            List<AuthUI.IdpConfig> providers = Arrays.asList(
                    new AuthUI.IdpConfig.EmailBuilder().build());

            // Create and launch sign-in intent
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(),
                    RC_SIGN_IN);

        });

    }

    /**
     * calls when the activity is finished.
     * @param requestCode requestCode.
     * @param resultCode resultCode
     * @param data intent.
     */
    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {

            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {

                // Successfully signed in
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                // launch MainActivity

                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);

                finish();

                // ...
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
                if (response == null) {

                    System.out.println("x");
//                    Intent intent = new Intent(this, LaunchActivity.class);
//                    startActivity(intent);

                } else {

                    System.out.println(response.getError().getErrorCode());

                }


            }
        }
    }

}
