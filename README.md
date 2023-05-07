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
    "notification": {
        "title": "This is notification title",
        "body": "This is notification body"
    },
    "data": {
        "campaign_id": "push_test_dsboard",
        "link": "https://www.saladpet.com"
    },
    "priority": "high",
    "registration_ids": ["fut-o8kNTcyZf9BoGyXGsC:APA91bHELQ6_L-zhhDZrw4tahn95PFAslEwYPOvsXNClPgPT1X0zsFMr0Rv2fgxJ7RaRx2HKh5I3rzAhr1Vriov30pPQ-HguTByRnp2bLafO-2QVwtJTyv6hXNaTsFvDpe3YKu3iYLJM"]
}'
```

### In-App Message (via FCM)

```bash
curl -X POST \
  https://fcm.googleapis.com/fcm/send \
  -H 'Content-Type: application/json' \
  -H 'Authorization: key=AAAAc0VpeVc:APA91bHye9A8uc5hjc0IwEeqk2hEeiEBQ63ndVdeQ_YZaTL3_CLgN7I0hqGnJywEFetX5YZSDxnLmTQjziFJ-DyowtJLjLmbrltVDc-tHGPzlM6BV7XefhrgSGXnrXM7pbntpEn6AqHC' \
  -d '{
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
    "registration_ids": ["dr_JkT53QBOAidHYvYDClh:APA91bHaUKxrj50BUJV2rG8PIdXfrQdqR7H8uHPjv7NDRbKnmF4_oj7pW8b624XaRXXsxsqQL_h3Wd0uvZWD_6_6qowbXDQrg2cBNEztOwrpZlMaX5j2MIpNNzk_nEitZsG1RomBKKQk"]
}'
```