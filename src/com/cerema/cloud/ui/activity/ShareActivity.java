/**
 * ownCloud Android client application
 *
 * @author masensio
 * @author David A. Velasco
 * @author Juan Carlos González Cabrero
 * Copyright (C) 2015 ownCloud Inc.
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.cerema.cloud2.ui.activity;

import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

import com.cerema.cloud2.R;
import com.cerema.cloud2.datamodel.OCFile;
import com.cerema.cloud2.lib.common.operations.RemoteOperation;
import com.cerema.cloud2.lib.common.operations.RemoteOperationResult;
import com.cerema.cloud2.lib.common.utils.Log_OC;
import com.cerema.cloud2.lib.resources.shares.OCShare;
import com.cerema.cloud2.lib.resources.shares.ShareType;
import com.cerema.cloud2.operations.CreateShareViaLinkOperation;
import com.cerema.cloud2.operations.GetSharesForFileOperation;
import com.cerema.cloud2.operations.UnshareOperation;
import com.cerema.cloud2.operations.UpdateSharePermissionsOperation;
import com.cerema.cloud2.providers.UsersAndGroupsSearchProvider;
import com.cerema.cloud2.ui.dialog.ShareLinkToDialog;
import com.cerema.cloud2.ui.fragment.EditShareFragment;
import com.cerema.cloud2.ui.fragment.SearchShareesFragment;
import com.cerema.cloud2.ui.fragment.ShareFileFragment;
import com.cerema.cloud2.ui.fragment.ShareFragmentListener;
import com.cerema.cloud2.utils.GetShareWithUsersAsyncTask;


/**
 * Activity for sharing files
 */

