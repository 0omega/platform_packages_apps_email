/*
 * Copyright (C) 2008-2010 Marc Blank
 * Licensed to The Android Open Source Project.
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

package com.android.emailcommon.service;

import com.android.emailcommon.service.IEmailServiceCallback;
import android.os.Bundle;

interface IEmailService {
    Bundle validate(in String protocol, in String host, in String userName, in String password,
        int port, boolean ssl, boolean trustCertificates) ;

    oneway void startSync(long mailboxId, boolean userRequest);
    oneway void stopSync(long mailboxId);

    oneway void loadMore(long messageId);
    oneway void loadAttachment(long attachmentId, boolean background);

    oneway void updateFolderList(long accountId);

    boolean createFolder(long accountId, String name);
    boolean deleteFolder(long accountId, String name);
    boolean renameFolder(long accountId, String oldName, String newName);

    // Must not be oneway; unless an exception is thrown, the caller is guaranteed that the callback
    // has been registered
    void setCallback(IEmailServiceCallback cb);

    oneway void setLogging(int on);

    oneway void hostChanged(long accountId);

    Bundle autoDiscover(String userName, String password);

    oneway void sendMeetingResponse(long messageId, int response);

    oneway void moveMessage(long messageId, long mailboxId);

    // Must not be oneway; unless an exception is thrown, the caller is guaranteed that the action
    // has been completed
    void deleteAccountPIMData(long accountId);

    int getApiLevel();

    // API level 2
    int searchMessages(long accountId, long mailboxId, boolean includeSubfolders, String query,
        int numResults, int firstResult, long destMailboxId);
}