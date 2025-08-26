package com.codingEmpire.bitbloom.fcm


import android.os.AsyncTask
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.common.collect.Lists
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class AccessToken {
    companion object {
        private const val FIREBASE_MESSAGING_SCOPE =
            "https://www.googleapis.com/auth/firebase.messaging"

        fun getAccessTokenAsync(callback: AccessTokenCallback) {
            AccessTokenTask(callback).execute()
        }
    }

    private class AccessTokenTask(private val callback: AccessTokenCallback) :
        AsyncTask<Void, Void, String?>() {

        override fun doInBackground(vararg params: Void?): String? {
            return try {
                val jsonString = """
               {
  "type": "service_account",
  "project_id": "investment-app-11ac4",
  "private_key_id": "REDACTED_KEY_ID",
  "private_key": "REDACTED_KEY_START\nREDACTED_KEY_BODY\nSK6LmIBGIOycQhxTQEips6giEtOpn818RSKY7uQTsiKuygG7GD3NHsxXX1OLqXyQ\nun9MCawwsfaA9rmN36wy26tyFZv+xlZiPxwq8sM7/a8stWp9oXaUCbsx+7TUoCtT\ntreBF2nyRRi6LFGI97xzWx/NFt1ZJnZmKxSs+wKeGx7lS23aulABA0MfBZ+vMrgV\nVdRn/tF9dqtIJFN20riHKSkur3RXzvrTcv5Y7nwbRBh3m5GmJUk4HFdc9ZP9tawl\nPqZa2xIp24t4UMBVQnC7CwC9j22TdRNaVwAiYZ/29uRelv5L0r/LB5FLLazu2iSE\n7aEZpUHBAgMBAAECggEAB9utl0sZzwd5OdLbhZzRI5zQq5OZfj9Svtd0FaJteOHc\nX4/uZrXad8Qw4GQsLJzO2JfWwQdvBRIT8owtGiutu+r2M5GRctKHGOZC53jYN4mT\n+WpYvR8OS0+wEOtrsAi9lbBnxomZscoQ19jEkAoNQ3PR5IepGsMA6q6auRLqe2w/\nPkundhSq2SLtbqhdwkazRBv+BIjQN4tVoNiqNiyR1gKu5RHgfhu85qoylNu8oosG\no0Fjc/qQ7Vvb2ed0b1AcW7Gg2goTE9gm8RcupswliFrTw4ibr0WOzVbJzd8SUsHs\nZPRl2d0mSq8kBVIw8JYUwEjABUjaeGJNCOY/Q2fPWQKBgQD+JdOkpnqflx3b1myO\nyBSr1DWViPLXBopT/Rd3f0EtrwU5gCn0v7wzFZRmBGEGZquna1DmFAsfFtJCPN4d\nYV0FSizTPM1G4o4fZfkEYwsoqdaTK5+n/KbzcdNEQMwn6bAewOVZ3HGKXufAW5B0\nkxRYQ9QhydVluMfQenne7gh5GQKBgQDUHSDp1NhZUBpyTDFnlSF+4LCDJm1a7uqo\n0nwx00q3hDmwFvROJtG/Z89G69VYaidS6AGSI8g5ANsROf0F2+rolIevZFKhdTRr\n1xYIInZ/KVXpb90Mr+9J2SOEyFo1arpoDrd545gPpRKCFLhKfnnU/q0aeluWi14p\neAGQuIaa6QKBgAlee57mRD794yvni7j3x0tOV2tb0Rf8Nb1C50qQdmaovRiRkPpk\n1xtLAF2Ca9FAl7NkUWcp9f7/aGDovYd3v2YiheSDqU2jrHmb2MJApHirSi3CvfAD\ncGQpHhC2EtCl3MhFdC8L4WOofAKrXXfutCFM3tUgC63kUfltinCdddKBAoGAHrE0\nUQF+aLYBjaZew7k9holmoSOPUUge5lzGocMMHa/hVQyNPz24vfR8dqurTEbX99Qg\nXhAVacIo8L4uUYm33P2ZAJUIq9o0wqH1yymJce7+Qm/wUWSnwEzOKel/vBj8bhAr\nFlULbMAbBH2RCR5x7JMJYzpvREJYVrHJsDIzL/kCgYEAibunyvbFgbcws9gQd/Qn\nGEFLRNbam8Z8UJiJh/WnYX8h9jeX/QswH8cG/+XfOEbvEvnSbBLI+SiZJ1fTaH0Y\nKHYBhNjwRUkmmELOgCTA/nL7mnXIlTEKLGm54NapCAnyzAKnHJu+sgRI1G9Fv8c1\npk9CKXz2sh67p7t8kdhc9mo=\nREDACTED_KEY_END\n",
  "client_email": "REDACTED_CLIENT_EMAIL",
  "client_id": "116086274382549132254",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40investment-app-11ac4.iam.gserviceaccount.com",
  "universe_domain": "googleapis.com"
}

            """
                val stream: InputStream =
                    ByteArrayInputStream(jsonString.toByteArray(StandardCharsets.UTF_8))
                val googleCredentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList(FIREBASE_MESSAGING_SCOPE))
                googleCredentials.refreshIfExpired()
                googleCredentials.accessToken.tokenValue
            } catch (e: IOException) {
                Log.e("AccessToken", "Error retrieving access token", e)
                null
            }
        }

        override fun onPostExecute(token: String?) {
            callback.onAccessTokenReceived(token)
        }
    }

    interface AccessTokenCallback {
        fun onAccessTokenReceived(token: String?)
    }
}
