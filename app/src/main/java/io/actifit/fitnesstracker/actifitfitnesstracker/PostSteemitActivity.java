package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;

import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import android.os.Bundle;
import android.text.Editable;

import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;

import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.mobileconnectors.s3.transferutility.*;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.IOUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import com.mittsu.markedview.MarkedView;


public class PostSteemitActivity extends BaseActivity implements View.OnClickListener{

    private StepsDBHelper mStepsDBHelper;
    private String notification = "";
    private int min_step_limit = 1000;
    private int min_char_count = 100;
    private Context steemit_post_context;

    //track Choosing Image Intent
    private static final int CHOOSING_IMAGE_REQUEST = 1234;

    private EditText steemitPostContent;

    private Uri fileUri;
    private Bitmap bitmap;
    private ImageView image_preview;

    private EditText stepCountContainer;
    private Activity currentActivity;

    //tracks whether user synched his Fitbit data to avoid refetching activity count from current device
    private static int fitbitSyncDone = 0;

    //tracks whether user wants to post yesterday's data instead
    private static boolean yesterdayReport = false;

    private String fitbitUserId;

    EditText steemitPostTitle;
    EditText steemitUsername;
    EditText steemitPostingKey;
    EditText steemitStepCount;
    TextView measureSectionLabel;

    TextView heightSizeUnit;
    TextView weightSizeUnit;
    TextView waistSizeUnit;
    TextView chestSizeUnit;
    TextView thighsSizeUnit;

    EditText steemitPostTags;

    CheckBox fullAFITPay;

    EditText heightSize;
    EditText weightSize;
    EditText bodyFat;
    EditText chestSize;
    EditText thighsSize;
    EditText waistSize;

    MarkedView mdView;

    MultiSelectionSpinner activityTypeSelector;

    String accountUsername, accountPostingKey, accountActivityCount, finalPostTitle, finalPostTags,
        finalPostContent;
    int selectedActivityCount;
    String heightVal, weightVal, waistVal, chestVal, thighsVal, bodyFatVal,
        heightUnit, weightUnit, waistUnit, chestUnit, thighsUnit;

    String selectedActivitiesVal;

    Boolean fullAFITPayVal;

    //required function to ask for proper read/write permissions on later Android versions
    protected boolean shouldAskPermissions() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    @TargetApi(23)
    protected void askPermissions() {
        String[] permissions = {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
        };
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }


