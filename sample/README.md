## Notifly Android SDK Test app

### Getting started

1. Make sure you have google-services.json under the `sample` directory. 

Firebase project link: https://console.firebase.google.com/u/0/project/ios-test-app-f6e0e/settings/general/android:tech.notifly.sample

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
  -H 'Authorization: key=AAAA071qs2o:APA91bEgUVz05Nca-uUIo2lK8vJl4SuiJIfZs-dqEhAYJgnO6C_gpVnfs2OVOvXgDp-IwVCmIHDQ2rSIrFAdDhQUTvDx_szpdeTAuQL8wx77nJV2BHtEW4cOk56t5YMXDKs8CvQ1ffIj' \
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
    "registration_ids": ["eVMJArGL85pJyyTsaAxCgq:APA91bGg3NhWmaxwT0SZ2TLQKCCRWIMp7tCLSZZ10S5zbsEKnmaGL2YkSFHrpPdfWQXvb6DZBzmPKZ6budB9iMUPbg3WfgU4iQAM77iENtzo8pYkNOgkK53bUEJmeZhsF9z8IjFnXm5p"]
}'
```

### In-App Message

```bash
curl -X POST \
  https://fcm.googleapis.com/fcm/send \
  -H 'Content-Type: application/json' \
  -H 'Authorization: key=AAAA071qs2o:APA91bEgUVz05Nca-uUIo2lK8vJl4SuiJIfZs-dqEhAYJgnO6C_gpVnfs2OVOvXgDp-IwVCmIHDQ2rSIrFAdDhQUTvDx_szpdeTAuQL8wx77nJV2BHtEW4cOk56t5YMXDKs8CvQ1ffIj' \
  -d '{
    "data": {
        "notifly_message_type": "in-app-message",
        "notifly_in_app_message_data":"eyJjYW1wYWlnbl9pZCI6ImluX2FwcF9tZXNzYWdlX3Rlc3RfYW5kcm9pZC1zaW11bGF0b3IiLCJ1cmwiOiJodHRwczovL2QydmFieXJhY2RjYjQ2LmNsb3VkZnJvbnQubmV0L2I4MGMzZjBlMmZiZDVlYjk4NmRmNGYxZDMyZWEyODcxL2luLWFwcC1tZXNzYWdlLXRlc3QvaW5fYXBwX21lc3NhZ2VfdGVzdF9hbmRyb2lkLXNpbXVsYXRvcl8yMDIzLTA1LTA5VDA2OjM0OjE4LjExOVouaHRtbCIsIm1vZGFsX3Byb3BlcnRpZXMiOnsidGVtcGxhdGVfbmFtZSI6ImZ1bGxfbWVkaWEiLCJ3aWR0aF92dyI6MTAwLCJoZWlnaHRfdmgiOjEwMCwicG9zaXRpb24iOiJmdWxsIn19"
    },
    "registration_ids": ["eVMJArGL85pJyyTsaAxCgq:APA91bGg3NhWmaxwT0SZ2TLQKCCRWIMp7tCLSZZ10S5zbsEKnmaGL2YkSFHrpPdfWQXvb6DZBzmPKZ6budB9iMUPbg3WfgU4iQAM77iENtzo8pYkNOgkK53bUEJmeZhsF9z8IjFnXm5p"]
}'
```
