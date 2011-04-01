/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.email.activity;

import com.android.email.Controller;
import com.android.email.ControllerResultUiThreadWrapper;
import com.android.email.Email;
import com.android.email.MessagingExceptionStrings;
import com.android.email.R;
import com.android.email.activity.setup.AccountSettingsXL;
import com.android.emailcommon.mail.MessagingException;
import com.android.emailcommon.provider.EmailContent.Account;
import com.android.emailcommon.provider.EmailContent.AccountColumns;
import com.android.emailcommon.utility.Utility;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

// TODO: This has a lot in common with MessageList --should we merge them somehow?
// But maybe we'll need to move to a single activity style anyway -- let's not worry about it too
// much for now.  We might even completely ditch this activity and switch to a dialog.
public class MailboxList extends Activity implements MailboxListFragment.Callback {

    // Intent extras (internal to this activity)
    private static final String EXTRA_ACCOUNT_ID = "com.android.email.activity._ACCOUNT_ID";

    // UI support
    private ActionBar mActionBar;
    private TextView mErrorBanner;
    private MailboxListFragment mListFragment;

    private Controller.Result mControllerCallback;

    // DB access
    private long mAccountId;
    private AsyncTask<Void, Void, String[]> mLoadAccountNameTask;

    /**
     * Open a specific account.
     *
     * @param context
     * @param accountId the account to view
     */
    public static void actionHandleAccount(Context context, long accountId) {
        Intent intent = new Intent(context, MailboxList.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_ACCOUNT_ID, accountId);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ActivityHelper.debugSetWindowFlags(this);

        mAccountId = getIntent().getLongExtra(EXTRA_ACCOUNT_ID, -1);
        if (mAccountId == -1) {
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.mailbox_list);

        mControllerCallback = new ControllerResultUiThreadWrapper<ControllerResults>(
                new Handler(), new ControllerResults());
        mActionBar = getActionBar();
        mErrorBanner = (TextView) findViewById(R.id.connection_error_text);
        mListFragment = (MailboxListFragment) getFragmentManager()
                .findFragmentById(R.id.mailbox_list_fragment);

        mActionBar.setTitle(R.string.mailbox_list_title);
        mListFragment.setCallback(this);
        mListFragment.openMailboxes(mAccountId);

        // Halt the progress indicator (we'll display it later when needed)
        setProgressBarIndeterminate(true);
        setProgressBarIndeterminateVisibility(false);

        // Go to the database for the account name
        mLoadAccountNameTask = new AsyncTask<Void, Void, String[]>() {
            @Override
            protected String[] doInBackground(Void... params) {
                String accountName = null;
                Uri uri = ContentUris.withAppendedId(Account.CONTENT_URI, mAccountId);
                Cursor c = MailboxList.this.getContentResolver().query(
                        uri, new String[] { AccountColumns.DISPLAY_NAME }, null, null, null);
                try {
                    if (c.moveToFirst()) {
                        accountName = c.getString(0);
                    }
                } finally {
                    c.close();
                }
                return new String[] { accountName };
            }

            @Override
            protected void onPostExecute(String[] result) {
                if (result == null) {
                    return;
                }
                final String accountName = (String) result[0];
                // accountName is null if account name can't be retrieved or query exception
                if (accountName == null) {
                    // something is wrong with this account
                    finish();
                }
                mActionBar.setTitle(R.string.mailbox_list_title);
                mActionBar.setSubtitle(accountName);
            }

        }.execute();
    }