    //implementing file upload functionality
    private void uploadFile() {
        final ProgressDialog uploadProgress;
        if (fileUri != null) {

            //create unique image file name
            final String fileName = UUID.randomUUID().toString();

            final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "/" + fileName);

            createFile(getApplicationContext(), fileUri, file);

            TransferUtility transferUtility =
                    TransferUtility.builder()
                            .context(getApplicationContext())
                            .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                            .s3Client(new AmazonS3Client(AWSMobileClient.getInstance().getCredentialsProvider()))
                            .build();

            //specify content type to be image to be properly recognizable upon rendering
            ObjectMetadata imgMetaData = new ObjectMetadata();
            imgMetaData.setContentType("image/jpeg");

            TransferObserver uploadObserver =
                    transferUtility.upload(fileName, file, imgMetaData);

            //create a new progress dialog to show action is underway
            uploadProgress = new ProgressDialog(steemit_post_context);
            uploadProgress.setMessage(getString(R.string.start_upload));
            uploadProgress.show();

            uploadObserver.setTransferListener(new TransferListener() {

                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        try {
                            if (uploadProgress != null && uploadProgress.isShowing()) {
                                uploadProgress.dismiss();
                            }
                        }catch (Exception ex){
                            //Log.d(MainActivity.TAG,ex.getMessage());
                        }

                        Toast.makeText(getApplicationContext(), getString(R.string.upload_complete), Toast.LENGTH_SHORT).show();

                        String full_img_url = getString(R.string.actifit_usermedia_url)+fileName;
                        String img_markdown_text = "![]("+full_img_url+")";

                        //append the uploaded image url to the text as markdown
                        //if there is any particular selection, replace it too

                        int start = Math.max(steemitPostContent.getSelectionStart(), 0);
                        int end = Math.max(steemitPostContent.getSelectionEnd(), 0);
                        steemitPostContent.getText().replace(Math.min(start, end), Math.max(start, end),
                                img_markdown_text, 0, img_markdown_text.length());

                        file.delete();

                    } else if (TransferState.FAILED == state) {
                        Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.upload_failed), Toast.LENGTH_SHORT);
                        TextView v = toast.getView().findViewById(android.R.id.message);
                        v.setTextColor(Color.RED);
                        toast.show();
                        file.delete();
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int) percentDonef;
                    uploadProgress.setMessage(getString(R.string.uploading) + percentDone + "%");
                    //tvFileName.setText("ID:" + id + "|bytesCurrent: " + bytesCurrent + "|bytesTotal: " + bytesTotal + "|" + percentDone + "%");
                }

                @Override
                public void onError(int id, Exception ex) {
                    ex.printStackTrace();
                }

            });

            // If your upload does not trigger the onStateChanged method inside your
            // TransferListener, you can directly check the transfer state as shown here.
            if (TransferState.COMPLETED == uploadObserver.getState()) {
                // Handle a completed upload.
                try {
                    if (uploadProgress != null && uploadProgress.isShowing()) {
                        uploadProgress.dismiss();
                    }
                }catch (Exception ex){
                    //Log.d(MainActivity.TAG,ex.getMessage());
                }

                Toast.makeText(getApplicationContext(), getString(R.string.upload_complete), Toast.LENGTH_SHORT).show();

                String full_img_url = getString(R.string.actifit_usermedia_url)+fileName;
                String img_markdown_text = "![]("+full_img_url+")";

                //append the uploaded image url to the text as markdown
                //if there is any particular selection, replace it too

                int start = Math.max(steemitPostContent.getSelectionStart(), 0);
                int end = Math.max(steemitPostContent.getSelectionEnd(), 0);
                steemitPostContent.getText().replace(Math.min(start, end), Math.max(start, end),
                        img_markdown_text, 0, img_markdown_text.length());

                file.delete();
            }
        }
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();

        if (i == R.id.btn_choose_file) {
            showChoosingFile();
        } /*else if (i == R.id.btn_upload) {
            uploadFile();
        }*/
    }

    //handles the display of image selection
    private void showChoosingFile() {

        //ensure we have proper permissions for image upload
        if (shouldAskPermissions()) {
            askPermissions();
        }

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_img_title)), CHOOSING_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (bitmap != null) {
            bitmap.recycle();
        }

        if (requestCode == CHOOSING_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), fileUri);

                uploadFile();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //handle displaying a preview of selected image
    private void setPic(String mCurrentPhotoPath) {
        // Get the dimensions of the View
        int targetW = image_preview.getWidth();
        int targetH = image_preview.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        image_preview.setImageBitmap(bitmap);
    }

    private void createFile(Context context, Uri srcUri, File dstFile) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
            if (inputStream == null) return;
            OutputStream outputStream = new FileOutputStream(dstFile);
            IOUtils.copy(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_steemit);


        /*Toolbar postToolbar = findViewById(R.id.post_toolbar);
        setSupportActionBar(postToolbar);*/


        //make sure PPKey link click works
        TextView ppHelpLink = findViewById(R.id.posting_key_link);
        ppHelpLink.setMovementMethod(LinkMovementMethod.getInstance());

        TextView createAccountLink = findViewById(R.id.username_create_account_link);
        createAccountLink.setMovementMethod(LinkMovementMethod.getInstance());


        //setting context
        this.steemit_post_context = this;

        //getting an instance of DB handler
        mStepsDBHelper = new StepsDBHelper(this);

        //grabbing instances of input data sources
        stepCountContainer = findViewById(R.id.steemit_step_count);

        //set initial steps display value
        int stepCount = mStepsDBHelper.fetchTodayStepCount();
        //display step count while ensuring we don't display negative value if no steps tracked yet
        stepCountContainer.setText(String.valueOf((stepCount<0?0:stepCount)), TextView.BufferType.EDITABLE);


        steemitPostTitle = findViewById(R.id.steemit_post_title);
        steemitUsername = findViewById(R.id.steemit_username);
        steemitPostingKey = findViewById(R.id.steemit_posting_key);
        steemitPostContent = findViewById(R.id.steemit_post_text);
        steemitStepCount = findViewById(R.id.steemit_step_count);
        measureSectionLabel = findViewById(R.id.measurements_section_lbl);

        heightSizeUnit = findViewById(R.id.measurements_height_unit);
        weightSizeUnit = findViewById(R.id.measurements_weight_unit);
        waistSizeUnit = findViewById(R.id.measurements_waistsize_unit);
        chestSizeUnit = findViewById(R.id.measurements_chest_unit);
        thighsSizeUnit = findViewById(R.id.measurements_thighs_unit);


        //final EditText steemitPostContentInner = findViewById(R.id.steemit_post_text);
        steemitPostTags = findViewById(R.id.steemit_post_tags);
        activityTypeSelector = findViewById(R.id.steemit_activity_type);

        fullAFITPay = findViewById(R.id.full_afit_pay);

        heightSize = findViewById(R.id.measurements_height);
        weightSize = findViewById(R.id.measurements_weight);
        bodyFat = findViewById(R.id.measurements_bodyfat);
        chestSize = findViewById(R.id.measurements_chest);
        thighsSize = findViewById(R.id.measurements_thighs);
        waistSize = findViewById(R.id.measurements_waistsize);

        heightSizeUnit = findViewById(R.id.measurements_height_unit);
        weightSizeUnit = findViewById(R.id.measurements_weight_unit);
        waistSizeUnit = findViewById(R.id.measurements_waistsize_unit);
        chestSizeUnit = findViewById(R.id.measurements_chest_unit);
        thighsSizeUnit = findViewById(R.id.measurements_thighs_unit);

        mdView = findViewById(R.id.md_view);

        // call from code
        // MarkedView mdView = new MarkedView(this);



        //retrieving account data for simple reuse. Data is not stored anywhere outside actifit App.
        final SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        //try to load editor content if it was stored previously
        steemitPostContent.setText(sharedPreferences.getString("steemPostContent",""));

        // set markdown text pattern. ('contents' object is markdown text)
        mdView.setMDText(steemitPostContent.getText().toString());

        //hook change event for report content preview and saving the text to prevent data loss
        steemitPostContent.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                if(s.length() != 0) {
                    mdView.setMDText(steemitPostContent.getText().toString());

                    //store current text
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("steemPostContent",
                            steemitPostContent.getText().toString());
                    editor.apply();

                }
            }
        });

        //hooking to date change event for activity

        //need to check if user switched to fetch yesterday's data

        RadioGroup reportDateOptionGroup = findViewById(R.id.report_date_option_group);

        //check if the user is allowed to post yesterday's report
        Calendar myCalendar = Calendar.getInstance();

        myCalendar.add(Calendar.DATE, -1);

        //get yesterday's date
        String currentDate = new SimpleDateFormat("yyyyMMdd").format(
                myCalendar.getTime());
        //check last recorded post date
        String lastPostDate = sharedPreferences.getString("actifitLastPostDate","");

        if (!lastPostDate.equals("")){
            if (Integer.parseInt(lastPostDate) >= Integer.parseInt(currentDate)) {
                //need to disable yesterday's option
                RadioButton yesterdayOption = findViewById(R.id.report_yesterday_option);
                yesterdayOption.setEnabled(false);
                yesterdayReport = false;
                //ensure today is selected
                reportDateOptionGroup.check(R.id.report_today_option);
            }
        }


        //make sure to select proper radio button in case it was previously set
        if (yesterdayReport){
            reportDateOptionGroup.check(R.id.report_yesterday_option);
        }

        final TextView fitbitSyncNotice = findViewById(R.id.fitbit_sync_notice);

        //event listener for change in selection
        reportDateOptionGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                //common code for both cases

                //if user had synced Fitbit before, we need to notify that they need to sync again after change of date
                if (fitbitSyncDone > 0) {
                    fitbitSyncNotice.setVisibility(View.VISIBLE);
                    //reset that we fetched fitbit data
                    fitbitSyncDone = 0;
                }

                switch(checkedId){
                    case R.id.report_today_option:
                        //we have today's option
                        //set initial steps display value
                        int stepCount = mStepsDBHelper.fetchTodayStepCount();
                        //display step count while ensuring we don't display negative value if no steps tracked yet
                        stepCountContainer.setText(String.valueOf((stepCount<0?0:stepCount)), TextView.BufferType.EDITABLE);

                        yesterdayReport = false;

                        break;
                    case R.id.report_yesterday_option:
                        //yesterday's option
                        //set initial steps display value
                        stepCount = mStepsDBHelper.fetchYesterdayStepCount();
                        //display step count while ensuring we don't display negative value if no steps tracked yet
                        stepCountContainer.setText(String.valueOf((stepCount<0?0:stepCount)), TextView.BufferType.EDITABLE);

                        yesterdayReport = true;

                        break;
                }
            }
        });

        //image_preview = findViewById(R.id.image_preview);

        //initialize AWS settings and configuration

        findViewById(R.id.btn_choose_file).setOnClickListener(this);

        AWSMobileClient.getInstance().initialize(this).execute();

        //Adding default title content for the daily post

        //generating today's date
        Calendar mCalendar = Calendar.getInstance();
        String postTitle = getString(R.string.default_post_title);
        //set date in title accordingly
        if (yesterdayReport){
            mCalendar.add(Calendar.DATE, -1);
        }
        postTitle += " "+new SimpleDateFormat("MMMM d yyyy").format(mCalendar.getTime());

        //postTitle += String.valueOf(mCalendar.get(Calendar.MONTH)+1)+" " +
                //String.valueOf(mCalendar.get(Calendar.DAY_OF_MONTH))+"/"+String.valueOf(mCalendar.get(Calendar.YEAR));
        steemitPostTitle.setText(postTitle);

        //initializing activity options
        String[] activity_type = {
                getString(R.string.Walking), getString(R.string.Jogging), getString(R.string.Running), getString(R.string.Cycling),
                getString(R.string.RopeSkipping), getString(R.string.Dancing),getString(R.string.Basketball), getString(R.string.Football),
                getString(R.string.Boxing), getString(R.string.Tennis), getString(R.string.TableTennis),
                getString(R.string.MartialArts), getString(R.string.HouseChores), getString(R.string.MovingAroundOffice),
                getString(R.string.Shopping),getString(R.string.DailyActivity), getString(R.string.Aerobics),
                getString(R.string.WeightLifting), getString(R.string.Treadmill),getString(R.string.StairMill),
                getString(R.string.Elliptical), getString(R.string.Hiking), getString(R.string.Gardening),
                getString(R.string.Rollerblading), getString(R.string.Cricket), getString(R.string.Golf),
                getString(R.string.Volleyball), getString(R.string.Geocaching), getString(R.string.Shoveling),
                getString(R.string.Skiing), getString(R.string.Scootering), getString(R.string.Photowalking),
                getString(R.string.KettlebellTraining), getString(R.string.Bootcamp), getString(R.string.Gym),
                getString(R.string.Skating), getString(R.string.Hockey), getString(R.string.Swimming),
                getString(R.string.ChasingPokemons), getString(R.string.Badminton), getString(R.string.PickleBall),
                getString(R.string.Snowshoeing),getString(R.string.Sailing),getString(R.string.Kayaking), getString(R.string.Kidplay),
                getString(R.string.HomeImprovement), getString(R.string.YardWork), getString(R.string.StairClimbing),
                getString(R.string.Yoga), getString(R.string.Stretching)
        };

        //sort options in alpha order
        Arrays.sort(activity_type);

        activityTypeSelector = findViewById(R.id.steemit_activity_type);
        activityTypeSelector.setItems(activity_type);

        steemitUsername.setText(sharedPreferences.getString("actifitUser",""));
        steemitPostingKey.setText(sharedPreferences.getString("actifitPst",""));

        //grab current selection for measure system
        String activeSystem = sharedPreferences.getString("activeSystem",getString(R.string.metric_system_ntt));
        //adjust units accordingly
        if (activeSystem.equals(getString(R.string.metric_system_ntt))){
            weightSizeUnit.setText(getString(R.string.kg_unit));
            heightSizeUnit.setText(getString(R.string.cm_unit));
            waistSizeUnit.setText(getString(R.string.cm_unit));
            chestSizeUnit.setText(getString(R.string.cm_unit));
            thighsSizeUnit.setText(getString(R.string.cm_unit));
        }else{
            weightSizeUnit.setText(getString(R.string.lb_unit));
            heightSizeUnit.setText(getString(R.string.ft_unit));
            waistSizeUnit.setText(getString(R.string.in_unit));
            chestSizeUnit.setText(getString(R.string.in_unit));
            thighsSizeUnit.setText(getString(R.string.in_unit));
        }

        currentActivity = this;

        //capturing steemit post submission
        Button BtnSubmitSteemit = findViewById(R.id.post_to_steem_btn);
        BtnSubmitSteemit.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View arg0) {
                ProcessPost();
            }

        });

        /* fixing scrollability of content within the post content section */

        steemitPostContent.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (v.getId() == R.id.steemit_post_text) {

                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_UP:
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            break;
                    }

                }
                return false;
            }
        });


        /***************** Fitbit Sync Implementation ****************/

        //capturing fitbit sync action
        Button BtnFitbitSync = findViewById(R.id.fitbit_sync);
        BtnFitbitSync.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(final View arg0) {
                // Connect to fitbit and grab data
                NxFitbitHelper.sendUserToAuthorisation(steemit_post_context);
            }

        });

        //retrieve resulting data from fitbit sync (parameter from the Intent)
        Uri returnUrl = getIntent().getData();
        if (returnUrl != null) {
            NxFitbitHelper fitbit = new NxFitbitHelper(getApplicationContext());
            fitbit.requestAccessTokenFromIntent(returnUrl);

            // Get user profile using helper function
            try {
                JSONObject responseProfile = fitbit.getUserProfile();
                //Log.d(MainActivity.TAG, "From JSON encodedId: " + responseProfile.getJSONObject("user"));
                //Log.d(MainActivity.TAG, "From JSON fullName: " + responseProfile.getJSONObject("user").getString("fullName"));

                //essential for capability to fetch measurements
                responseProfile.getJSONObject("user");

                //grab userId
                fitbitUserId = fitbit.getUserId();

                //check to see if settings allows fetching measurements - default true
                String fetchMeasurements = sharedPreferences.getString("fitbitMeasurements",getString(R.string.fitbit_measurements_on_ntt));
                if (fetchMeasurements.equals(getString(R.string.fitbit_measurements_on_ntt))) {

                    //grab and update user weight
                    TextView weight = findViewById(R.id.measurements_weight);
                    weight.setText(fitbit.getFieldFromProfile("weight"));

                    //grab and update user height
                    TextView height = findViewById(R.id.measurements_height);
                    height.setText(fitbit.getFieldFromProfile("height"));
                }

            } catch (JSONException | InterruptedException | ExecutionException | IOException e){
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }

            try {
                String soughtInfo = "steps";
                String targetDate = "today";
                //fetch yesterday data in case this is yesterday's option
                if (yesterdayReport){
                    targetDate = new SimpleDateFormat("yyyy-MM-dd").format(mCalendar.getTime());
                }
                JSONObject stepActivityList = fitbit.getActivityByDate(soughtInfo, targetDate);
                JSONArray stepActivityArray = stepActivityList.getJSONArray("activities-tracker-"+soughtInfo);
                Log.d(MainActivity.TAG, "From JSON distance:" + stepActivityArray.length() );
                int trackedActivityCount = 0;
                if (stepActivityArray.length()>0){
                    Log.d(MainActivity.TAG, "we found matching records");
                    //loop through records adding up recorded steps
                    for (int i=0;i<stepActivityArray.length();i++){
                        trackedActivityCount += Integer.parseInt(stepActivityArray.getJSONObject(i).getString("value"));
                    }

                    //update value according to activity we were able to grab
                    EditText activityCount = findViewById(R.id.steemit_step_count);
                    activityCount.setText("" + trackedActivityCount);

                    //flag that we synced properly
                    fitbitSyncDone = 1;

                    //store date of last sync to avoid improper use of older fitbit data
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("fitbitLastSyncDate",
                            new SimpleDateFormat("yyyyMMdd").format(
                                    mCalendar.getTime()));
                    editor.apply();
                }else{
                    Log.d(MainActivity.TAG, "No auto-tracked activity found for today" );
                }

            } catch (JSONException | InterruptedException | ExecutionException | IOException e){
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }

        } else {
            Log.d(MainActivity.TAG, "Something is wrong with the return value from Fitbit. getIntent().getData() is NULL?");
        }

    }

    /**
     * function handling the display of popup notification
     * @param notification
     */
    void displayNotification(final String notification, final ProgressDialog progress,
                             final Context context, final Activity currentActivity,
                             final String success){
        //render result
        currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //hide the progressDialog
                try{
                    if (progress != null && progress.isShowing()) {
                        progress.dismiss();
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
                /*spinner=findViewById(R.id.progressBar);
                spinner.setVisibility(View.GONE);*/

                final AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
                builder1.setMessage(notification);

                if (success.equals("success")){
                    builder1.setIcon(getResources().getDrawable(R.drawable.success_icon));
                    builder1.setTitle("Actifit Success");
                }else{
                    builder1.setIcon(getResources().getDrawable(R.drawable.error_icon));
                    builder1.setTitle("Actifit Error");
                }

                builder1.setCancelable(true);

                builder1.setPositiveButton(
                        getString(R.string.dismiss_button),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                if (success.equals("success")) {
                                    //close current screen
                                    Log.d(MainActivity.TAG,">>>Finish");
                                    currentActivity.finish();
                                }
                            }
                        });
                //create and display alert window
                try {
                    AlertDialog alert11 = builder1.create();
                    alert11.show();
                }catch(Exception e){
                    //Log.e(MainActivity.TAG, e.getMessage());
                }
            }
        });

        //finish();
    }

    private class PostSteemitRequest extends AsyncTask<String, Void, Void> {
        ProgressDialog progress;
        private final Context context;
        private Activity currentActivity;
        public PostSteemitRequest(Context c, Activity currentActivity){
                this.context = c;
                this.currentActivity = currentActivity;
        }
        protected void onPreExecute(){
            //create a new progress dialog to show action is underway
            progress = new ProgressDialog(this.context);
            progress.setMessage(getString(R.string.sending_post));
            progress.show();
        }
        protected Void doInBackground(String... params) {
            try {
                Log.d(MainActivity.TAG,"click");

                //disable button to prevent multiple clicks
                //arg0.setEnabled(false);



                //storing account data for simple reuse. Data is not stored anywhere outside actifit App.
                SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                //skip on spaces, upper case, and @ symbols to properly match steem username patterns
                editor.putString("actifitUser", accountUsername
                        .trim().toLowerCase().replace("@",""));
                editor.putString("actifitPst", accountPostingKey);
                editor.apply();


                //if (1==1) return null;

                //set proper target date
                Calendar mCalendar = Calendar.getInstance();

                if (yesterdayReport){
                    //go back one day
                    mCalendar.add(Calendar.DATE, -1);
                }

                String targetDate = new SimpleDateFormat("yyyyMMdd").format(
                        mCalendar.getTime());

                //this runs only on live mode
                if (getString(R.string.test_mode).equals("off")){
                    //make sure we have reached the min movement amount
                    /*if (Integer.parseInt(accountActivityCount) < min_step_limit) {
                        notification = getString(R.string.min_activity_not_reached) + " " +
                                NumberFormat.getNumberInstance(Locale.US).format(min_step_limit) + " " + getString(R.string.not_yet);
                        displayNotification(notification, progress, context, currentActivity, "");

                        return null;
                    }*/

                    //make sure the post content has at least the min_char_count
                    if (finalPostContent.length()
                            <= min_char_count){
                        notification = getString(R.string.min_char_count_error)
                                +" "+ min_char_count
                                +" "+ getString(R.string.characters_plural_label);
                        displayNotification(notification, progress, context, currentActivity, "");

                        return null;
                    }

                    //make sure the user has not posted today already,
                    //and also avoid potential abuse of changing phone clock via comparing to older dates
                    String lastPostDate = sharedPreferences.getString("actifitLastPostDate","");

                    Log.d(MainActivity.TAG,">>>>[Actifit]lastPostDate:"+lastPostDate);
                    Log.d(MainActivity.TAG,">>>>[Actifit]currentDate:"+targetDate);
                    if (!lastPostDate.equals("")){
                        if (Integer.parseInt(lastPostDate) >= Integer.parseInt(targetDate)) {
                            notification = getString(R.string.one_post_per_day_error);
                            displayNotification(notification, progress, context, currentActivity, "");
                            return null;
                        }
                    }

                }

                //let us check if user has selected activities yet
                if (selectedActivityCount < 1){
                    notification = getString(R.string.error_need_select_one_activity);
                    displayNotification(notification, progress, context, currentActivity, "");

                    //reset to enabled
                    //arg0.setEnabled(true);
                    return null;
                }

                //prepare relevant day detailed data
                ArrayList<ActivitySlot> timeSlotActivity = mStepsDBHelper.fetchDateTimeSlotActivity(targetDate);

                String stepDataString = "";

                //loop through the data to prepare it for proper display
                for (int position = 0; position < timeSlotActivity.size(); position++) {
                    try {
                        //grab date entry according to stored format
                        String slotTime = (timeSlotActivity.get(position)).slot;
                        String slotEntryFormat = slotTime;
                        if (slotTime.length()<4){
                            //no leading zero, add leading zero
                            slotEntryFormat = "0" + slotTime;
                        }

                        //append to display
                        stepDataString += slotEntryFormat + (timeSlotActivity.get(position)).activityCount + "|";

                    } catch (Exception ex) {
                        Log.d(MainActivity.TAG, ex.toString());
                        ex.printStackTrace();
                    }
                }

                //prepare data to be sent along post
                final JSONObject data = new JSONObject();
                try {
                    //skip on spaces, upper case, and @ symbols to properly match steem username patterns
                    data.put("author", accountUsername
                            .trim().toLowerCase().replace("@",""));
                    data.put("posting_key", accountPostingKey);
                    data.put("title", finalPostTitle);
                    data.put("content", finalPostContent);
                    data.put("tags", finalPostTags);
                    data.put("step_count", accountActivityCount);
                    data.put("activity_type", selectedActivitiesVal);

                    if (fullAFITPayVal) {
                        data.put("full_afit_pay", "on");
                    }

                    data.put("height", heightVal);
                    data.put("weight", weightVal);
                    data.put("chest", chestVal);
                    data.put("waist", waistVal);
                    data.put("thighs", thighsVal);
                    data.put("bodyfat", bodyFatVal);

                    data.put("heightUnit", heightUnit);
                    data.put("weightUnit", weightUnit);
                    data.put("chestUnit", chestUnit);
                    data.put("waistUnit", waistUnit);
                    data.put("thighsUnit", thighsUnit);

                    data.put("appType", "Android");

                    //append detailed activity data
                    data.put("detailedActivity", stepDataString);

                    //appending security param values
                    data.put( getString(R.string.sec_param), getString(R.string.sec_param_val));

                    //grab app version number
                    try {
                        PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                        String version = pInfo.versionName;
                        data.put("appVersion",version);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }


                    //if this is a yesterday post, make sure to include this data
                    if (yesterdayReport){
                        data.put("yesterdayReport", "1");
                    }

                    //also append the date used
                    data.put("activityDate", targetDate);

                    //choose a charity if one is already selected before

                    sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

                    final String currentCharity = (sharedPreferences.getString("selectedCharity",""));

                    if (!currentCharity.equals("")){
                        data.put("charity", currentCharity);
                    }

                    //append user ID
                    data.put("actifitUserID", sharedPreferences.getString("actifitUserID",""));

                    //append data tracking source to see if this is a device reading or a fitbit one
                    //if there was a Fitbit sync, also need to send out that this is Fitbit data
                    if (fitbitSyncDone == 1){
                        data.put("dataTrackingSource", getString(R.string.fitbit_tracking_ntt));

                        //also append encrypted user identifier
                        MessageDigest md = MessageDigest.getInstance(getString(R.string.fitbit_user_enc));
                        byte[] digest = md.digest(fitbitUserId.getBytes());
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < digest.length; i++) {
                            sb.append(Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1));
                        }
                        System.out.println(sb);

                        data.put("fitbitUserId", sb.toString());
                    }else {
                        data.put("dataTrackingSource", sharedPreferences.getString("dataTrackingSystem", ""));
                    }
                    //append report STEEM payout type
                    data.put("reportSTEEMPayMode",sharedPreferences.getString("reportSTEEMPayMode",""));

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String inputLine;
                String result = "";
                //use test url only if testing mode is on
                String urlStr = getString(R.string.test_api_url);
                if (getString(R.string.test_mode).equals("off")) {
                    urlStr = getString(R.string.api_url);
                }
                // Headers
                ArrayList<String[]> headers = new ArrayList<>();

                headers.add(new String[]{"Content-Type", "application/json"});
                HttpResultHelper httpResult = new HttpResultHelper();

                httpResult = httpResult.httpPost(urlStr, null, null, data.toString(), headers, 20000);
                BufferedReader in = new BufferedReader(new InputStreamReader(httpResult.getResponse()));
                while ((inputLine = in.readLine()) != null) {
                    result += inputLine;
                }

                Log.d(MainActivity.TAG,">>>test:" + result);

                //check result of action
                if (result.equals("success")) {
                    notification = getString(R.string.success_post);



                    //send out server notification registration with username and token
                    sendRegistrationToServer();

                    //store date of last successful post to prevent multiple posts per day

                    //storing account data for simple reuse. Data is not stored anywhere outside actifit App.
                    sharedPreferences = getSharedPreferences("actifitSets", MODE_PRIVATE);
                    editor = sharedPreferences.edit();
                    editor.putString("actifitLastPostDate", targetDate);
                    //also clear editor text content
                    editor.putString("steemPostContent", "");
                    editor.apply();
                } else {
                    // notification = getString(R.string.failed_post);
                    notification = result;
                }

                //display proper notification
                displayNotification(notification, progress, context, currentActivity, result);

            }catch (Exception e){

                //display proper notification
                notification = getString(R.string.failed_post);
                displayNotification(notification, progress, context, currentActivity, "");

                Log.d(MainActivity.TAG,"Error connecting");
                e.printStackTrace();
            }
            return null;
        }
    }

