/**
 *   ownCloud Android client application
 *
 *   @author David A. Velasco
 *   Copyright (C) 2015 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.cerema.cloud2.ui.activity;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.cerema.cloud2.R;
import com.cerema.cloud2.files.services.FileUploader;
import com.cerema.cloud2.lib.common.utils.Log_OC;
import com.cerema.cloud2.ui.dialog.ConfirmationDialogFragment;
import com.cerema.cloud2.ui.dialog.ConfirmationDialogFragment.ConfirmationDialogFragmentListener;
import com.cerema.cloud2.ui.dialog.IndeterminateProgressDialog;
import com.cerema.cloud2.ui.fragment.LocalFileListFragment;
import com.cerema.cloud2.utils.FileStorageUtils;

import java.io.File;


/**
 * Displays local files and let the user choose what of them wants to upload
 * to the current ownCloud account
 */

public class UploadFilesActivity extends FileActivity implements
    LocalFileListFragment.ContainerActivity, ActionBar.OnNavigationListener,
        OnClickListener, ConfirmationDialogFragmentListener {
    
    private ArrayAdapter<String> mDirectories;
    private File mCurrentDir = null;
    private LocalFileListFragment mFileListFragment;
    private Button mCancelBtn;
    private Button mUploadBtn;
    private Account mAccountOnCreation;
    private DialogFragment mCurrentDialog;
    
    public static final String EXTRA_CHOSEN_FILES =
            UploadFilesActivity.class.getCanonicalName() + ".EXTRA_CHOSEN_FILES";

    public static final int RESULT_OK_AND_MOVE = RESULT_FIRST_USER; 
    
    private static final String KEY_DIRECTORY_PATH =
            UploadFilesActivity.class.getCanonicalName() + ".KEY_DIRECTORY_PATH";
    private static final String TAG = "UploadFilesActivity";
    private static final String WAIT_DIALOG_TAG = "WAIT";
    private static final String QUERY_TO_MOVE_DIALOG_TAG = "QUERY_TO_MOVE";
    private RadioButton mRadioBtnCopyFiles;
    private RadioButton mRadioBtnMoveFiles;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log_OC.d(TAG, "onCreate() start");
        super.onCreate(savedInstanceState);

        if(savedInstanceState != null) {
            mCurrentDir = new File(savedInstanceState.getString(
                    UploadFilesActivity.KEY_DIRECTORY_PATH));
        } else {
            mCurrentDir = Environment.getExternalStorageDirectory();
        }
        
        mAccountOnCreation = getAccount();
                
        /// USER INTERFACE
            
        // Drop-down navigation 
        mDirectories = new CustomArrayAdapter<String>(this,
                R.layout.support_simple_spinner_dropdown_item);
        File currDir = mCurrentDir;
        while(currDir != null && currDir.getParentFile() != null) {
            mDirectories.add(currDir.getName());
            currDir = currDir.getParentFile();
        }
        mDirectories.add(File.separator);

        // Inflate and set the layout view
        setContentView(R.layout.upload_files_layout);
        mFileListFragment = (LocalFileListFragment)
                getSupportFragmentManager().findFragmentById(R.id.local_files_list);
        
        
        // Set input controllers
        mCancelBtn = (Button) findViewById(R.id.upload_files_btn_cancel);
        mCancelBtn.setOnClickListener(this);
        mUploadBtn = (Button) findViewById(R.id.upload_files_btn_upload);
        mUploadBtn.setOnClickListener(this);

        SharedPreferences appPreferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        Integer localBehaviour = appPreferences.getInt("prefs_uploader_behaviour", FileUploader.LOCAL_BEHAVIOUR_COPY);

        mRadioBtnMoveFiles = (RadioButton) findViewById(R.id.upload_radio_move);
        if (localBehaviour == FileUploader.LOCAL_BEHAVIOUR_MOVE){
            mRadioBtnMoveFiles.setChecked(true);
        }

        mRadioBtnCopyFiles = (RadioButton) findViewById(R.id.upload_radio_copy);
        if (localBehaviour == FileUploader.LOCAL_BEHAVIOUR_COPY){
            mRadioBtnCopyFiles.setChecked(true);
        }
        
            
        // Action bar setup
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);   // mandatory since Android ICS, according to the
                                                // official documentation
        actionBar.setDisplayHomeAsUpEnabled(mCurrentDir != null && mCurrentDir.getName() != null);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setListNavigationCallbacks(mDirectories, this);
        
        // wait dialog
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
            
        Log_OC.d(TAG, "onCreate() end");
    }

    /**
     * Helper to launch the UploadFilesActivity for which you would like a result when it finished.
     * Your onActivityResult() method will be called with the given requestCode.
     *
     * @param activity    the activity which should call the upload activity for a result
     * @param account     the account for which the upload activity is called
     * @param requestCode If >= 0, this code will be returned in onActivityResult()
     */
    public static void startUploadActivityForResult(Activity activity, Account account, int requestCode) {
        Intent action = new Intent(activity, UploadFilesActivity.class);
        action.putExtra(EXTRA_ACCOUNT, (account));
        activity.startActivityForResult(action, requestCode);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retval = true;
        switch (item.getItemId()) {
            case android.R.id.home: {
                if(mCurrentDir != null && mCurrentDir.getParentFile() != null){
                    onBackPressed(); 
                }
                break;
            }
            default:
                retval = super.onOptionsItemSelected(item);
        }
        return retval;
    }

    
    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        int i = itemPosition;
        while (i-- != 0) {
            onBackPressed();
        }
        // the next operation triggers a new call to this method, but it's necessary to 
        // ensure that the name exposed in the action bar is the current directory when the 
        // user selected it in the navigation list
        if (itemPosition != 0)
            getSupportActionBar().setSelectedNavigationItem(0);
        return true;
    }

    
    @Override
    public void onBackPressed() {
        if (mDirectories.getCount() <= 1) {
            finish();
            return;
        }
        popDirname();
        mFileListFragment.onNavigateUp();
        mCurrentDir = mFileListFragment.getCurrentDirectory();
        
        if(mCurrentDir.getParentFile() == null){
            ActionBar actionBar = getSupportActionBar(); 
            actionBar.setDisplayHomeAsUpEnabled(false);
        } 
    }

    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // responsibility of restore is preferred in onCreate() before than in
        // onRestoreInstanceState when there are Fragments involved
        Log_OC.d(TAG, "onSaveInstanceState() start");
        super.onSaveInstanceState(outState);
        outState.putString(UploadFilesActivity.KEY_DIRECTORY_PATH, mCurrentDir.getAbsolutePath());
        Log_OC.d(TAG, "onSaveInstanceState() end");
    }

    
    /**
     * Pushes a directory to the drop down list
     * @param directory to push
     * @throws IllegalArgumentException If the {@link File#isDirectory()} returns false.
     */
    public void pushDirname(File directory) {
        if(!directory.isDirectory()){
            throw new IllegalArgumentException("Only directories may be pushed!");
        }
        mDirectories.insert(directory.getName(), 0);
        mCurrentDir = directory;
    }

    /**
     * Pops a directory name from the drop down list
     * @return True, unless the stack is empty
     */
    public boolean popDirname() {
        mDirectories.remove(mDirectories.getItem(0));
        return !mDirectories.isEmpty();
    }

    
    // Custom array adapter to override text colors
    private class CustomArrayAdapter<T> extends ArrayAdapter<T> {
    
        public CustomArrayAdapter(UploadFilesActivity ctx, int view) {
            super(ctx, view);
        }
    
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = super.getView(position, convertView, parent);
    
            ((TextView) v).setTextColor(getResources().getColorStateList(
                    android.R.color.white));
            return v;
        }
    
        public View getDropDownView(int position, View convertView,
                ViewGroup parent) {
            View v = super.getDropDownView(position, convertView, parent);
    
            ((TextView) v).setTextColor(getResources().getColorStateList(
                    android.R.color.white));
    
            return v;
        }
    
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDirectoryClick(File directory) {
        pushDirname(directory);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void onFileClick(File file) {
        // nothing to do
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public File getInitialDirectory() {
        return mCurrentDir;
    }


    /**
     * Performs corresponding action when user presses 'Cancel' or 'Upload' button
     * 
     * TODO Make here the real request to the Upload service ; will require to receive the account and 
     * target folder where the upload must be done in the received intent.
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.upload_files_btn_cancel) {
            setResult(RESULT_CANCELED);
            finish();
            
        } else if (v.getId() == R.id.upload_files_btn_upload) {
            new CheckAvailableSpaceTask().execute();
        }
    }


    /**
     * Asynchronous task checking if there is space enough to copy all the files chosen
     * to upload into the ownCloud local folder.
     * 
     * Maybe an AsyncTask is not strictly necessary, but who really knows.
     */
    private class CheckAvailableSpaceTask extends AsyncTask<Void, Void, Boolean> {

        /**
         * Updates the UI before trying the movement
         */
        @Override
        protected void onPreExecute () {
            /// progress dialog and disable 'Move' button
            mCurrentDialog = IndeterminateProgressDialog.newInstance(R.string.wait_a_moment, false);
            mCurrentDialog.show(getSupportFragmentManager(), WAIT_DIALOG_TAG);
        }
        
        
        /**
         * Checks the available space
         * 
         * @return     'True' if there is space enough.
         */
        @Override
        protected Boolean doInBackground(Void... params) {
            String[] checkedFilePaths = mFileListFragment.getCheckedFilePaths();
            long total = 0;
            for (int i=0; checkedFilePaths != null && i < checkedFilePaths.length ; i++) {
                String localPath = checkedFilePaths[i];
                File localFile = new File(localPath);
                total += localFile.length();
            }
            return (new Boolean(FileStorageUtils.getUsableSpace(mAccountOnCreation.name) >= total));
        }

        /**
         * Updates the activity UI after the check of space is done.
         * 
         * If there is not space enough. shows a new dialog to query the user if wants to move the files instead
         * of copy them.
         * 
         * @param result        'True' when there is space enough to copy all the selected files.
         */
        @Override
        protected void onPostExecute(Boolean result) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
            
            if (result) {
                // return the list of selected files (success)
                Intent data = new Intent();
                data.putExtra(EXTRA_CHOSEN_FILES, mFileListFragment.getCheckedFilePaths());

                SharedPreferences.Editor appPreferencesEditor = PreferenceManager
                        .getDefaultSharedPreferences(getApplicationContext()).edit();


                if (mRadioBtnMoveFiles.isChecked()){
                    setResult(RESULT_OK_AND_MOVE, data);
                    appPreferencesEditor.putInt("prefs_uploader_behaviour",
                            FileUploader.LOCAL_BEHAVIOUR_MOVE);
                } else {
                    setResult(RESULT_OK, data);
                    appPreferencesEditor.putInt("prefs_uploader_behaviour",
                            FileUploader.LOCAL_BEHAVIOUR_COPY);
                }
                appPreferencesEditor.apply();
                finish();
            } else {
                // show a dialog to query the user if wants to move the selected files
                // to the ownCloud folder instead of copying
                String[] args = {getString(R.string.app_name)};
                ConfirmationDialogFragment dialog = ConfirmationDialogFragment.newInstance(
                    R.string.upload_query_move_foreign_files, args, R.string.common_yes, -1, R.string.common_no
                );
                dialog.setOnConfirmationListener(UploadFilesActivity.this);
                dialog.show(getSupportFragmentManager(), QUERY_TO_MOVE_DIALOG_TAG);
            }
        }
    }

    @Override
    public void onConfirmation(String callerTag) {
        Log_OC.d(TAG, "Positive button in dialog was clicked; dialog tag is " + callerTag);
        if (callerTag.equals(QUERY_TO_MOVE_DIALOG_TAG)) {
            // return the list of selected files to the caller activity (success),
            // signaling that they should be moved to the ownCloud folder, instead of copied
            Intent data = new Intent();
            data.putExtra(EXTRA_CHOSEN_FILES, mFileListFragment.getCheckedFilePaths());
            setResult(RESULT_OK_AND_MOVE, data);
            finish();
        }
    }


    @Override
    public void onNeutral(String callerTag) {
        Log_OC.d(TAG, "Phantom neutral button in dialog was clicked; dialog tag is " + callerTag);
    }


    @Override
    public void onCancel(String callerTag) {
        /// nothing to do; don't finish, let the user change the selection
        Log_OC.d(TAG, "Negative button in dialog was clicked; dialog tag is " + callerTag);
    }


    @Override
    protected void onAccountSet(boolean stateWasRecovered) {
        super.onAccountSet(stateWasRecovered);
        if (getAccount() != null) {
            if (!mAccountOnCreation.equals(getAccount())) {
                setResult(RESULT_CANCELED);
                finish();
            }
            
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }    

    
}
