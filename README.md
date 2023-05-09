# notifly-android-sdk

## Getting Started

## Payload Sample

### Push Notification (via FCM)

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

### In-App Message (via FCM)

```bash
curl -X POST \
  https://fcm.googleapis.com/fcm/send \
  -H 'Content-Type: application/json' \
  -H 'Authorization: key=AAAA071qs2o:APA91bEgUVz05Nca-uUIo2lK8vJl4SuiJIfZs-dqEhAYJgnO6C_gpVnfs2OVOvXgDp-IwVCmIHDQ2rSIrFAdDhQUTvDx_szpdeTAuQL8wx77nJV2BHtEW4cOk56t5YMXDKs8CvQ1ffIj' \
  -d '{
    "data": {
        "campaign_id": "TestCampaignMinyong",
        "notifly_message_type": "in-app-message",
        "url": "https://d2vabyracdcb46.cloudfront.net/common-htmls/full_media.html",
        "modal_properties": {
            "template_name": "modal_full_image",
            "width_vw": 100,
            "height_vh": 100,
            "position": "full"
        }
    },
    "registration_ids": ["eVMJArGL85pJyyTsaAxCgq:APA91bGg3NhWmaxwT0SZ2TLQKCCRWIMp7tCLSZZ10S5zbsEKnmaGL2YkSFHrpPdfWQXvb6DZBzmPKZ6budB9iMUPbg3WfgU4iQAM77iENtzo8pYkNOgkK53bUEJmeZhsF9z8IjFnXm5p"]
}'
```