    @Override
    public void onPause() {
        super.onPause();
        Controller.getInstance(getApplication()).removeResultCallback(mControllerCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        Controller.getInstance(getApplication()).addResultCallback(mControllerCallback);

        // Exit immediately if the accounts list has changed (e.g. externally deleted)
        if (Email.getNotifyUiAccountsChanged()) {
            Welcome.actionStart(this);
            finish();
            return;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Utility.cancelTaskInterrupt(mLoadAccountNameTask);
        mLoadAccountNameTask = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.mailbox_list_option, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onAccounts();
                return true;
            case R.id.refresh:
                onRefresh();
                return true;
            case R.id.compose:
                onCompose();
                return true;
            case R.id.account_settings:
                onEditAccount();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Implements MailboxFragment.Callback
     */
    @Override
    public void onMailboxSelected(long accountId, long mailboxId) {
        onOpenMailbox(mailboxId);
    }

    /**
     * Implements MailboxFragment.Callback
     */
    @Override
    public void onAccountSelected(long accountId) {
        // Only used on the Combined view, which isn't used on the phone UI.
    }

    /**
     * Implements MailboxFragment.Callback
     */
    @Override
    public void onCurrentMailboxUpdated(long mailboxId, String mailboxName, int unreadCount) {
    }

    /**
     * Refresh the mailbox list
     */
    private void onRefresh() {
        Controller controller = Controller.getInstance(getApplication());
        showProgressIcon(true);
        mListFragment.onRefresh();
    }

    private void onAccounts() {
        AccountFolderList.actionShowAccounts(this);
        finish();
    }

    private void onEditAccount() {
        AccountSettingsXL.actionSettings(this, mAccountId);
    }

    private void onOpenMailbox(long mailboxId) {
        MessageList.actionHandleMailbox(this, mailboxId);
    }

    private void onCompose() {
        MessageCompose.actionCompose(this, mAccountId);
    }

    private void showProgressIcon(boolean show) {
        setProgressBarIndeterminateVisibility(show);
    }

    private void showErrorBanner(String message) {
        boolean isVisible = mErrorBanner.getVisibility() == View.VISIBLE;
        if (message != null) {
            mErrorBanner.setText(message);
            if (!isVisible) {
                mErrorBanner.setVisibility(View.VISIBLE);
                mErrorBanner.startAnimation(
                        AnimationUtils.loadAnimation(
                                MailboxList.this, R.anim.header_appear));
            }
        } else {
            if (isVisible) {
                mErrorBanner.setVisibility(View.GONE);
                mErrorBanner.startAnimation(
                        AnimationUtils.loadAnimation(
                                MailboxList.this, R.anim.header_disappear));
            }
        }
    }

    /**
     * Controller results listener.  We wrap it with {@link ControllerResultUiThreadWrapper},
     * so all methods are called on the UI thread.
     */
    private class ControllerResults extends Controller.Result {

        @Override
        public void updateMailboxListCallback(MessagingException result, long accountKey,
                int progress) {
            if (accountKey == mAccountId) {
                updateBanner(result, progress);
                updateProgress(result, progress);
            }
        }

        @Override
        public void updateMailboxCallback(MessagingException result, long accountKey,
                long mailboxKey, int progress, int numNewMessages) {
            if (accountKey == mAccountId) {
                updateBanner(result, progress);
                updateProgress(result, progress);
            }
        }

        @Override
        public void sendMailCallback(MessagingException result, long accountId, long messageId,
                int progress) {
            if (accountId == mAccountId) {
                updateBanner(result, progress);
                updateProgress(result, progress);
            }
        }

        private void updateProgress(MessagingException result, int progress) {
            showProgressIcon(result == null && progress < 100);
        }

        /**
         * Show or hide the connection error banner, and convert the various MessagingException
         * variants into localizable text.  There is hysteresis in the show/hide logic:  Once shown,
         * the banner will remain visible until some progress is made on the connection.  The
         * goal is to keep it from flickering during retries in a bad connection state.
         *
         * @param result
         * @param progress
         */
        private void updateBanner(MessagingException result, int progress) {
            if (result != null) {
                showErrorBanner(
                        MessagingExceptionStrings.getErrorString(MailboxList.this, result));
            } else if (progress > 0) {
                showErrorBanner(null);
            }
        }
    }
}
