/*
 * Copyright (C) 2015 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.klinker.android.send_message;

/**
 * The action and extras of the broadcasts that hand on an MMS the system has downloaded or sent
 */
public final class MmsIntents {
    /**
     * Hands a downloaded MMS on to the receiver whose task affinity this is
     */
    public static final String MMS_RECEIVED = "com.klinker.android.messaging.MMS_RECEIVED";

    /** Where the downloaded or sent MMS is stored */
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_LOCATION_URL = "location_url";
    /** The notification of the downloaded MMS */
    public static final String EXTRA_URI = "notification_ind_uri";
    public static final String EXTRA_SUBSCRIPTION_ID = "subscription_id";

    private MmsIntents() {
    }
}