public class ShareActivity extends FileActivity
        implements ShareFragmentListener {

    private static final String TAG = ShareActivity.class.getSimpleName();

    private static final String TAG_SHARE_FRAGMENT = "SHARE_FRAGMENT";
    private static final String TAG_SEARCH_FRAGMENT = "SEARCH_USER_AND_GROUPS_FRAGMENT";
    private static final String TAG_EDIT_SHARE_FRAGMENT = "EDIT_SHARE_FRAGMENT";

    /**
     * Tag for dialog
     */
    private static final String FTAG_CHOOSER_DIALOG = "CHOOSER_DIALOG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.share_activity);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        if (savedInstanceState == null) {
            // Add Share fragment on first creation
            Fragment fragment = ShareFileFragment.newInstance(getFile(), getAccount());
            ft.replace(R.id.share_fragment_container, fragment, TAG_SHARE_FRAGMENT);
            ft.commit();
        }

    }

    protected void onAccountSet(boolean stateWasRecovered) {
        super.onAccountSet(stateWasRecovered);

        // Load data into the list
        Log_OC.d(TAG, "Refreshing lists on account set");
        refreshSharesFromStorageManager();

        // Request for a refresh of the data through the server (starts an Async Task)
        refreshUsersOrGroupsListFromServer();
    }


    @Override
    protected void onNewIntent(Intent intent) {
        // Verify the action and get the query
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            Log_OC.w(TAG, "Ignored Intent requesting to query for " + query);

        } else if (UsersAndGroupsSearchProvider.ACTION_SHARE_WITH.equals(intent.getAction())) {
            Uri data = intent.getData();
            String dataString = intent.getDataString();
            String shareWith = dataString.substring(dataString.lastIndexOf('/') + 1);
            doShareWith(
                    shareWith,
                    data.getAuthority()
            );

        } else {
            Log_OC.wtf(TAG, "Unexpected intent " + intent.toString());
        }
    }

    private void doShareWith(String shareeName, String dataAuthority) {

        ShareType shareType = UsersAndGroupsSearchProvider.getShareType(dataAuthority);

        getFileOperationsHelper().shareFileWithSharee(
                getFile(),
                shareeName,
                shareType,
                getAppropiatePermissions(shareType)
        );
    }


    private int getAppropiatePermissions(ShareType shareType) {

        // check if the Share is FERERATED
        boolean isFederated = ShareType.FEDERATED.equals(shareType);

        if (getFile().isSharedWithMe()) {
            return OCShare.READ_PERMISSION_FLAG;    // minimum permissions

        } else if (getFile().isFolder()) {
            return (isFederated) ? OCShare.FEDERATED_PERMISSIONS_FOR_FOLDER : OCShare.MAXIMUM_PERMISSIONS_FOR_FOLDER;

        } else {    // isFile
            return (isFederated) ? OCShare.FEDERATED_PERMISSIONS_FOR_FILE : OCShare.MAXIMUM_PERMISSIONS_FOR_FILE;
        }
    }


    @Override
    public void showSearchUsersAndGroups() {
        // replace ShareFragment with SearchFragment on demand
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment searchFragment = SearchShareesFragment.newInstance(getFile(), getAccount());
        ft.replace(R.id.share_fragment_container, searchFragment, TAG_SEARCH_FRAGMENT);
        ft.addToBackStack(null);    // BACK button will recover the ShareFragment
        ft.commit();
    }

    @Override
    public void showEditShare(OCShare share) {
        // replace current fragment with EditShareFragment on demand
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment editShareFragment = EditShareFragment.newInstance(share, getFile(), getAccount());
        ft.replace(R.id.share_fragment_container, editShareFragment, TAG_EDIT_SHARE_FRAGMENT);
        ft.addToBackStack(null);    // BACK button will recover the previous fragment
        ft.commit();
    }

    @Override
    // Call to Unshare operation
    public void unshareWith(OCShare share) {
        OCFile file = getFile();
        getFileOperationsHelper().unshareFileWithUserOrGroup(file, share.getShareType(), share.getShareWith());
    }

    /**
     * Get users and groups from the server to fill in the "share with" list
     */
    @Override
    public void refreshUsersOrGroupsListFromServer() {
        // Show loading
        showLoadingDialog(getString(R.string.common_loading));
        // Get Users and Groups
        GetShareWithUsersAsyncTask getTask = new GetShareWithUsersAsyncTask(this);
        Object[] params = {getFile(), getAccount(), getStorageManager()};
        getTask.execute(params);
    }

    /**
     * Updates the view associated to the activity after the finish of some operation over files
     * in the current account.
     *
     * @param operation Removal operation performed.
     * @param result    Result of the removal.
     */
    @Override
    public void onRemoteOperationFinish(RemoteOperation operation, RemoteOperationResult result) {
        super.onRemoteOperationFinish(operation, result);

        if (result.isSuccess() ||
                (operation instanceof GetSharesForFileOperation &&
                        result.getCode() == RemoteOperationResult.ResultCode.SHARE_NOT_FOUND
                )
                ) {
            Log_OC.d(TAG, "Refreshing view on successful operation or finished refresh");
            refreshSharesFromStorageManager();
        }

        if (operation instanceof CreateShareViaLinkOperation && result.isSuccess()) {
            // Send link to the app
            String link = ((OCShare) (result.getData().get(0))).getShareLink();
            Log_OC.d(TAG, "Share link = " + link);

            Intent intentToShareLink = new Intent(Intent.ACTION_SEND);
            intentToShareLink.putExtra(Intent.EXTRA_TEXT, link);
            intentToShareLink.setType("text/plain");
            String[] packagesToExclude = new String[]{getPackageName()};
            DialogFragment chooserDialog = ShareLinkToDialog.newInstance(intentToShareLink, packagesToExclude);
            chooserDialog.show(getSupportFragmentManager(), FTAG_CHOOSER_DIALOG);
        }

        if (operation instanceof UnshareOperation && result.isSuccess() && getEditShareFragment() != null) {
            getSupportFragmentManager().popBackStack();
        }

        if (operation instanceof UpdateSharePermissionsOperation
                && getEditShareFragment() != null && getEditShareFragment().isAdded()) {
            getEditShareFragment().onUpdateSharePermissionsFinished(result);
        }

    }


    /**
     * Updates the view, reading data from {@link com.cerema.cloud2.datamodel.FileDataStorageManager}
     */
    private void refreshSharesFromStorageManager() {

        ShareFileFragment shareFileFragment = getShareFileFragment();
        if (shareFileFragment != null
                && shareFileFragment.isAdded()) {   // only if added to the view hierarchy!!
            shareFileFragment.refreshCapabilitiesFromDB();
            shareFileFragment.refreshUsersOrGroupsListFromDB();
            shareFileFragment.refreshPublicShareFromDB();
        }

        SearchShareesFragment searchShareesFragment = getSearchFragment();
        if (searchShareesFragment != null &&
                searchShareesFragment.isAdded()) {  // only if added to the view hierarchy!!
            searchShareesFragment.refreshUsersOrGroupsListFromDB();
        }

        EditShareFragment editShareFragment = getEditShareFragment();
        if (editShareFragment != null &&
                editShareFragment.isAdded()) {
            editShareFragment.refreshUiFromDB();
        }

    }

    /**
     * Shortcut to get access to the {@link ShareFileFragment} instance, if any
     *
     * @return A {@link ShareFileFragment} instance, or null
     */
    private ShareFileFragment getShareFileFragment() {
        return (ShareFileFragment) getSupportFragmentManager().findFragmentByTag(TAG_SHARE_FRAGMENT);
    }

    /**
     * Shortcut to get access to the {@link SearchShareesFragment} instance, if any
     *
     * @return A {@link SearchShareesFragment} instance, or null
     */
    private SearchShareesFragment getSearchFragment() {
        return (SearchShareesFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEARCH_FRAGMENT);
    }

    /**
     * Shortcut to get access to the {@link EditShareFragment} instance, if any
     *
     * @return A {@link EditShareFragment} instance, or null
     */
    private EditShareFragment getEditShareFragment() {
        return (EditShareFragment) getSupportFragmentManager().findFragmentByTag(TAG_EDIT_SHARE_FRAGMENT);
    }

}
