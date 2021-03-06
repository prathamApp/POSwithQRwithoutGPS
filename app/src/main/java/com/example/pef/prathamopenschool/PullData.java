package com.example.pef.prathamopenschool;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// Refer : http://www.androidhive.info/2012/07/android-detect-internet-connection-status/
// Refer : http://www.journaldev.com/13629/okhttp-android-example-tutorial

public class PullData extends AppCompatActivity implements ConnectivityReceiver.ConnectivityReceiverListener {

    Spinner spinner_State;
    Button btn_pull, btn_SaveData;
    VillageDBHelper database;
    CrlDBHelper db;
    Context context;
    String selectedState;
    boolean isConnected;
    boolean flag = false; // Flag is used to check the approach used for Pulling data (true = online, false = offline)
    String StudentIDfromJson;
    List<String> stdIDfromJson, grpIDfromJson;
    List<Student> jlstid, checkStudentFlag;
    List<Group> checkGroupFlag, jlstgid;

    Context sessionContex;
    ScoreDBHelper scoreDBHelper;
    PlayVideo playVideo;
    boolean timer;
    int btnPressed = 0;
    ProgressDialog pd;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pull_data);

        MainActivity.sessionFlg = false;
        sessionContex = this;
        playVideo = new PlayVideo();
        // Hide Actionbar
        getSupportActionBar().hide();

        pd = new ProgressDialog(PullData.this);

        context = this;
        database = new VillageDBHelper(context);

        spinner_State = (Spinner) findViewById(R.id.spinner_State);
        //Get Villages Data for States AllSpinners
        List<String> States = database.GetState();
        //Creating the ArrayAdapter instance having the Villages list
        ArrayAdapter<String> StateAdapter = new ArrayAdapter<String>(this, R.layout.custom_spinner, States);
        // Hint for AllSpinners
        spinner_State.setPrompt("Select State");
        spinner_State.setAdapter(StateAdapter);

        spinner_State.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedState = spinner_State.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        btn_pull = (Button) findViewById(R.id.btn_PullData);
        btn_pull.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Check Spinner's Emptyness
                int SpinnerValue = spinner_State.getSelectedItemPosition();

                if (SpinnerValue > 0) {

                    checkConnection();

                    if (isConnected) {

                        btnPressed = 1;

                        Toast.makeText(context, "Connected to the Internet !!!", Toast.LENGTH_SHORT).show();
                        // Executed When internet
                        pd.setCancelable(false);
                        pd.setMessage("Pulling Data Online ... Please Wait ...");
                        pd.show();
                        startPullingOnline();

                        // Flag is set to true for executing code on onBackPressed
                        flag = true;

                    } else if (isConnected == false) {

                        btnPressed = 1;

                        MultiPhotoSelectActivity.dilog.showDilog(PullData.this, "Pulling Data Offline !!!");

                        Thread mThread = new Thread() {
                            @Override
                            public void run() {
                                // Executed when Pull Data will be called for Offline Mode

                                flag = false;

                                try {
                                    UpdateCrlAndVillageJson();
                                    removeDeletedRecords();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                } finally {
                                    MultiPhotoSelectActivity.dilog.dismissDilog();
                                    PullData.this.runOnUiThread(new Runnable() {
                                        public void run() {
                                            Toast.makeText(PullData.this, "Crl & Villages updated in database successfully !!!", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            }
                        };
                        mThread.start();
                    }
                } else {
                    Toast.makeText(PullData.this, "Please Select the State !!!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // STEP 2
        btn_SaveData = (Button) findViewById(R.id.btn_Save);
        btn_SaveData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (btnPressed == 1) {

                    if (isConnected) {
                        MultiPhotoSelectActivity.dilog.showDilog(PullData.this, "Updating Database");

                        Thread mThread = new Thread() {
                            @Override
                            public void run() {
                                // Update fetched CRL & Village data in DB
                                try {
                                    UpdateCrlAndVillageJson();
                                    removeDeletedRecords();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                } finally {
                                    PullData.this.runOnUiThread(new Runnable() {
                                        public void run() {
                                            Toast.makeText(PullData.this, "Database Updated", Toast.LENGTH_LONG).show();
                                            Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
                                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            startActivity(i);
                                        }
                                    });
                                }
                            }
                        };
                        mThread.start();
                    } else if (isConnected == false) {
                        Toast.makeText(PullData.this, "Saved !!!", Toast.LENGTH_SHORT).show();
                        Intent i = getBaseContext().getPackageManager()
                                .getLaunchIntentForPackage(getBaseContext().getPackageName());
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    }

                } else {
                    Toast.makeText(PullData.this, "Please complete Step 1 !!!", Toast.LENGTH_SHORT).show();
                }

            }
        });


    }

    private void startPullingOnline() {
        if (MultiPhotoSelectActivity.programID.equals("1")) {
            //For HLearning Pull URLs
            Log.d("Json : ", "CRL");
            UpdateJsonOnline(Utility.getProperty("HLpullCrlsURL", PullData.this), "Crl", selectedState, "1");
        } else if (MultiPhotoSelectActivity.programID.equals("2")) {
            //For RI pull URLS
            Log.d("Json : ", "CRL");
            UpdateJsonOnline(Utility.getProperty("RIpullCrlsURL", PullData.this), "Crl", selectedState, "2");
        } else if (MultiPhotoSelectActivity.programID.equals("3")) {
            //For Second Chance Pull URLs
            Log.d("Json : ", "CRL");
            UpdateJsonOnline(Utility.getProperty("HLpullCrlsURL", PullData.this), "Crl", selectedState, "3");
        } else if (MultiPhotoSelectActivity.programID.equals("10")) {
            //For PI Pull URLs
            Log.d("Json : ", "CRL");
            UpdateJsonOnline(Utility.getProperty("PIpullCrlsURL", PullData.this), "Crl", selectedState, "10");
        } else if (MultiPhotoSelectActivity.programID.equals("8")) {
            //For PI Pull URLs
            Log.d("Json : ", "CRL");
            UpdateJsonOnline(Utility.getProperty("PIpullCrlsURL", PullData.this), "Crl", selectedState, "8");
        } else if (MultiPhotoSelectActivity.programID.equals("13")) {
            //For PI Pull URLs
            Log.d("Json : ", "CRL");
            UpdateJsonOnline(Utility.getProperty("HGpullCrlsURL", PullData.this), "Crl", selectedState, "13");
        } else {
            String programID = new Utility().getProgramId();
            UpdateJsonOnline(Utility.getProperty("HLpullCrlsURL", PullData.this), "Crl", selectedState, programID);
        }

    }


    /* @Override
     public void onBackPressed() {

         if (flag == true) {
             // This should get executed after  update json
             if (isConnected) {
                 MultiPhotoSelectActivity.dilog.showDilog(PullData.this, "Updating Database");

                 Thread mThread = new Thread() {
                     @Override
                     public void run() {
                         // Update fetched CRL & Village data in DB
                         try {
                             UpdateCrlAndVillageJson();
                             //MultiPhotoSelectActivity.dilog.dismissDilog();
                             PullData.this.runOnUiThread(new Runnable() {
                                 public void run() {
                                     Toast.makeText(PullData.this, "Database Updated", Toast.LENGTH_LONG).show();
                                     *//*Intent i = new Intent(PullData.this, AdminActivity.class);
                                    startActivity(i);
                                    finish();*//*
                                    Intent i = getBaseContext().getPackageManager()
                                            .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    startActivity(i);
                                }
                            });
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                };
                mThread.start();
            }
        }
        super.onBackPressed();
    }
*/
    // Receiver for checking connection
    private void checkConnection() {
        isConnected = ConnectivityReceiver.isConnected();
    }


    public void onNetworkConnectionChanged(boolean isConnected) {
        // Useful if status of the network is changed
    }

    private void UpdateJsonOnline(String baseurl, final String filename, String state, final String programid) {

        //if program id = 13 then create Aser.json if not exists
        if (MultiPhotoSelectActivity.programID.equalsIgnoreCase("13")) {
            // check Aser.json exists & create empty if not found
            File Aser = new File(Environment.getExternalStorageDirectory() + "/.POSinternal/Json/Aser.json");
            if (!Aser.exists()) {
                try {
                    Aser.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        OkHttpClient client = new OkHttpClient();

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseurl).newBuilder();
        urlBuilder.addQueryParameter("state", state);
        urlBuilder.addQueryParameter("programid", programid);
        String url = urlBuilder.build().toString();
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                call.cancel();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                final String myResponse = response.body().string();
                try {
                    File outFile = new File(Environment.getExternalStorageDirectory() + "/.POSinternal/Json/", filename + ".json");
                    FileOutputStream out = new FileOutputStream(outFile, false);
                    byte[] contents = myResponse.toString().getBytes();
                    out.write(contents);
                    out.flush();
                    out.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(PullData.this, "" + filename + " pulled ...", Toast.LENGTH_SHORT).show();
                        }
                    });

                    switch (programid) {
                        case "1":
                            if (filename.equalsIgnoreCase("CRL")) {
                                Log.d("Json : ", "Village");
                                UpdateJsonOnline(Utility.getProperty("HLpullVillagesURL", PullData.this), "Village", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Village")) {
                                Log.d("Json : ", "Group");
                                UpdateJsonOnline(Utility.getProperty("HLpullGroupsURL", PullData.this), "Group", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Group")) {
                                Log.d("Json : ", "Student");
                                UpdateJsonOnline(Utility.getProperty("HLpullStudentsURL", PullData.this), "Student", selectedState, programid);
                            } else {
                                Log.d("Json : ", "DONE");
                                if (pd != null && pd.isShowing())
                                    pd.dismiss();
                                btnPressed = 1;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(PullData.this, "Data Successfully Pulled from Server !!! ", Toast.LENGTH_SHORT).show();
                                    }
                                });

                            }
                            break;
                        case "2":
                            if (filename.equalsIgnoreCase("CRL")) {
                                Log.d("Json : ", "Village");
                                UpdateJsonOnline(Utility.getProperty("RIpullVillagesURL", PullData.this), "Village", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Village")) {
                                Log.d("Json : ", "Group");
                                UpdateJsonOnline(Utility.getProperty("RIpullGroupsURL", PullData.this), "Group", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Group")) {
                                Log.d("Json : ", "Student");
                                UpdateJsonOnline(Utility.getProperty("RIpullStudentsURL", PullData.this), "Student", selectedState, programid);
                            } else {
                                Log.d("Json : ", "DONE");
                                if (pd != null && pd.isShowing())
                                    pd.dismiss();
                                btnPressed = 1;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(PullData.this, "Data Successfully Pulled from Server !!! ", Toast.LENGTH_SHORT).show();
                                    }
                                });

                            }
                            break;


                        case "3":
                            if (filename.equalsIgnoreCase("CRL")) {
                                Log.d("Json : ", "Village");
                                UpdateJsonOnline(Utility.getProperty("HLpullVillagesURL", PullData.this), "Village", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Village")) {
                                Log.d("Json : ", "Group");
                                UpdateJsonOnline(Utility.getProperty("HLpullGroupsURL", PullData.this), "Group", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Group")) {
                                Log.d("Json : ", "Student");
                                UpdateJsonOnline(Utility.getProperty("HLpullStudentsURL", PullData.this), "Student", selectedState, programid);
                            } else {
                                Log.d("Json : ", "DONE");
                                if (pd != null && pd.isShowing())
                                    pd.dismiss();
                                btnPressed = 1;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(PullData.this, "Data Successfully Pulled from Server !!! ", Toast.LENGTH_SHORT).show();
                                    }
                                });

                            }
                            break;


                        case "10":
                            if (filename.equalsIgnoreCase("CRL")) {
                                Log.d("Json : ", "Village");
                                UpdateJsonOnline(Utility.getProperty("PIpullVillagesURL", PullData.this), "Village", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Village")) {
                                Log.d("Json : ", "Group");
                                UpdateJsonOnline(Utility.getProperty("PIpullGroupsURL", PullData.this), "Group", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Group")) {
                                Log.d("Json : ", "Student");
                                UpdateJsonOnline(Utility.getProperty("PIpullStudentsURL", PullData.this), "Student", selectedState, programid);
                            } else {
                                Log.d("Json : ", "DONE");
                                if (pd != null && pd.isShowing())
                                    pd.dismiss();
                                btnPressed = 1;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(PullData.this, "Data Successfully Pulled from Server !!! ", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                            break;

                        case "13":
                            if (filename.equalsIgnoreCase("CRL")) {
                                Log.d("Json : ", "Village");
                                UpdateJsonOnline(Utility.getProperty("HGpullVillagesURL", PullData.this), "Village", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Village")) {
                                Log.d("Json : ", "Group");
                                UpdateJsonOnline(Utility.getProperty("HGpullGroupsURL", PullData.this), "Group", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Group")) {
                                Log.d("Json : ", "Student");
                                UpdateJsonOnline(Utility.getProperty("HGpullStudentsURL", PullData.this), "Student", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Student")) {
                                Log.d("Json : ", "Aser");
                                UpdateJsonOnline(Utility.getProperty("HGpullAserURL", PullData.this), "Aser", selectedState, programid);
                            } else {
                                Log.d("Json : ", "DONE");
                                if (pd != null && pd.isShowing())
                                    pd.dismiss();
                                btnPressed = 1;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(PullData.this, "Data Successfully Pulled from Server !!! ", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                            break;

                        default:
                            if (filename.equalsIgnoreCase("CRL")) {
                                Log.d("Json : ", "Village");
                                UpdateJsonOnline(Utility.getProperty("HLpullVillagesURL", PullData.this), "Village", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Village")) {
                                Log.d("Json : ", "Group");
                                UpdateJsonOnline(Utility.getProperty("HLpullGroupsURL", PullData.this), "Group", selectedState, programid);
                            } else if (filename.equalsIgnoreCase("Group")) {
                                Log.d("Json : ", "Student");
                                UpdateJsonOnline(Utility.getProperty("HLpullStudentsURL", PullData.this), "Student", selectedState, programid);
                            } else {
                                Log.d("Json : ", "DONE");
                                if (pd != null && pd.isShowing())
                                    pd.dismiss();
                                btnPressed = 1;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(PullData.this, "Data Successfully Pulled from Server !!! ", Toast.LENGTH_SHORT).show();
                                    }
                                });

                            }
                            break;

                    }

//                    Toast.makeText(PullData.this, "Pulled From Server !!! " + filename, Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    e.printStackTrace();
//                    Toast.makeText(PullData.this, "Error" + filename + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }


    // Check entries from Json & delete records from db if exists
    public void removeDeletedRecords() throws JSONException {
        JSONArray grpJsonArray = new JSONArray(loadGrpJSONFromAsset());
        JSONArray stdJsonArray = new JSONArray(loadStdJSONFromAsset());

        // DELETE WHOLE GROUP
        try {
            for (int i = 0; i < grpJsonArray.length(); i++) {
                JSONObject grpObject = grpJsonArray.getJSONObject(i);
                String grpID = grpObject.getString("GroupId");
                String status = grpObject.getString("DeviceId");
                if (status.equalsIgnoreCase("deleted")) {

                    // Delete From Group Table
                    GroupDBHelper groupDBHelper = new GroupDBHelper(PullData.this);
                    boolean isDeleted = groupDBHelper.Delete(grpID);

                    // Delete Associated Students
                    StudentDBHelper studentDBHelper = new StudentDBHelper(this);
                    boolean res = studentDBHelper.deleteDeletedGrpsStdRecords(grpID);

                    // Delete assigned group (Activated Groups & Assigned Groups in Status Table)
                    StatusDBHelper statusDBHelper = new StatusDBHelper(PullData.this);
                    String grp1 = statusDBHelper.getValue("group1");
                    String grp2 = statusDBHelper.getValue("group2");
                    String grp3 = statusDBHelper.getValue("group3");
                    String grp4 = statusDBHelper.getValue("group4");
                    String grp5 = statusDBHelper.getValue("group5");
                    if (grpID.equalsIgnoreCase(grp1)) {
                        statusDBHelper.Update("group1", "0");
                        String act = statusDBHelper.getValue("ActivatedForGroups");
                        act = act.replace(grp1, "0");
                        statusDBHelper.Update("ActivatedForGroups", act);
                    }
                    if (grpID.equalsIgnoreCase(grp2)) {
                        statusDBHelper.Update("group2", "0");
                        String act = statusDBHelper.getValue("ActivatedForGroups");
                        act = act.replace(grp2, "0");
                        statusDBHelper.Update("ActivatedForGroups", act);
                    }
                    if (grpID.equalsIgnoreCase(grp3)) {
                        statusDBHelper.Update("group3", "0");
                        String act = statusDBHelper.getValue("ActivatedForGroups");
                        act = act.replace(grp3, "0");
                        statusDBHelper.Update("ActivatedForGroups", act);
                    }
                    if (grpID.equalsIgnoreCase(grp4)) {
                        statusDBHelper.Update("group4", "0");
                        String act = statusDBHelper.getValue("ActivatedForGroups");
                        act = act.replace(grp4, "0");
                        statusDBHelper.Update("ActivatedForGroups", act);
                    }
                    if (grpID.equalsIgnoreCase(grp5)) {
                        statusDBHelper.Update("group5", "0");
                        String act = statusDBHelper.getValue("ActivatedForGroups");
                        act = act.replace(grp5, "0");
                        statusDBHelper.Update("ActivatedForGroups", act);
                    }
                }
            }// Delete Whole Grp
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // DELETE STUDENTS
        try {
            for (int i = 0; i < stdJsonArray.length(); i++) {
                JSONObject stdObject = stdJsonArray.getJSONObject(i);
                String stdID = stdObject.getString("StudentId");
                String status = stdObject.getString("Gender");
                if (status.equalsIgnoreCase("deleted")) {
                    // Delete Student
                    StudentDBHelper studentDBHelper = new StudentDBHelper(PullData.this);
                    Boolean isDeleted = studentDBHelper.Delete(stdID);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }


    public void UpdateCrlAndVillageJson() throws JSONException {

        // insert your code to run only when application is started first time here
        context = this;

        //CRL Initial DB Process
        db = new CrlDBHelper(context);
        // For Loading CRL Json From External Storage (Assets)
        JSONArray crlJsonArray = new JSONArray(loadCrlJSONFromAsset());
        for (int i = 0; i < crlJsonArray.length(); i++) {

            JSONObject clrJsonObject = crlJsonArray.getJSONObject(i);
            Crl crlobj = new Crl();
            crlobj.CRLId = clrJsonObject.getString("CRLId");
            crlobj.FirstName = clrJsonObject.getString("FirstName");
            crlobj.LastName = clrJsonObject.getString("LastName");
            crlobj.UserName = clrJsonObject.getString("UserName");
            crlobj.Password = clrJsonObject.getString("Password");
            crlobj.ProgramId = clrJsonObject.getInt("ProgramId");
            crlobj.Mobile = clrJsonObject.getString("Mobile");
            crlobj.State = clrJsonObject.getString("State");
            crlobj.Email = clrJsonObject.getString("Email");
            crlobj.newCrl = false;

            // new entries default values
            try {
                crlobj.sharedBy = clrJsonObject.getString("sharedBy");
                crlobj.SharedAtDateTime = clrJsonObject.getString("SharedAtDateTime");
                crlobj.appVersion = clrJsonObject.getString("appVersion");
                crlobj.appName = clrJsonObject.getString("appName");
                crlobj.CreatedOn = clrJsonObject.getString("CreatedOn");
            } catch (Exception e) {
                crlobj.sharedBy = "";
                crlobj.SharedAtDateTime = "";
                crlobj.appVersion = "";
                crlobj.appName = "";
                crlobj.CreatedOn = "";
                e.printStackTrace();
            }


            db.updateJsonData(crlobj);
            BackupDatabase.backup(context);
        }


        //Villages Initial DB Process
        database = new VillageDBHelper(context);
        // For Loading Villages Json From External Storage (Assets)
        JSONArray villagesJsonArray = new JSONArray(loadVillageJSONFromAsset());

        for (int j = 0; j < villagesJsonArray.length(); j++) {
            JSONObject villagesJsonObject = villagesJsonArray.getJSONObject(j);

            Village villageobj = new Village();
            villageobj.VillageID = villagesJsonObject.getInt("VillageId");
            villageobj.VillageCode = villagesJsonObject.getString("VillageCode");
            villageobj.VillageName = villagesJsonObject.getString("VillageName");
            villageobj.Block = villagesJsonObject.getString("Block");
            villageobj.District = villagesJsonObject.getString("District");
            villageobj.State = villagesJsonObject.getString("State");
            villageobj.CRLID = villagesJsonObject.getString("CRLId");

            database.updateJsonData(villageobj);
            BackupDatabase.backup(context);
        }


        //compareStudentDBvsJson();
        //compareGroupDBvsJson();
    }


    /* ************************************** COMPARE JSON VS DB Student ****************************************************************/


    private void compareStudentDBvsJson() throws JSONException {

        context = this;
        StudentDBHelper sdb = new StudentDBHelper(context);

        // Flag From DB
        checkStudentFlag = sdb.GetAllNewStudents();


        stdIDfromJson = new ArrayList<>();
        // Flag & ID From Json
        JSONArray stdJsonArray = new JSONArray(loadStdJSONFromAsset());
        JSONObject stdJsonObject;
        Student stdobj;

        for (int i = 0; i < stdJsonArray.length(); i++) {
            stdJsonObject = stdJsonArray.getJSONObject(i);
            stdobj = new Student();
            stdobj.StudentID = stdJsonObject.getString("StudentId");
            //stdobj.newStudent = Boolean.valueOf(stdJsonObject.getString("NewFlag"));

            // db.updateJsonData(crlobj);
            stdIDfromJson.add(stdobj.StudentID);
        }

        jlstid = new ArrayList<Student>();
        Student stdJson;
        for (int k = 0; k < stdIDfromJson.size(); k++) {
            stdJson = new Student();
            stdJson.StudentID = stdIDfromJson.get(k);
            jlstid.add(stdJson);
        }

        try {
            for (int i = 0; i < checkStudentFlag.size(); i++) {
                for (int j = 0; j < stdIDfromJson.size(); j++) {
                    //System.out.print("i ="+i+"J ="+j );
                    //if(checkStudentFlag.get(i).StudentID != null  && jlstid.get(j).StudentID !=null){
                    if (checkStudentFlag.get(i).StudentID.equals(jlstid.get(j).StudentID)) {
                        String StudentID = checkStudentFlag.get(i).StudentID;
                        SetFlagFalse(StudentID);
                    }
                    //}
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void SetFlagFalse(String studentID) {
        context = this;
        StudentDBHelper sdb = new StudentDBHelper(context);
        sdb.SetFlagFalse(studentID);

    }



    /* ************************************** COMPARE JSON VS DB Group ****************************************************************/


    private void compareGroupDBvsJson() throws JSONException {

        context = this;
        GroupDBHelper sdb = new GroupDBHelper(context);

        // Flag From DB
        checkGroupFlag = sdb.GetAllNewGroups();


        // Flag & ID From Json
        JSONArray grpJsonArray = new JSONArray(loadGrpJSONFromAsset());
        JSONObject grpJsonObject;
        Group grpobj;
        for (int i = 0; i < grpJsonArray.length(); i++) {

            grpJsonObject = grpJsonArray.getJSONObject(i);
            grpobj = new Group();
            grpobj.GroupID = grpJsonObject.getString("GroupId");

            grpIDfromJson = new ArrayList<>();
            grpIDfromJson.add(grpobj.GroupID);
        }


        for (int k = 0; k < grpIDfromJson.size(); k++) {
            Group grpJson = new Group();
            grpJson.GroupID = grpIDfromJson.get(k);
            jlstgid = new ArrayList<Group>();
            jlstgid.add(grpJson);

        }

        try {
            for (int i = 0; i < checkGroupFlag.size(); i++) {
                for (int j = 0; j < grpIDfromJson.size(); j++) {
                    if (checkGroupFlag.get(i).GroupID.equals(jlstgid.get(j).GroupID)) {

                        String GroupID = checkGroupFlag.get(i).GroupID;
                        SetFlagFalseGrp(GroupID);

                    }

                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void SetFlagFalseGrp(String GrpID) {

        context = this;
        GroupDBHelper gdb = new GroupDBHelper(context);
        gdb.SetFlagFalse(GrpID);

    }


    // Reading Group Json From internal memory
    public String loadGrpJSONFromAsset() {
        String grpJsonStr = null;
        try {
            File grpJsonSDCard = new File(Environment.getExternalStorageDirectory() + "/.POSinternal/Json/", "Group.json");
            FileInputStream stream = new FileInputStream(grpJsonSDCard);
            try {
                FileChannel fc = stream.getChannel();
                MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

                grpJsonStr = Charset.defaultCharset().decode(bb).toString();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stream.close();
            }

        } catch (Exception e) {
        }

        return grpJsonStr;
    }


    // Reading Student Json From internal memory
    public String loadStdJSONFromAsset() {
        String stdJsonStr = null;

        try {
            File stdJsonSDCard = new File(Environment.getExternalStorageDirectory() + "/.POSinternal/Json/", "Student.json");
            FileInputStream stream = new FileInputStream(stdJsonSDCard);
            try {
                FileChannel fc = stream.getChannel();
                MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

                stdJsonStr = Charset.defaultCharset().decode(bb).toString();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stream.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return stdJsonStr;
    }


    // Reading CRL Json From internal memory
    public String loadCrlJSONFromAsset() {
        String crlJsonStr = null;

        try {
            File crlJsonSDCard = new File(Environment.getExternalStorageDirectory() + "/.POSinternal/Json/", "Crl.json");
            FileInputStream stream = new FileInputStream(crlJsonSDCard);
            try {
                FileChannel fc = stream.getChannel();
                MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

                crlJsonStr = Charset.defaultCharset().decode(bb).toString();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stream.close();
            }

        } catch (Exception e) {
        }

        return crlJsonStr;
    }

    // Reading Village Json From internal memory
    public String loadVillageJSONFromAsset() {
        String villageJson = null;
        try {
            File villageJsonSDCard = new File(Environment.getExternalStorageDirectory() + "/.POSinternal/Json/", "Village.json");
            FileInputStream stream = new FileInputStream(villageJsonSDCard);
            try {
                FileChannel fc = stream.getChannel();
                MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

                villageJson = Charset.defaultCharset().decode(bb).toString();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stream.close();
            }

        } catch (Exception e) {
        }

        return villageJson;

    }

    @Override
    protected void onResume() {
        super.onResume();

        MyApplication.getInstance().setConnectivityListener(this);

        if (MultiPhotoSelectActivity.pauseFlg) {
            MultiPhotoSelectActivity.cd.cancel();
            MultiPhotoSelectActivity.pauseFlg = false;
            MultiPhotoSelectActivity.duration = MultiPhotoSelectActivity.timeout;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        MultiPhotoSelectActivity.pauseFlg = true;

        MultiPhotoSelectActivity.cd = new CountDownTimer(MultiPhotoSelectActivity.duration, 1000) {
            //cd = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                MultiPhotoSelectActivity.duration = millisUntilFinished;
                timer = true;
            }

            @Override
            public void onFinish() {
                timer = false;
                MainActivity.sessionFlg = true;
                if (!CardAdapter.vidFlg) {
                    scoreDBHelper = new ScoreDBHelper(sessionContex);
                    playVideo.calculateEndTime(scoreDBHelper);
                    BackupDatabase.backup(sessionContex);
                    System.exit(0);
                    finishAffinity();

                }

                if (MultiPhotoSelectActivity.dilog != null) {
                    try {
                        MultiPhotoSelectActivity.dilog.dismissDilog();
                        MultiPhotoSelectActivity.dilog = null;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
        }.start();

        if (MultiPhotoSelectActivity.dilog != null) {
            try {
                MultiPhotoSelectActivity.dilog.dismissDilog();
                MultiPhotoSelectActivity.dilog = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

}
