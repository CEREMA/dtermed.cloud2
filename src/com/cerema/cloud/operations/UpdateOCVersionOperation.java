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

package com.cerema.cloud2.operations;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.json.JSONException;
import org.json.JSONObject;

import com.cerema.cloud2.authentication.AccountUtils;
import com.cerema.cloud2.lib.common.OwnCloudClient;
import com.cerema.cloud2.lib.common.accounts.AccountUtils.Constants;
import com.cerema.cloud2.lib.common.operations.RemoteOperation;
import com.cerema.cloud2.lib.common.operations.RemoteOperationResult;
import com.cerema.cloud2.lib.common.operations.RemoteOperationResult.ResultCode;
import com.cerema.cloud2.lib.common.utils.Log_OC;
import com.cerema.cloud2.lib.resources.status.OwnCloudVersion;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;


/**
 * Remote operation that checks the version of an ownCloud server and stores it locally
 */
public class UpdateOCVersionOperation extends RemoteOperation {

    private static final String TAG = UpdateOCVersionOperation.class.getSimpleName();

    private Account mAccount;
    private Context mContext;
    private OwnCloudVersion mOwnCloudVersion;
    
    
    public UpdateOCVersionOperation(Account account, Context context) {
        mAccount = account;
        mContext = context;
        mOwnCloudVersion = null;
    }
    
    
    @Override
    protected RemoteOperationResult run(OwnCloudClient client) {
        AccountManager accountMngr = AccountManager.get(mContext); 
        String statUrl = accountMngr.getUserData(mAccount, Constants.KEY_OC_BASE_URL);
        statUrl += AccountUtils.STATUS_PATH;
        RemoteOperationResult result = null;
        GetMethod get = null;
        try {
            get = new GetMethod(statUrl);
            int status = client.executeMethod(get);
            if (status != HttpStatus.SC_OK) {
                client.exhaustResponse(get.getResponseBodyAsStream());
                result = new RemoteOperationResult(false, status, get.getResponseHeaders());
                
            } else {
                String response = get.getResponseBodyAsString();
                if (response != null) {
                    JSONObject json = new JSONObject(response);
                    if (json != null && json.getString("version") != null) {

                        String version = json.getString("version");
                        mOwnCloudVersion = new OwnCloudVersion(version);
                        if (mOwnCloudVersion.isVersionValid()) {
                            accountMngr.setUserData(mAccount, Constants.KEY_OC_VERSION, mOwnCloudVersion.getVersion());
                            Log_OC.d(TAG, "Got new OC version " + mOwnCloudVersion.toString());

                            result = new RemoteOperationResult(ResultCode.OK);
                            
                        } else {
                            Log_OC.w(TAG, "Invalid version number received from server: " + json.getString("version"));
                            result = new RemoteOperationResult(RemoteOperationResult.ResultCode.BAD_OC_VERSION);
                        }
                    }
                }
                if (result == null) {
                    result = new RemoteOperationResult(RemoteOperationResult.ResultCode.INSTANCE_NOT_CONFIGURED);
                }
            }
            Log_OC.i(TAG, "Check for update of ownCloud server version at " + client.getWebdavUri() + ": " + result.getLogMessage());
            
        } catch (JSONException e) {
            result = new RemoteOperationResult(RemoteOperationResult.ResultCode.INSTANCE_NOT_CONFIGURED);
            Log_OC.e(TAG, "Check for update of ownCloud server version at " + client.getWebdavUri() + ": " + result.getLogMessage(), e);
                
        } catch (Exception e) {
            result = new RemoteOperationResult(e);
            Log_OC.e(TAG, "Check for update of ownCloud server version at " + client.getWebdavUri() + ": " + result.getLogMessage(), e);
            
        } finally {
            if (get != null) 
                get.releaseConnection();
        }
        return result;
    }


    public OwnCloudVersion getOCVersion() {
        return mOwnCloudVersion;
    }

}
