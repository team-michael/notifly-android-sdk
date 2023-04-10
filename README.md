# notifly-android-sdk

## Getting Started

## Payload Sample

### Push Notification (via FCM)

```bash
curl -X POST \
  https://fcm.googleapis.com/fcm/send \
  -H 'Authorization: key=AAAA5_bCtUY:APA91bFOccC_CNmkcFje5tRgFPR51aEgAyQgcSboQ6JRPmP-qrrV6TSq8lA-HtLJ8IPL2JRMnvl9_eQ1g__jyFXZ4N22ykIbJxZGGULu3OKZt_OTodOhAFZfDbJGi_J5znN4O1n-tKi3' \
  -H 'Content-Type: application/json' \
  -d '{
    "notification": {
        "title": "sdf",
        "body": "sdf"
    },
    "data": {
        "campaign_id": "push_test_dsboard",
        "link": "www.saladpet.com"
    },
    "priority": "high",
    "registration_ids": ["cwWnuwf5mEc_vfyKhH-2l7:APA91bEf1ZF8IRJaU03XqSm7mENi0ldKVfc1LM72l_65Off-ur1S_PnohgVNqLKAs4FaGz6EpPyDXrRZu5K5Sk96DUHjvLKdAacE_bK0oyRAi2JYAsGXRYt13ZeUAXrZKUYvavNQ_dvd"]
}'
```

### In-App Messaging (via FCM)

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -H 'Authorization: key=AAAA5_bCtUY:APA91bFOccC_CNmkcFje5tRgFPR51aEgAyQgcSboQ6JRPmP-qrrV6TSq8lA-HtLJ8IPL2JRMnvl9_eQ1g__jyFXZ4N22ykIbJxZGGULu3OKZt_OTodOhAFZfDbJGi_J5znN4O1n-tKi3' \
  -d '{
    "content_available": true,
    "data": {
        "campaign_id": "TestCampaignMinyong",
        "notifly_message_type": "in-app-message",
        "url": "https://d2vabyracdcb46.cloudfront.net/common-htmls/modal_full_image.html",
        "modal_properties": {
            "template_name": "modal_full_image",
            "width_vw": 100,
            "height_vh": 100,
            "position": "full"
        }
    },
    "registration_ids": ["f10B8R4WR-mYS-LUMsBEmL:APA91bFg9T7IEK0ZqL76FoIG_Zy6K57i6zNGqmAhRLcbE6uPkPLY9g11i4AiZxgeuNu9Ldif1MtOtifcLVo_HY3CeEjlybCndDULNt05ktY-4o0UfM3bPijwyIyD0rIZcesZOJIh0RyO"]
}' \
  https://fcm.googleapis.com/fcm/send
```