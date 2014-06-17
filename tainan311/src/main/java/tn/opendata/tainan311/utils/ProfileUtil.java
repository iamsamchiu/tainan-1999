package tn.opendata.tainan311.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;


import static android.provider.ContactsContract.CommonDataKinds.*;
import static android.provider.ContactsContract.*;



public class ProfileUtil {

    /*get name from shared_pref or contacts provider*/
    public static String getUserName(Context context){
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString("user_name",getUserProfile(context).name);
    }


    public static UserProfile getUserProfile(Context context) {
        final ContentResolver content = context.getContentResolver();
        final Cursor cursor = content.query(
                Uri.withAppendedPath(
                        Profile.CONTENT_URI,
                        Contacts.Data.CONTENT_DIRECTORY),
                ProfileQuery.PROJECTION,

                // Selects only email addresses or names
                Contacts.Data.MIMETYPE + "=? OR "
                        + Contacts.Data.MIMETYPE + "=?",
                new String[]{
                        Email.CONTENT_ITEM_TYPE,
                        StructuredName.CONTENT_ITEM_TYPE
                },
                Contacts.Data.IS_PRIMARY + " DESC"
        );

        String name = "noname";
        String email = "nomail";
        String mime_type;
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    mime_type = getString(cursor, Contacts.Data.MIMETYPE);
                    if (mime_type.equals(Email.CONTENT_ITEM_TYPE)) {
                        if ("nomail".equals(email)) {
                            email = getString(cursor, Email.ADDRESS);
                        } else if (getInt(cursor, Email.IS_PRIMARY) > 0) {
                            email = getString(cursor, Email.ADDRESS);
                        }
                    } else if (mime_type.equals(StructuredName.CONTENT_ITEM_TYPE)) {
                        name = getString(cursor, StructuredName.DISPLAY_NAME);


                        //mark below... and use display name instead
//                        String fname = getString(cursor, StructuredName.FAMILY_NAME);
//                        //TODO: should really check alphabet
//                        if (fname.length() < 2) { //must be chinese...
//                            name = getString(cursor, StructuredName.FAMILY_NAME) + getString(cursor, StructuredName.GIVEN_NAME);
//
//                        } else {
//                            name = getString(cursor, StructuredName.GIVEN_NAME) + " " + getString(cursor, StructuredName.FAMILY_NAME);
//                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cursor.close();
            }

        }


        return new UserProfile(email, name);
    }

    private static int getInt(Cursor cursor, String field) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(field));
    }

    private static String getString(Cursor cursor, String field) {
        return cursor.getString(cursor.getColumnIndexOrThrow(field));
    }



    public static final class UserProfile {
        public final String email;
        private final String name;  //do not use this.. use getUserName

        public UserProfile(String email, String name) {
            this.email = email;
            this.name = name;
        }
    }

    /**
     * Contacts user profile query interface.
     */
    private interface ProfileQuery {
        /**
         * The set of columns to extract from the profile query results
         */
        String[] PROJECTION = {
                Email.ADDRESS,
                Email.IS_PRIMARY,
                StructuredName.FAMILY_NAME,
                StructuredName.GIVEN_NAME,
                StructuredName.DISPLAY_NAME,
                Contacts.Data.MIMETYPE
        };
    }
}