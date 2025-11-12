## Notifly Android SDK Test app

### Getting started

1. Make sure you have google-services.json under the `sample` directory.
2. Create file `sample/gradle.properties` and add:

```
NOTIFLY_USERNAME=...
NOTIFLY_PASSWORD=...
```

### Payload Sample

### Push Notification

```bash
curl -X POST \
  https://fcm.googleapis.com/fcm/send \
  -H 'Authorization: ${key}' \
  -H 'Content-Type: application/json' \
  -d '{
    "data": {
        "notifly": {
            "type": "push",
            "ti": "This is push notification title",
            "bd": "This is push notification body",
            "cid": "test_campaign_id",
            "u": "https://www.notifly.tech",
        }
    },
    "priority": "high",
    "registration_ids": ["$id"]
}'
```

### In-App Message

```bash
curl -X POST \
  https://fcm.googleapis.com/fcm/send \
  -H 'Content-Type: application/json' \
  -H 'Authorization: ${key}' \
  -d '{
    "data": {
        "notifly_message_type": "in-app-message",
        "notifly_in_app_message_data":"message_data"
    },
    "registration_ids": ["$id"]
}'
```
