## Google Authentication

The first thing you'll want to do is decide how you want to authenticate with Firebase. How you can set this up is
rather complex and is outlined [here](https://cloud.google.com/docs/authentication). If you only have one client
application you can relay on the admin-sdk's default credentials setup. For example, you should be able to deploy in
Google Cloud without additional setup. Otherwise, you'll need to use a
[service account](https://cloud.google.com/docs/authentication#service-accounts). From here on the instructions will
assume a service account is being used.

If you haven't already, create a new project in the [Firebase console](https://console.firebase.google.com/) then go to
`Project Settings > Service Accounts > Firebase Admin SDK`.

<img src="/images/firebase-console.png" alt="Screenshot of the Firebase console project settings" width="100%">

Warning! Do *not* click the 'Generate new private key', while this will work it gives the account far more permissions
than you want. Instead, click on 'Manage service account permissions' to get to the Google Cloud Console.

The first thing we need to do is create a new role that only has the send push message permission. To do so, click the
Roles tab on the left, then click 'Create Role' at the top.

<img src="/images/google-console-roles.png" alt="Screenshot of the Google Cloud console Roles tab" width="100%">

Name your role "Send Push" and set the Role launch stage to "General Availability". Then click "Add Permissions" and
search for "Firebase Cloud Messaging Send". Check the "cloudmessaging.messages.create" permission and then click "Add".

<img src="/images/google-console-roles-create.png" alt="Screenshot of Create Role" width="50%">

Finally, click "Create".

Go back to the "Service Accounts" tab and click "Create Service Account".

<img src="/images/google-console-service-accounts.png" alt="Screenshot of Service Accounts" width="100%">

Name your service account "webpush-relay" and click "Create and Continue".

Click the "Select a role" dropdown and search for the role you created in the previous step. Click "Done".

<img src="/images/google-console-service-create.png" alt="Screenshot Create service account" width="50%">

Click on the service account you just created and click on the "Keys" tab.

<img src="/images/google-console-keys.png" alt="Screenshot of Service Accounts keys" width="100%">

Click "Add Key" and "Create new key".

Keep the JSON key type selected and click "Create".

<img src="/images/google-console-keys-create.png" alt="Screenshot of the Create private key dialog" width="75%">

This should download a json file that has your credentials.