/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.post_steem_menu, menu);
        return true;
    }


    //handle the menu item click (new post to steem button)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.action_post:
                ProcessPost();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }
*/
    private void ProcessPost(){

        //only if we haven't grabbed fitbit data, we need to grab new sensor data
        if (fitbitSyncDone == 0){
            int stepCount = 0;
            if (yesterdayReport){
                stepCount = mStepsDBHelper.fetchYesterdayStepCount();
            }else{
                stepCount = mStepsDBHelper.fetchTodayStepCount();
            }
            //display step count while ensuring we don't display negative value if no steps tracked yet
            stepCountContainer.setText(String.valueOf((stepCount<0?0:stepCount)), TextView.BufferType.EDITABLE);
        }else{
            //need to check if a day has passed, to prevent posting again using same fitbit data
            SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);
            String lastSyncDate = sharedPreferences.getString("fitbitLastSyncDate","");

            //generating today's date
            Calendar mCalendar = Calendar.getInstance();
            //set date in title accordingly
            if (yesterdayReport){
                mCalendar.add(Calendar.DATE, -1);
            }

            String targetDate = new SimpleDateFormat("yyyyMMdd").format(
                    mCalendar.getTime());

            Log.d(MainActivity.TAG,">>>>[Actifit]lastPostDate:"+lastSyncDate);
            Log.d(MainActivity.TAG,">>>>[Actifit]currentDate:"+targetDate);
            if (!lastSyncDate.equals("")){
                if (Integer.parseInt(lastSyncDate) < Integer.parseInt(targetDate)) {
                    notification = getString(R.string.need_sync_fitbit_again);
                    ProgressDialog progress = new ProgressDialog(steemit_post_context);
                    progress.setMessage(notification);
                    progress.show();
                    displayNotification(notification, progress, steemit_post_context, currentActivity, "");
                    return;
                }
            }

            EditText activityCount = findViewById(R.id.steemit_step_count);
            int trackedActivityCount = Integer.parseInt(activityCount.getText().toString());

            //store the returned activity count to the DB
            mStepsDBHelper.manualInsertStepsEntry(trackedActivityCount);

        }


        //we need to check first if we have a charity setup
        SharedPreferences sharedPreferences = getSharedPreferences("actifitSets",MODE_PRIVATE);

        final String currentCharity = sharedPreferences.getString("selectedCharity","");
        final String currentCharityDisplayName = sharedPreferences.getString("selectedCharityDisplayName","");

        accountUsername = steemitUsername.getText().toString();
        accountPostingKey = steemitPostingKey.getText().toString();
        accountActivityCount = steemitStepCount.getText().toString();
        finalPostTitle = steemitPostTitle.getText().toString();
        selectedActivityCount = activityTypeSelector.getSelectedIndicies().size();

        finalPostContent = steemitPostContent.getText().toString();
        finalPostTags = steemitPostTags.getText().toString();

        selectedActivitiesVal = activityTypeSelector.getSelectedItemsAsString();

        fullAFITPayVal = fullAFITPay.isChecked();

        heightVal = heightSize.getText().toString();
        weightVal = weightSize.getText().toString();
        chestVal = chestSize.getText().toString();
        waistVal = waistSize.getText().toString();
        thighsVal = thighsSize.getText().toString();
        bodyFatVal = bodyFat.getText().toString();

        heightUnit = heightSizeUnit.getText().toString();
        weightUnit = weightSizeUnit.getText().toString();
        chestUnit = chestSizeUnit.getText().toString();
        waistUnit = waistSizeUnit.getText().toString();
        thighsUnit = thighsSizeUnit.getText().toString();

        if (!currentCharity.equals("")){
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            //go ahead posting
                            new PostSteemitRequest(steemit_post_context, currentActivity).execute();
                            break;

                        case DialogInterface.BUTTON_NEGATIVE:
                            //cancel
                            break;
                    }
                }
            };

            AlertDialog.Builder builder = new AlertDialog.Builder(steemit_post_context);
            builder.setMessage(getString(R.string.current_workout_going_charity) + " "
                    + currentCharityDisplayName + " "
                    + getString(R.string.current_workout_settings_based))
                    .setPositiveButton(getString(R.string.yes_button), dialogClickListener)
                    .setNegativeButton(getString(R.string.no_button), dialogClickListener).show();
        }else {
            //connect to the server via a thread to prevent application hangup
            new PostSteemitRequest(steemit_post_context, currentActivity).execute();
        }
    }

    /**
     * Persist token to third-party servers.
     *
     * Modify this method to associate the user's FCM InstanceID token with any server-side account
     * maintained by your application.
     *
     */
    private void sendRegistrationToServer() {
        String urlStr = getString(R.string.live_server) + getString(R.string.register_user_token_notifications);
        Log.d(MainActivity.TAG, "sendRegistrationToServer - urlStr:"+urlStr);
        ArrayList<String[]> headers = new ArrayList<>();
        headers.add(new String[]{"Content-Type", "application/json"});
        HttpResultHelper httpResult = new HttpResultHelper();

        final JSONObject data = new JSONObject();
        try {
            data.put("token", MainActivity.commToken);
            data.put("user", MainActivity.username);
            data.put("app", "Android");

            String inputLine;
            String result = "";
            httpResult = httpResult.httpPost(urlStr, null, null, data.toString(), headers, 20000);
            BufferedReader in = new BufferedReader(new InputStreamReader(httpResult.getResponse()));
            while ((inputLine = in.readLine()) != null) {
                result += inputLine;
            }

            Log.d(MainActivity.TAG,">>>test:" + result);
        } catch (JSONException | IOException e) {
            //e.printStackTrace();
            Log.e(MainActivity.TAG, "error sending registration data");
        }

    }


}

