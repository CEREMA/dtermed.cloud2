/**
 *   ownCloud Android client application
 *
 *   @author masensio
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
package com.cerema.cloud2.ui.adapter;

import com.cerema.cloud2.R;
import com.cerema.cloud2.lib.common.network.CertificateCombinedException;
import com.cerema.cloud2.ui.dialog.SslUntrustedCertDialog;

import android.view.View;
import android.widget.TextView;

/**
 * TODO
 *
 */
public class CertificateCombinedExceptionViewAdapter implements SslUntrustedCertDialog.ErrorViewAdapter {
    
    //private final static String TAG = CertificateCombinedExceptionViewAdapter.class.getSimpleName();
    
    private CertificateCombinedException mSslException = null;
    
    public CertificateCombinedExceptionViewAdapter(CertificateCombinedException sslException) {
        mSslException = sslException;
    }
    
    @Override
    public void updateErrorView(View dialogView) {
        /// clean
        dialogView.findViewById(R.id.reason_no_info_about_error).setVisibility(View.GONE);
       
        /// refresh
        if (mSslException.getCertPathValidatorException() != null) {
            ((TextView)dialogView.findViewById(R.id.reason_cert_not_trusted)).setVisibility(View.VISIBLE);
        } else {
            dialogView.findViewById(R.id.reason_cert_not_trusted).setVisibility(View.GONE);
        }
        
        if (mSslException.getCertificateExpiredException() != null) {
            ((TextView)dialogView.findViewById(R.id.reason_cert_expired)).setVisibility(View.VISIBLE);
        } else {
            dialogView.findViewById(R.id.reason_cert_expired).setVisibility(View.GONE);
        }
        
        if (mSslException.getCertificateNotYetValidException() != null) {
            ((TextView)dialogView.findViewById(R.id.reason_cert_not_yet_valid)).setVisibility(View.VISIBLE);
        } else {
            dialogView.findViewById(R.id.reason_cert_not_yet_valid).setVisibility(View.GONE);
        }

        if (mSslException.getSslPeerUnverifiedException() != null) {
            ((TextView)dialogView.findViewById(R.id.reason_hostname_not_verified)).setVisibility(View.VISIBLE);
        } else {
            dialogView.findViewById(R.id.reason_hostname_not_verified).setVisibility(View.GONE);
        }
        
    }
}
