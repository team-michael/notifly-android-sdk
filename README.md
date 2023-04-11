# notifly-android-sdk

## Getting Started

## Payload Sample

### Push Notification (via FCM)

```bash
curl -X POST \
  https://fcm.googleapis.com/fcm/send \
  -H 'Authorization: key=AAAAc0VpeVc:APA91bHye9A8uc5hjc0IwEeqk2hEeiEBQ63ndVdeQ_YZaTL3_CLgN7I0hqGnJywEFetX5YZSDxnLmTQjziFJ-DyowtJLjLmbrltVDc-tHGPzlM6BV7XefhrgSGXnrXM7pbntpEn6AqHC' \
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
    "registration_ids": ["dr_JkT53QBOAidHYvYDClh:APA91bHaUKxrj50BUJV2rG8PIdXfrQdqR7H8uHPjv7NDRbKnmF4_oj7pW8b624XaRXXsxsqQL_h3Wd0uvZWD_6_6qowbXDQrg2cBNEztOwrpZlMaX5j2MIpNNzk_nEitZsG1RomBKKQk"]
}'
```

### In-App Messaging (via FCM)

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