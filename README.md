# webpush-fcm-relay

Relays WebPush messages through firebase to allow them to be received by mobile applications. This was built for my
[Mastodon](https://joinmastodon.org/) client but is pretty general.

## Features

- Supports both [rfc8291](https://datatracker.ietf.org/doc/html/rfc8291) and the
  [draft rfc](https://datatracker.ietf.org/doc/html/draft-ietf-webpush-encryption-04) encryption standards.
- Supports multiple client applications.
- Includes a client android library for easy setup.

## Server Setup

Your first step should be to figure out you are authenticating with Firebase. If you aren't familiar with the process
you can follow along [here](docs/google-authentication.md). For the rest of this section we'll assume you have a service
account key json saved in `./credentials/firebase-key.json`

### Running the jar directly

You can download the [jar](https://github.com/evant/webpush-fcm-relay/releases/download/server-1.0.0/webpush-fcm-relay.jar)
and run directly. This requires java 21+.

```shell
java -jar webpush-fcm-relay.jar -port=8080 -P:firebase.auth.credentialsDir=./credentials
```

### Running with docker/podman

Images are published to [dockerhub](https://hub.docker.com/r/etatarka/webpush-fcm-relay).

```shell
docker run -p 8080:8080 -v ./credentials:/credentials:ro docker.io/etatarka/webpush-fcm-relay:latest -port=8080 -P:firebase.auth.credentialsDir=./credentials
```

### Running with docker/podman-compose

There's a [docker-compose.yaml](/docker-compose.yaml) file in this repo that you can use as an example.

```shell
docker-compose up
```

### Arguments

All methods take the same arguments:
- `-port=` (required) sets the port the server is run on
- `-host=0.0.0.0` sets the host address the server is run on
- `-P:firebase.auth.credentialsDir=` sets the directory to looks for firebase credentials, multiple credentials with
  different project-id's are allowed in that directory.

In addition, the following jvm arguments can be set:
- `-Dlog.level=INFO` set the level of logging. The default is very conservative, setting this to at least DEBUG will
  cause requests to be logged.

Note: As the server will require https to handle web pushes properly, you'll want to set up a reverse proxy and
ssl certificate. There's a lot of ways to do this, and I'd recommend starting with
[Let's Encrypt](https://letsencrypt.org/docs/client-options/)'s clients.

## Client Setup (Android)

Start by setting up [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging/android/client) in your
project. Note: I recommend skipping the `com.google.gms.google-services` plugin as it's not well maintained. Instead you
can create a resources file with the following values that can be found in your Firebase project's console or in the
google-services.json file you can download.

```xml
<resources>
    <string name="google_app_id">1:0000000000000:android:0000000000000000000000</string>
    <string name="project_id">projectid</string>
    <string name="google_api_key">000000000000000000000000000000000000000</string>
</resources>
```

Then add the `client-android` dependency to your `build.gradke(.kts)`

```groovy
dependencies {
  implementation("me.tatarka.webpush.relay:client-android:1.0.0")
}
```

Make your push service extend `me.tatarka.webpush.relay.WebPushRelayService` instead of
`com.google.firebase.messaging.FirebaseMessagingService.FirebaseMessagingService` and implement the required methods.

```kotlin
class MyWebPushService : WebPushRelayService() {
  override suspend fun register(path: String, publicKey: ByteString, authSecret: ByteString) {
    // This is where you'd register with the service that's sending the web pushes. The url should be the domain of
    // your relay server + the path given here. Ex: https://relay.example.com/ + path
  }

  override suspend fun onWebPushReceived(body: Source) {
    // This is where you decode the body and optionally show a notification to the user.
  }
}
```

## Comparison to Related Projects

### [mastodon/webpush-fcm-relay](https://github.com/mastodon/webpush-fcm-relay)

Pros:

- Written in Go
- Low memory usage
- Very simple implementation

Cons:

- Unclear licencing
- Uses deprecated firebase authentication
- Non-standard message encoding
- Only supports pre-standard WebPush encryption
- Only supports a single client application

### [mozilla/autopush-rs](https://github.com/mozilla-services/autopush-rs)

Pros:

- Written in Rust
- Designed to handle millions of push messages
- Supports all WebPush encryption standards
- Supports multiple client applications

Cons:

- Not well documented
- Existing storage backends are propitiatory
