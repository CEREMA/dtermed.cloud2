/**
 *   ownCloud Android client application
 *
 *   @author David A. Velasco
 *   @author masensio
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

import java.util.ArrayList;

import com.cerema.cloud2.authentication.AccountUtils;
import com.cerema.cloud2.lib.common.OwnCloudClient;
import com.cerema.cloud2.lib.common.operations.RemoteOperation;
import com.cerema.cloud2.lib.common.operations.RemoteOperationResult;
import com.cerema.cloud2.lib.common.operations.RemoteOperationResult.ResultCode;
import com.cerema.cloud2.lib.common.utils.Log_OC;
import com.cerema.cloud2.lib.resources.status.GetRemoteStatusOperation;
import com.cerema.cloud2.lib.resources.status.OwnCloudVersion;
import com.cerema.cloud2.operations.DetectAuthenticationMethodOperation.AuthenticationMethod;

import android.content.Context;

/**
 * Get basic information from an ownCloud server given its URL.
 * 
 * Checks the existence of a configured ownCloud server in the URL, gets its version 
 * and finds out what authentication method is needed to access files in it.
 */

public class GetServerInfoOperation extends RemoteOperation {
    
    private static final String TAG = GetServerInfoOperation.class.getSimpleName();
    
    private String mUrl;
    private Context mContext;
    
    private ServerInfo mResultData;

    /** 
     * Constructor.
     * 
     * @param url               URL to an ownCloud server.
     * @param context           Android context; needed to check network state
     *                          TODO ugly dependency, get rid of it. 
     */
    public GetServerInfoOperation(String url, Context context) {
        mUrl = trimWebdavSuffix(url);
        mContext = context;
        
        mResultData = new ServerInfo();
    }
    
    
    /**
     * Performs the operation
     * 
     * @return      Result of the operation. If successful, includes an instance of 
     *              {@link ServerInfo} with the information retrieved from the server. 
     *              Call {@link RemoteOperationResult#getData()}.get(0) to get it.
     */
	@Override
	protected RemoteOperationResult run(OwnCloudClient client) {
	    
	    // first: check the status of the server (including its version)
	    GetRemoteStatusOperation getStatus = new GetRemoteStatusOperation(mContext);
	    RemoteOperationResult result = getStatus.execute(client);
	    
        if (result.isSuccess()) {
            // second: get authentication method required by the server
            mResultData.mVersion = (OwnCloudVersion)(result.getData().get(0));
            mResultData.mIsSslConn = (result.getCode() == ResultCode.OK_SSL);
            mResultData.mBaseUrl = normalizeProtocolPrefix(mUrl, mResultData.mIsSslConn);
            RemoteOperationResult detectAuthResult = detectAuthorizationMethod(client);
            
            // third: merge results
            if (detectAuthResult.isSuccess()) {
                mResultData.mAuthMethod = 
                        (AuthenticationMethod)detectAuthResult.getData().get(0);
                ArrayList<Object> data = new ArrayList<Object>();
                data.add(mResultData);
                result.setData(data);
            } else {
                result = detectAuthResult;
            }
        }
        return result;
	}

	
    private RemoteOperationResult detectAuthorizationMethod(OwnCloudClient client) {
        Log_OC.d(TAG, "Trying empty authorization to detect authentication method");
        DetectAuthenticationMethodOperation operation = 
                new DetectAuthenticationMethodOperation(mContext);
        return operation.execute(client);
    }
    

    private String trimWebdavSuffix(String url) {
        if (url == null) {
            url = "";
        } else {
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            if(url.toLowerCase().endsWith(AccountUtils.WEBDAV_PATH_4_0_AND_LATER)){
                url = url.substring(0, url.length() - AccountUtils.WEBDAV_PATH_4_0_AND_LATER.length());
            }
        }
        return url;
    }

    
    private String normalizeProtocolPrefix(String url, boolean isSslConn) {
        if (!url.toLowerCase().startsWith("http://") &&
                !url.toLowerCase().startsWith("https://")) {
            if (isSslConn) {
                return "https://" + url;
            } else {
                return "http://" + url;
            }
        }
        return url;
    }
    
    
    public static class ServerInfo {
        public OwnCloudVersion mVersion = null;
        public String mBaseUrl = "";
        public AuthenticationMethod mAuthMethod = AuthenticationMethod.UNKNOWN;
        public boolean mIsSslConn = false;
    }
	
}
