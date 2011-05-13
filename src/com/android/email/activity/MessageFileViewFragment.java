/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.email.Email;
import com.android.email.R;
import com.android.emailcommon.Logging;
import com.android.emailcommon.provider.EmailContent.Message;
import com.android.emailcommon.utility.Utility;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.security.InvalidParameterException;

/**
 * A {@link MessageViewFragmentBase} subclass for file based messages. (aka EML files)
 *
 * See {@link MessageViewBase} for the class relation diagram.
 */
public class MessageFileViewFragment extends MessageViewFragmentBase {
    /**
     * URI of message to open.  Protect with {@link #mLock}.
     */
    private Uri mFileEmailUri;

    /**
     * # of instances of this class.  When it gets 0, and the last one is not destroying for
     * a config change, we delete all the EML files.
     */
    private static int sFragmentCount;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sFragmentCount++;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // If this is the last fragment of its kind, delete any/all attachment messages
        sFragmentCount--;
        if ((sFragmentCount == 0) && !getActivity().isChangingConfigurations()) {
            getController().deleteAttachmentMessages();
        }
    }

    /** Called by activities with a URI to an EML file. */
    public void openMessage(Uri fileEmailUri) {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, "MessageFileViewFragment openMessage");
        }
        if (mFileEmailUri != null) {
            // Unlike MessageViewFragment, this fragment doesn't support loading another message
            // once it opens a message, even after clearContent().
            throw new IllegalStateException();
        }
        if (fileEmailUri == null) {
            throw new InvalidParameterException();
        }
        mFileEmailUri = fileEmailUri;
        loadMessageIfResumed();
    }

    @Override
    protected void clearContent() {
        super.clearContent();
    }

    @Override
    protected boolean isMessageSpecified() {
        return mFileEmailUri != null;
    }

    /**
     * NOTE See the comment on the super method.  It's called on a worker thread.
     */
    @Override
    protected Message openMessageSync(Activity activity) {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, "MessageFileViewFragment openMessageSync");
        }
        Uri messageUri = mFileEmailUri;
        if (messageUri == null) {
            return null; // Called after clearContent().
        }
        // Put up a toast; this can take a little while...
        Utility.showToast(activity, R.string.message_view_parse_message_toast);
        Message msg = getController().loadMessageFromUri(messageUri);
        if (msg == null) {
            // Indicate that the attachment couldn't be loaded
            Utility.showToast(activity, R.string.message_view_display_attachment_toast);
            return null;
        }
        return msg;
    }

    /**
     * {@inheritDoc}
     *
     * Does exactly same as the super class method, but does an extra sanity check.
     */
    @Override
    protected void reloadUiFromMessage(Message message, boolean okToFetch) {
        // EML file should never be partially loaded.
        if (message.mFlagLoaded != Message.FLAG_LOADED_COMPLETE) {
            throw new IllegalStateException();
        }
        super.reloadUiFromMessage(message, okToFetch);
    }
}
