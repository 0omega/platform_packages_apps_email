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
import com.android.emailcommon.mail.MeetingInfo;
import com.android.emailcommon.mail.PackedString;
import com.android.emailcommon.provider.EmailContent.Mailbox;
import com.android.emailcommon.provider.EmailContent.Message;
import com.android.emailcommon.service.EmailServiceConstants;
import com.android.emailcommon.utility.Utility;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;

import java.security.InvalidParameterException;

/**
 * A {@link MessageViewFragmentBase} subclass for regular email messages.  (regular as in "not eml
 * files").
 *
 * See {@link MessageViewBase} for the class relation diagram.
 */
public class MessageViewFragment extends MessageViewFragmentBase
        implements CheckBox.OnCheckedChangeListener {
    /** Argument name(s) */
    private static final String ARG_MESSAGE_ID = "messageId";

    private ImageView mFavoriteIcon;

    private View mReplyButton;
    private View mReplyAllButton;
    private View mForwardButton;

    // calendar meeting invite answers
    private CheckBox mMeetingYes;
    private CheckBox mMeetingMaybe;
    private CheckBox mMeetingNo;
    private Drawable mFavoriteIconOn;
    private Drawable mFavoriteIconOff;

    private int mPreviousMeetingResponse = EmailServiceConstants.MEETING_REQUEST_NOT_RESPONDED;

    /**
     * ID of the message that will be loaded.  Protect with {@link #mLock}.
     */
    private long mMessageIdToOpen = -1;

    /** Lock object to protect {@link #mMessageIdToOpen} */
    private final Object mLock = new Object();

    /** ID of the currently shown message */
    private long mCurrentMessageId = -1;

    /**
     * This class has more call backs than {@link MessageViewFragmentBase}.
     *
     * - EML files can't be "mark unread".
     * - EML files can't have the invite buttons or the view in calender link.
     *   Note EML files can have ICS (calendar invitation) files, but we don't treat them as
     *   invites.  (Only exchange provider sets the FLAG_INCOMING_MEETING_INVITE
     *   flag.)
     *   It'd be weird to respond to an invitation in an EML that might not be addressed to you...
     */
    public interface Callback extends MessageViewFragmentBase.Callback {
        /** Called when the "view in calendar" link is clicked. */
        public void onCalendarLinkClicked(long epochEventStartTime);

        /**
         * Called when a calender response button is clicked.
         *
         * @param response one of {@link EmailServiceConstants#MEETING_REQUEST_ACCEPTED},
         * {@link EmailServiceConstants#MEETING_REQUEST_DECLINED}, or
         * {@link EmailServiceConstants#MEETING_REQUEST_TENTATIVE}.
         */
        public void onRespondedToInvite(int response);

        /** Called when the current message is set unread. */
        public void onMessageSetUnread();

        /**
         * Called right before the current message will be deleted.
         * Callees don't have to delete messages.  The fragment does.
         */
        public void onBeforeMessageDelete();

        /** Called when the move button is pressed. */
        public void onMoveMessage();
        /** Called when the forward button is pressed. */
        public void onForward();
        /** Called when the reply button is pressed. */
        public void onReply();
        /** Called when the reply-all button is pressed. */
        public void onReplyAll();
    }

    public static final class EmptyCallback extends MessageViewFragmentBase.EmptyCallback
            implements Callback {
        @SuppressWarnings("hiding")
        public static final Callback INSTANCE = new EmptyCallback();

        @Override public void onCalendarLinkClicked(long epochEventStartTime) { }
        @Override public void onMessageSetUnread() { }
        @Override public void onRespondedToInvite(int response) { }
        @Override public void onBeforeMessageDelete() { }
        @Override public void onMoveMessage() { }
        @Override public void onForward() { }
        @Override public void onReply() { }
        @Override public void onReplyAll() { }
    }

    private Callback mCallback = EmptyCallback.INSTANCE;

    /**
     * Create a new instance with initialization parameters.
     *
     * This fragment should be created only with this method.  (Arguments should always be set.)
     */
    public static MessageViewFragment newInstance(long messageId) {
        final MessageViewFragment instance = new MessageViewFragment();
        final Bundle args = new Bundle();
        args.putLong(ARG_MESSAGE_ID, messageId);
        instance.setArguments(args);
        return instance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Resources res = getActivity().getResources();
        mFavoriteIconOn = res.getDrawable(R.drawable.btn_star_on_normal_email_holo_light);
        mFavoriteIconOff = res.getDrawable(R.drawable.btn_star_off_normal_email_holo_light);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        mFavoriteIcon = (ImageView) UiUtilities.getView(view, R.id.favorite);
        mReplyButton = UiUtilities.getView(view, R.id.reply);
        mReplyAllButton = UiUtilities.getView(view, R.id.reply_all);
        mForwardButton = UiUtilities.getView(view, R.id.forward);
        mMeetingYes = (CheckBox) UiUtilities.getView(view, R.id.accept);
        mMeetingMaybe = (CheckBox) UiUtilities.getView(view, R.id.maybe);
        mMeetingNo = (CheckBox) UiUtilities.getView(view, R.id.decline);

        // Star is only visible on this fragment (as opposed to MessageFileViewFragment.)
        UiUtilities.getView(view, R.id.favorite).setVisibility(View.VISIBLE);

        mFavoriteIcon.setOnClickListener(this);
        mReplyButton.setOnClickListener(this);
        mReplyAllButton.setOnClickListener(this);
        mForwardButton.setOnClickListener(this);
        mMeetingYes.setOnCheckedChangeListener(this);
        mMeetingMaybe.setOnCheckedChangeListener(this);
        mMeetingNo.setOnCheckedChangeListener(this);
        UiUtilities.getView(view, R.id.invite_link).setOnClickListener(this);

        enableReplyForwardButtons(false);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Bundle args = getArguments();
        // STOPSHIP remove the check.  Right now it's needed for the obsolete phone activities.
        if (args != null) {
            openMessage(args.getLong(ARG_MESSAGE_ID));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.message_view_fragment_option, menu);
    }

    private void enableReplyForwardButtons(boolean enabled) {
        // We don't have disabled button assets, so let's hide them for now
        final int visibility = enabled ? View.VISIBLE : View.GONE;
        mReplyButton.setVisibility(visibility);
        mReplyAllButton.setVisibility(visibility);
        mForwardButton.setVisibility(visibility);
    }

    public void setCallback(Callback callback) {
        mCallback = (callback == null) ? EmptyCallback.INSTANCE : callback;
        super.setCallback(mCallback);
    }

    /** Called by activities to set an id of a message to open. */
    // STOPSHIP Make it private once phone activities are gone
    void openMessage(long messageId) {
        if (Email.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, "MessageViewFragment openMessage");
        }
        if (messageId == -1) {
            throw new InvalidParameterException();
        }
        synchronized (mLock) {
            mMessageIdToOpen = messageId;
        }
        loadMessageIfResumed();
    }

    @Override
    protected void clearContent() {
        synchronized (mLock) {
            super.clearContent();
            mMessageIdToOpen = -1;

            // Hide the menu.
            // This isn't really necessary if we're really hiding the fragment.  However,
            // for now, we're actually *not* hiding the fragment (just hiding the root view of it),
            // so need to remove menus manually.
            setHasOptionsMenu(false);
        }
    }

    @Override
    protected void resetView() {
        super.resetView();
        mMeetingYes.setChecked(false);
        mMeetingNo.setChecked(false);
        mMeetingMaybe.setChecked(false);
        mPreviousMeetingResponse = EmailServiceConstants.MEETING_REQUEST_NOT_RESPONDED;
    }

    @Override
    protected boolean isMessageSpecified() {
        synchronized (mLock) {
            return mMessageIdToOpen != -1;
        }
    }

    /**
     * NOTE See the comment on the super method.  It's called on a worker thread.
     */
    @Override
    protected Message openMessageSync(Activity activity) {
        synchronized (mLock) {
            long messageId = mMessageIdToOpen;
            if (messageId < 0) {
                return null; // Called after clearContent().
            }
            return Message.restoreMessageWithId(activity, messageId);
        }
    }

    @Override
    protected void onMessageShown(long messageId, int mailboxType) {
        super.onMessageShown(messageId, mailboxType);

        // Remember the currently shown message ID.
        mCurrentMessageId = messageId;

        // Disable forward/reply buttons as necessary.
        enableReplyForwardButtons(Mailbox.isMailboxTypeReplyAndForwardable(mailboxType));

        // Show the menu when it's showing a message.
        setHasOptionsMenu(true);
    }

    /**
     * Toggle favorite status and write back to provider
     */
    private void onClickFavorite() {
        Message message = getMessage();

        // Update UI
        boolean newFavorite = ! message.mFlagFavorite;
        mFavoriteIcon.setImageDrawable(newFavorite ? mFavoriteIconOn : mFavoriteIconOff);

        // Update provider
        message.mFlagFavorite = newFavorite;
        getController().setMessageFavorite(message.mId, newFavorite);
    }

    /**
     * Set message read/unread.
     */
    public void onMarkMessageAsRead(boolean isRead) {
        Message message = getMessage();
        if (message.mFlagRead != isRead) {
            message.mFlagRead = isRead;
            getController().setMessageRead(message.mId, isRead);
            if (!isRead) { // Became unread.  We need to close the message.
                mCallback.onMessageSetUnread();
            }
        }
    }

    /**
     * Send a service message indicating that a meeting invite button has been clicked.
     */
    private void onRespondToInvite(int response, int toastResId) {
        Message message = getMessage();
        // do not send twice in a row the same response
        if (mPreviousMeetingResponse != response) {
            getController().sendMeetingResponse(message.mId, response);
            mPreviousMeetingResponse = response;
        }
        Utility.showToast(getActivity(), toastResId);
        mCallback.onRespondedToInvite(response);
    }

    private void onInviteLinkClicked() {
        Message message = getMessage();
        String startTime = new PackedString(message.mMeetingInfo).get(MeetingInfo.MEETING_DTSTART);
        if (startTime != null) {
            long epochTimeMillis = Utility.parseEmailDateTimeToMillis(startTime);
            mCallback.onCalendarLinkClicked(epochTimeMillis);
        } else {
            Email.log("meetingInfo without DTSTART " + message.mMeetingInfo);
        }
    }

    @Override
    public void onClick(View view) {
        if (!isMessageOpen()) {
            return; // Ignore.
        }
        switch (view.getId()) {
            case R.id.reply:
                mCallback.onReply();
                return;
            case R.id.reply_all:
                mCallback.onReplyAll();
                return;
            case R.id.forward:
                mCallback.onForward();
                return;

            case R.id.favorite:
                onClickFavorite();
                return;

            case R.id.invite_link:
                onInviteLinkClicked();
                return;
        }
        super.onClick(view);
    }

    @Override
    public void onCheckedChanged(CompoundButton view, boolean isChecked) {
        if (!isChecked) return;
        switch (view.getId()) {
            case R.id.accept:
                onRespondToInvite(EmailServiceConstants.MEETING_REQUEST_ACCEPTED,
                        R.string.message_view_invite_toast_yes);
                return;
            case R.id.maybe:
                onRespondToInvite(EmailServiceConstants.MEETING_REQUEST_TENTATIVE,
                        R.string.message_view_invite_toast_maybe);
                return;
            case R.id.decline:
                onRespondToInvite(EmailServiceConstants.MEETING_REQUEST_DECLINED,
                        R.string.message_view_invite_toast_no);
                return;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.move:
                onMove();
                return true;
            case R.id.delete:
                onDelete();
                return true;
            case R.id.mark_as_unread:
                onMarkAsUnread();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onMove() {
        mCallback.onMoveMessage();
    }

    private void onDelete() {
        mCallback.onBeforeMessageDelete();
        ActivityHelper.deleteMessage(mContext, mCurrentMessageId);
    }

    private void onMarkAsUnread() {
        onMarkMessageAsRead(false);
    }

    /**
     * {@inheritDoc}
     *
     * Mark the current as unread.
     */
    @Override
    protected void onPostLoadBody() {
        onMarkMessageAsRead(true);
    }

    @Override
    protected void updateHeaderView(Message message) {
        super.updateHeaderView(message);

        mFavoriteIcon.setImageDrawable(message.mFlagFavorite ? mFavoriteIconOn : mFavoriteIconOff);

        // Enable the invite tab if necessary
        if ((message.mFlags & Message.FLAG_INCOMING_MEETING_INVITE) != 0) {
            addTabFlags(TAB_FLAGS_HAS_INVITE);
        }
    }
}
